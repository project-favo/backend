package com.favo.backend.Service.Product.feed;

import com.favo.backend.Domain.product.Product;
import com.favo.backend.Domain.product.ProductMapper;
import com.favo.backend.Domain.product.ProductResponseDto;
import com.favo.backend.Domain.product.ProductSearchResultDto;
import com.favo.backend.Domain.product.Repository.ProductRepository;
import com.favo.backend.Domain.product.Repository.TagRepository;
import com.favo.backend.Domain.product.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductFeedService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final ProductFeedNativeRepository feedNativeRepository;
    private final ProductRepository productRepository;
    private final TagRepository tagRepository;

    public ProductSearchResultDto trendingByReviewsLast7Days(Pageable pageable) {
        FeedTimeWindows.TimeRangeInstant range = FeedTimeWindows.lastSevenCalendarDaysInclusiveTr();
        LocalDateTime start = FeedTimeWindows.toUtcLocalDateTime(range.startInclusive());
        LocalDateTime endExclusive = FeedTimeWindows.toUtcLocalDateTime(range.endExclusive());
        Pageable p = clampPageable(pageable);

        long total = feedNativeRepository.countProductsByReviewWindow(start, endExclusive);
        if (total == 0) {
            return emptyPage(p);
        }

        List<ScoredProductId> scored = feedNativeRepository.findProductIdsByReviewWindow(
                start,
                endExclusive,
                (int) p.getOffset(),
                p.getPageSize()
        );
        return mapScoredToSearchResult(scored, total, p);
    }

    public ProductSearchResultDto trendingByLikesCurrentWeek(Pageable pageable) {
        FeedTimeWindows.TimeRangeInstant range = FeedTimeWindows.currentCalendarWeekTr();
        LocalDateTime start = FeedTimeWindows.toUtcLocalDateTime(range.startInclusive());
        LocalDateTime endExclusive = FeedTimeWindows.toUtcLocalDateTime(range.endExclusive());
        Pageable p = clampPageable(pageable);

        long total = feedNativeRepository.countProductsByWeeklyLikes(start, endExclusive);
        if (total == 0) {
            return emptyPage(p);
        }

        List<ScoredProductId> scored = feedNativeRepository.findProductIdsByWeeklyLikes(
                start,
                endExclusive,
                (int) p.getOffset(),
                p.getPageSize()
        );
        return mapScoredToSearchResult(scored, total, p);
    }

    /**
     * Son 7 günde en çok yorum alan ürünler; ilgi alanı kullanıcının beğendiği ve yorumladığı ürünlerin
     * leaf tag'leri + bir üst parent tag (aynı parent altındaki diğer leaf ürünler dahil).
     * Sinyal yoksa veya kişisel sonuç boşsa global 7 günlük trende düşer.
     */
    public ProductSearchResultDto personalizedTrendingReviewsLast7Days(Long userId, Pageable pageable) {
        Pageable p = clampPageable(pageable);

        Set<Long> leafTagIds = new HashSet<>();
        leafTagIds.addAll(feedNativeRepository.findDistinctTagIdsFromLikes(userId));
        leafTagIds.addAll(feedNativeRepository.findDistinctTagIdsFromReviews(userId));

        if (leafTagIds.isEmpty()) {
            return trendingByReviewsLast7Days(p);
        }

        Set<Long> parentIds = new HashSet<>();
        List<Tag> tagsWithParent = tagRepository.fetchTagsWithParentByIds(leafTagIds);
        for (Tag t : tagsWithParent) {
            if (t.getParent() != null) {
                parentIds.add(t.getParent().getId());
            }
        }

        FeedTimeWindows.TimeRangeInstant range = FeedTimeWindows.lastSevenCalendarDaysInclusiveTr();
        LocalDateTime start = FeedTimeWindows.toUtcLocalDateTime(range.startInclusive());
        LocalDateTime endExclusive = FeedTimeWindows.toUtcLocalDateTime(range.endExclusive());

        long total = feedNativeRepository.countPersonalizedReviewWindow(start, endExclusive, leafTagIds, parentIds);
        if (total == 0) {
            return trendingByReviewsLast7Days(p);
        }

        List<ScoredProductId> scored = feedNativeRepository.findPersonalizedReviewWindow(
                start,
                endExclusive,
                leafTagIds,
                parentIds,
                (int) p.getOffset(),
                p.getPageSize()
        );
        return mapScoredToSearchResult(scored, total, p);
    }

    private static Pageable clampPageable(Pageable pageable) {
        int size = pageable == null ? DEFAULT_SIZE : pageable.getPageSize();
        size = Math.min(Math.max(1, size), MAX_SIZE);
        int page = pageable == null ? 0 : Math.max(0, pageable.getPageNumber());
        return PageRequest.of(page, size);
    }

    private static ProductSearchResultDto emptyPage(Pageable p) {
        int totalPages = 0;
        return new ProductSearchResultDto(List.of(), 0, totalPages, p.getPageSize(), p.getPageNumber());
    }

    private ProductSearchResultDto mapScoredToSearchResult(List<ScoredProductId> scored, long total, Pageable p) {
        if (scored.isEmpty()) {
            int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) p.getPageSize());
            return new ProductSearchResultDto(List.of(), total, totalPages, p.getPageSize(), p.getPageNumber());
        }

        List<Long> ids = scored.stream().map(ScoredProductId::productId).toList();
        List<Product> loaded = productRepository.findByIdInWithTagAndParent(ids);
        Map<Long, Integer> order = IntStream.range(0, ids.size()).boxed().collect(Collectors.toMap(ids::get, i -> i));
        loaded.sort(Comparator.comparingInt(product -> order.getOrDefault(product.getId(), Integer.MAX_VALUE)));

        List<ProductResponseDto> content = loaded.stream().map(ProductMapper::toDto).collect(Collectors.toList());
        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) p.getPageSize());
        return new ProductSearchResultDto(content, total, totalPages, p.getPageSize(), p.getPageNumber());
    }
}

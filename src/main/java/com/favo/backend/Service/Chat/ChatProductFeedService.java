package com.favo.backend.Service.Chat;

import com.favo.backend.Domain.chat.ChatProductCardDto;
import com.favo.backend.Domain.interaction.ProductInteraction;
import com.favo.backend.Domain.interaction.Repository.ProductInteractionRepository;
import com.favo.backend.Domain.product.Product;
import com.favo.backend.Domain.product.Repository.ProductRepository;
import com.favo.backend.Domain.review.Repository.ReviewRepository;
import com.favo.backend.Domain.user.GeneralUser;
import com.favo.backend.Domain.user.SystemUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatProductFeedService {

    private static final int FEED_SIZE = 8;
    private static final int CANDIDATE_POOL = 40;
    private static final int MAX_LIKES_FOR_TAGS = 25;

    private final ProductRepository productRepository;
    private final ProductInteractionRepository productInteractionRepository;
    private final ReviewRepository reviewRepository;

    /**
     * Kullanıcı mesajı ürün keşfi / öneri niyeti taşıyorsa gerçek ürün kartları üretir.
     */
    public List<ChatProductCardDto> buildFeed(SystemUser user, String userMessage) {
        if (!wantsProductFeed(userMessage)) {
            return List.of();
        }

        boolean preferHighRated = wantsHighRated(userMessage);

        if (!(user instanceof GeneralUser gu)) {
            return toCardList(pickGuestProducts(preferHighRated), preferHighRated);
        }

        Set<Long> likedIds = loadLikedProductIds(gu);
        Set<Long> tagIds = loadPreferredTagIdsFromLikes(gu);

        List<Long> candidateIds;
        if (tagIds.isEmpty()) {
            candidateIds = newestActiveProductIds(CANDIDATE_POOL);
        } else {
            Page<Long> page = productRepository.searchProductIds(
                    null,
                    new ArrayList<>(tagIds),
                    null,
                    PageRequest.of(0, CANDIDATE_POOL)
            );
            candidateIds = page.getContent().stream()
                    .filter(id -> !likedIds.contains(id))
                    .toList();
            if (candidateIds.isEmpty()) {
                candidateIds = newestActiveProductIds(CANDIDATE_POOL);
            }
        }

        List<Product> ordered = loadProductsPreservingOrder(candidateIds);
        return toCardList(ordered, preferHighRated);
    }

    private List<Product> pickGuestProducts(boolean preferHighRated) {
        List<Long> ids = newestActiveProductIds(CANDIDATE_POOL);
        List<Product> ordered = loadProductsPreservingOrder(ids);
        return ordered;
    }

    private List<Long> newestActiveProductIds(int limit) {
        return productRepository
                .findActiveProductIdsOrderByCreatedAtDescIdAsc(PageRequest.of(0, limit))
                .getContent();
    }

    private Set<Long> loadLikedProductIds(GeneralUser gu) {
        var page = productInteractionRepository.findLikedProductsByPerformerId(
                gu.getId(),
                PageRequest.of(0, MAX_LIKES_FOR_TAGS)
        );
        Set<Long> ids = new HashSet<>();
        for (ProductInteraction pi : page.getContent()) {
            if (pi.getTargetProduct() != null) {
                ids.add(pi.getTargetProduct().getId());
            }
        }
        return ids;
    }

    /** Beğenilen ürünlerin tag id'leri (tekrarsız) */
    private Set<Long> loadPreferredTagIdsFromLikes(GeneralUser gu) {
        var page = productInteractionRepository.findLikedProductsByPerformerId(
                gu.getId(),
                PageRequest.of(0, MAX_LIKES_FOR_TAGS)
        );
        Set<Long> tagIds = new HashSet<>();
        for (ProductInteraction pi : page.getContent()) {
            Product p = pi.getTargetProduct();
            if (p != null && p.getTag() != null && Boolean.TRUE.equals(p.getIsActive())) {
                tagIds.add(p.getTag().getId());
            }
        }
        return tagIds;
    }

    private List<Product> loadProductsPreservingOrder(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Product> found = productRepository.findByIdInWithTagAndParent(ids);
        Map<Long, Product> byId = found.stream().collect(Collectors.toMap(Product::getId, p -> p));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private List<ChatProductCardDto> toCardList(List<Product> products, boolean preferHighRated) {
        List<Product> work = new ArrayList<>(products);
        if (preferHighRated) {
            work.sort(Comparator.comparing((Product p) ->
                    Optional.ofNullable(reviewRepository.calculateAverageRatingByProductId(p.getId())).orElse(0.0)
            ).reversed());
        }
        return work.stream().limit(FEED_SIZE).map(this::toCard).toList();
    }

    private ChatProductCardDto toCard(Product p) {
        Long pid = p.getId();
        Double avg = reviewRepository.calculateAverageRatingByProductId(pid);
        Long cnt = reviewRepository.countReviewsByProductId(pid);
        String tagName = p.getTag() != null ? p.getTag().getName() : null;
        return new ChatProductCardDto(pid, p.getName(), p.getImageURL(), tagName, avg, cnt);
    }

    static boolean wantsProductFeed(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String m = message.toLowerCase(Locale.ROOT);
        String[] keys = {
                "ürün", "urun", "öner", "oner", "beğen", "begen", "kategori", "puan", "keşfet", "kesfet",
                "popüler", "populer", "hangi", "göster", "goster", "listele", "başka", "baska", "tavsiye",
                "similar", "recommend", "product", "wishlist", "browse", "discover", "suggest", "show me",
                "what to buy", "almalı", "almali", "satın", "satin", "fiyat", "yorumlu", "inceleme"
        };
        for (String k : keys) {
            if (m.contains(k)) {
                return true;
            }
        }
        return false;
    }

    static boolean wantsHighRated(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String m = message.toLowerCase(Locale.ROOT);
        return m.contains("yüksek puan") || m.contains("yuksek puan")
                || m.contains("en iyi") || m.contains("en çok") || m.contains("en cok")
                || m.contains("high rated") || m.contains("top rated") || m.contains("best")
                || m.contains("yüksek oy") || m.contains("yuksek oy");
    }
}

package com.favo.backend.Service.Product.feed;

import com.favo.backend.Domain.feed.FriendsFeedItemDto;
import com.favo.backend.Domain.feed.FriendsFeedResultDto;
import com.favo.backend.Domain.interaction.ProductInteraction;
import com.favo.backend.Domain.interaction.Repository.ProductInteractionRepository;
import com.favo.backend.Domain.review.Repository.ReviewRepository;
import com.favo.backend.Domain.review.Review;
import com.favo.backend.Domain.user.UserAnonymityUtil;
import com.favo.backend.Domain.user.Repository.UserFollowRepository;
import com.favo.backend.Service.User.ProfileImageUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendsFeedService {

    private final UserFollowRepository userFollowRepository;
    private final ReviewRepository reviewRepository;
    private final ProductInteractionRepository productInteractionRepository;
    private final ProfileImageUrlService profileImageUrlService;

    public FriendsFeedResultDto getFriendsFeed(Long currentUserId, Pageable pageable) {
        int safeSize = clampSize(pageable != null ? pageable.getPageSize() : 20);
        int pageNumber = Math.max(0, pageable != null ? pageable.getPageNumber() : 0);
        long offset = (long) pageNumber * safeSize;

        List<Long> followeeIds = userFollowRepository.findActiveFolloweeIds(currentUserId);
        if (followeeIds.isEmpty()) {
            return new FriendsFeedResultDto(List.of(), 0, 0, safeSize, pageNumber);
        }

        long totalReviews = reviewRepository.countActiveByOwnerIds(followeeIds);
        long totalLikes = productInteractionRepository.countActiveLikesByPerformerIds(followeeIds);
        long totalElements = totalReviews + totalLikes;
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil(totalElements / (double) safeSize);

        if (totalElements == 0 || offset >= totalElements) {
            return new FriendsFeedResultDto(List.of(), totalElements, totalPages, safeSize, pageNumber);
        }

        // Merge feed types in-memory for MVP. Fetch enough rows to support requested page offset.
        int fetchLimit = (int) Math.min(Math.max((offset + safeSize) * 2, safeSize * 2L), 1000L);
        Pageable fetchPage = PageRequest.of(0, fetchLimit);
        List<FriendsFeedItemDto> merged = new ArrayList<>();

        List<Review> reviews = reviewRepository.findRecentFeedReviewsByOwnerIds(followeeIds, fetchPage);
        reviews.forEach(review -> merged.add(mapReview(review)));

        List<ProductInteraction> likes = productInteractionRepository.findRecentFeedLikesByPerformerIds(followeeIds, fetchPage);
        likes.forEach(like -> merged.add(mapLike(like)));

        List<FriendsFeedItemDto> sorted = merged.stream()
                .sorted(Comparator.comparing(FriendsFeedItemDto::getCreatedAt).reversed())
                .toList();

        int fromIndex = (int) Math.min(offset, sorted.size());
        int toIndex = Math.min(fromIndex + safeSize, sorted.size());
        List<FriendsFeedItemDto> content = fromIndex >= toIndex ? List.of() : sorted.subList(fromIndex, toIndex);

        return new FriendsFeedResultDto(content, totalElements, totalPages, safeSize, pageNumber);
    }

    private int clampSize(int size) {
        return Math.max(1, Math.min(size, 50));
    }

    private FriendsFeedItemDto mapReview(Review review) {
        Long actorId = review.getOwner() != null ? review.getOwner().getId() : null;
        String actorPhotoUrl = actorId != null ? profileImageUrlService.buildProfileImageUrl(actorId) : null;
        return new FriendsFeedItemDto(
                "REVIEW",
                review.getCreatedAt(),
                actorId,
                UserAnonymityUtil.publicUserName(review.getOwner()),
                actorPhotoUrl,
                review.getProduct() != null ? review.getProduct().getId() : null,
                review.getProduct() != null ? review.getProduct().getName() : null,
                review.getProduct() != null ? review.getProduct().getImageURL() : null,
                review.getId(),
                review.getTitle(),
                review.getDescription(),
                review.getRating()
        );
    }

    private FriendsFeedItemDto mapLike(ProductInteraction like) {
        Long actorId = like.getPerformer() != null ? like.getPerformer().getId() : null;
        String actorPhotoUrl = actorId != null ? profileImageUrlService.buildProfileImageUrl(actorId) : null;
        return new FriendsFeedItemDto(
                "PRODUCT_LIKE",
                like.getCreatedAt(),
                actorId,
                UserAnonymityUtil.publicUserName(like.getPerformer()),
                actorPhotoUrl,
                like.getTargetProduct() != null ? like.getTargetProduct().getId() : null,
                like.getTargetProduct() != null ? like.getTargetProduct().getName() : null,
                like.getTargetProduct() != null ? like.getTargetProduct().getImageURL() : null,
                null,
                null,
                null,
                null
        );
    }
}

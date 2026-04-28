package com.favo.backend.Domain.review;

import java.util.List;
import java.util.stream.Collectors;

public class ReviewMapper {
    public static ReviewResponseDto toDto(Review review, Long currentUserId, String ownerProfilePhotoUrl) {
        List<MediaResponseDto> mediaList = review.getMediaList() != null
                ? review.getMediaList().stream()
                    .filter(media -> Boolean.TRUE.equals(media.getIsActive()))
                    .map(media -> new MediaResponseDto(
                            media.getId(),
                            media.getMimeType(),
                            media.getUploadDate()
                    ))
                    .collect(Collectors.toList())
                : List.of();

        // Like count hesapla
        Long likeCount = review.getInteractions() != null
                ? review.getInteractions().stream()
                    .filter(interaction -> Boolean.TRUE.equals(interaction.getIsActive()))
                    .filter(interaction -> "LIKE".equalsIgnoreCase(safeType(interaction.getType())))
                    .count()
                : 0L;

        // Mevcut kullanıcı bu review'ı beğenmiş mi? (bozuk veri: performer null olabilir)
        boolean isLiked = false;
        if (currentUserId != null && review.getInteractions() != null) {
            isLiked = review.getInteractions().stream()
                    .filter(interaction -> Boolean.TRUE.equals(interaction.getIsActive()))
                    .filter(interaction -> "LIKE".equalsIgnoreCase(safeType(interaction.getType())))
                    .anyMatch(interaction -> interaction.getPerformer() != null
                            && currentUserId.equals(interaction.getPerformer().getId()));
        }
        Boolean isLikedByCurrentUser = isLiked;

        return new ReviewResponseDto(
                review.getId(),
                review.getTitle(),
                review.getDescription(),
                review.getIsCollaborative(),
                review.getRating(),
                review.getCreatedAt(),
                review.getProduct() != null ? review.getProduct().getId() : null,
                review.getProduct() != null ? review.getProduct().getName() : null,
                review.getOwner() != null ? review.getOwner().getId() : null,
                review.getOwner() != null ? review.getOwner().getUserName() : null,
                ownerProfilePhotoUrl,
                mediaList,
                likeCount,
                isLikedByCurrentUser,
                review.getToxicityScore(),
                review.getModerationStatus(),
                review.getIsActive()
        );
    }

    public static ReviewResponseDto toDto(Review review) {
        return toDto(review, null, null);
    }

    private static String safeType(String type) {
        return type == null ? "" : type;
    }
}


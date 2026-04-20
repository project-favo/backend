package com.favo.backend.Domain.feed;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class FriendsFeedItemDto {
    private String type; // REVIEW | PRODUCT_LIKE
    private LocalDateTime createdAt;

    private Long actorId;
    private String actorUserName;
    private String actorProfilePhotoUrl;

    private Long productId;
    private String productName;
    private String productImageUrl;

    private Long reviewId;
    private String reviewTitle;
    private String reviewDescription;
    private Integer reviewRating;
}

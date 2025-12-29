package com.favo.backend.Domain.review;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class ReviewResponseDto {
    private Long id;
    private String title;
    private String description;
    private Boolean isCollaborative;
    private Integer rating;
    private LocalDateTime createdAt;
    private Long productId;
    private String productName;
    private Long ownerId;
    private String ownerUserName;
    private List<MediaResponseDto> mediaList;
    private Long likeCount; // Review'a yapılan like sayısı
    private Boolean isLikedByCurrentUser; // Mevcut kullanıcı bu review'ı beğenmiş mi?
}


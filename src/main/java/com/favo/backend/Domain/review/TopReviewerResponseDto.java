package com.favo.backend.Domain.review;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TopReviewerResponseDto {
    private Long userId;
    private String userName;
    private String profileImageUrl;
    private Long reviewCount;
}

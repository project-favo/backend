package com.favo.backend.Domain.review;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class FlagResponseDto {
    private Long id;
    private Long reviewId;
    private FlagReason reason;
    private String notes;
    private LocalDateTime createdAt;
}


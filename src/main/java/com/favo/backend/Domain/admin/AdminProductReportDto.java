package com.favo.backend.Domain.admin;

import java.time.LocalDateTime;

/**
 * Admin paneli için product REPORT etkileşimi DTO'su.
 */
public record AdminProductReportDto(
        Long id,
        Long productId,
        Long reporterId,
        LocalDateTime createdAt
) {
}


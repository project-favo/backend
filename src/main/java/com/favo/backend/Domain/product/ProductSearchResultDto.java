package com.favo.backend.Domain.product;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Search & Filter API sonuç sayfası.
 * Sayfalama bilgisi + product listesi döner.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchResultDto {
    private List<ProductResponseDto> content;
    private long totalElements;
    private int totalPages;
    private int size;
    private int number; // 0-based page index
}

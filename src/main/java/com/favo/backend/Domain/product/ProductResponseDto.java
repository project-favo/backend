package com.favo.backend.Domain.product;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ProductResponseDto {
    private Long id;
    private String name;
    private String description;
    private String imageURL;
    private TagDto tag;
    private LocalDateTime createdAt;
    private Boolean isActive;
}


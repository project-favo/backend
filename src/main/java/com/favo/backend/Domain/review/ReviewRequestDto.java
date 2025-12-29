package com.favo.backend.Domain.review;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRequestDto {
    private Long productId; // Review hangi product için
    private String title;
    private String description;
    private Boolean isCollaborative = false;
    private Integer rating; // 1-5 arası rating
    private List<MediaRequestDto> mediaList; // Opsiyonel: Review'a eklenen media dosyaları
}


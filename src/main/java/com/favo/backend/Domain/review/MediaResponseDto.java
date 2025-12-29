package com.favo.backend.Domain.review;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class MediaResponseDto {
    private Long id;
    private String mimeType;
    private LocalDateTime uploadDate;
    // imageData response'da gönderilmez (çok büyük olabilir)
    // İhtiyaç olursa ayrı bir endpoint ile getirilebilir
}


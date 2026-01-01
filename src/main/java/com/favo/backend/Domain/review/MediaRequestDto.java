package com.favo.backend.Domain.review;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MediaRequestDto {
    private byte[] imageData; // Base64 veya binary image data
    private String mimeType; // Örn: "image/jpeg", "image/png"
}


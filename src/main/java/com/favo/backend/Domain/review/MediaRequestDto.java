package com.favo.backend.Domain.review;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MediaRequestDto {
    /**
     * Mevcut medyayı korumak için: retain modunda sadece bu alan dolu gelir.
     * Mobil `{"id": "123"}` şeklinde String olarak gönderir.
     */
    @JsonProperty("id")
    private String existingMediaId;

    private byte[] imageData;
    private String mimeType;

    public boolean isRetain() {
        return existingMediaId != null && !existingMediaId.isBlank();
    }

    public Long getExistingMediaIdAsLong() {
        if (!isRetain()) return null;
        try {
            return Long.parseLong(existingMediaId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}


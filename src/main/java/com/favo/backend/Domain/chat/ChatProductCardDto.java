package com.favo.backend.Domain.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Chat yanıtıyla birlikte dönen, istemcinin kart olarak göstereceği ürün özeti.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatProductCardDto {

    private Long id;
    private String name;
    private String imageURL;
    /** Örn. alt kategori adı */
    private String tagName;
    /** Ortalama yıldız (review yoksa null) */
    private Double averageRating;
    private Long reviewCount;
}

package com.favo.backend.Domain.notification;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Activity / in-app bildirimde aksiyonu yapan kullanıcı özeti (avatar + kimlik).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationActorDto {

    private Long id;
    private String userName;

    /**
     * Profil görseli URL’si; {@code GET /api/users/{id}/profile-image} ile uyumlu.
     * Foto yoksa bile aynı yol döner; istemci 404’te placeholder kullanabilir.
     */
    private String profileImageUrl;
}

package com.favo.backend.Service.User;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Veritabanındaki BLOB için public GET yolunu üretir (görsel hâlâ DB'de; URL sadece endpoint adresi).
 * Foto yoksa bile aynı yol döner; istemci GET'te 404 ile yüz göstermez.
 * {@code app.public-api-base-url} boşsa göreli yol (örn. {@code /api/users/1/profile-image}).
 */
@Service
public class ProfileImageUrlService {

    @Value("${app.public-api-base-url:}")
    private String publicApiBaseUrl;

    public String buildProfileImageUrl(long userId) {
        String path = "/api/users/" + userId + "/profile-image";
        String base = publicApiBaseUrl == null ? "" : publicApiBaseUrl.trim();
        if (base.isEmpty()) {
            return path;
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }
}

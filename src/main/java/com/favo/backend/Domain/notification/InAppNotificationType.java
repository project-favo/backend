package com.favo.backend.Domain.notification;

/**
 * Uygulama içi bildirim türleri.
 */
public enum InAppNotificationType {
    NEW_FOLLOWER,
    /** Başka bir kullanıcı bu kullanıcının yorumunu beğendi. */
    REVIEW_LIKED,
    /** Aynı üründe daha önce yorum yazmış kullanıcılara: biri daha yorum ekledi. */
    NEW_REVIEW_ON_SHARED_PRODUCT,
    /** Ürün listesinde satıcı alanı eklendiğinde kullanılacak (şimdilik üretilmiyor). */
    PRODUCT_LIKED
}

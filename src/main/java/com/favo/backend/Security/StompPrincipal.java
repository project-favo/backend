package com.favo.backend.Security;

import java.security.Principal;

/**
 * STOMP CONNECT sonrası {@link org.springframework.messaging.simp.user.UserDestinationMessageHandler}
 * için {@code convertAndSendToUser(String userId, ...)} ile aynı anahtar: veritabanı kullanıcı id'si.
 */
public record StompPrincipal(long userId) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}

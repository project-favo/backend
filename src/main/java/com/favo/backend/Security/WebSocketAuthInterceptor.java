package com.favo.backend.Security;

import com.favo.backend.Domain.user.SystemUser;
import com.favo.backend.Service.Firebase.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final AuthService authService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("WebSocket CONNECT without Authorization header");
                throw new IllegalArgumentException("UNAUTHORIZED");
            }

            String token = authHeader.substring(7).trim();
            try {
                SystemUser user = authService.login(token);
                String roleName = (user.getUserType() != null && user.getUserType().getName() != null)
                        ? user.getUserType().getName()
                        : SecurityRoles.ROLE_USER;

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        List.of(new SimpleGrantedAuthority(roleName))
                );

                accessor.setUser(authentication);
                log.debug("WebSocket authentication successful for user: {}", user.getId());
            } catch (Exception ex) {
                log.warn("WebSocket authentication failed: {}", ex.getMessage());
                throw new IllegalArgumentException("UNAUTHORIZED");
            }
        }

        return message;
    }
}


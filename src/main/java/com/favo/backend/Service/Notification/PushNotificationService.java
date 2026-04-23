package com.favo.backend.Service.Notification;

import com.favo.backend.Domain.message.Repository.MessageRepository;
import com.favo.backend.Domain.notification.InAppNotificationType;
import com.favo.backend.Domain.notification.PushDeviceToken;
import com.favo.backend.Domain.notification.Repository.InAppNotificationRepository;
import com.favo.backend.Domain.notification.Repository.PushDeviceTokenRepository;
import com.favo.backend.Domain.user.SystemUser;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationService {

    private final PushDeviceTokenRepository pushDeviceTokenRepository;
    private final InAppNotificationRepository inAppNotificationRepository;
    private final MessageRepository messageRepository;
    private final FirebaseApp firebaseApp;

    @Transactional
    public void registerToken(SystemUser user, String token, String platform) {
        String normalized = normalizeToken(token);
        if (normalized == null) {
            throw new IllegalArgumentException("PUSH_TOKEN_REQUIRED");
        }
        String normalizedPlatform = normalizePlatform(platform);
        PushDeviceToken row = pushDeviceTokenRepository.findByToken(normalized)
                .orElseGet(PushDeviceToken::new);
        row.setUser(user);
        row.setToken(normalized);
        row.setPlatform(normalizedPlatform);
        row.setLastSeenAt(LocalDateTime.now());
        row.setIsActive(true);
        pushDeviceTokenRepository.save(row);
    }

    @Transactional(readOnly = true)
    public long badgeCount(Long userId) {
        long inAppUnread = inAppNotificationRepository.countByRecipientIdAndReadAtIsNullAndIsActiveTrue(userId);
        long dmUnread = messageRepository.countTotalUnreadForUser(userId);
        return inAppUnread + dmUnread;
    }

    @Transactional
    public void notifyInAppEvent(Long userId, InAppNotificationType type, String title, String body) {
        if (!isExternalPushType(type)) {
            return;
        }
        sendPush(userId, title, body, "IN_APP_EVENT");
    }

    @Transactional
    public void notifyDirectMessage(Long userId, String title, String body) {
        sendPush(userId, title, body, "DIRECT_MESSAGE");
    }

    @Transactional
    public void syncBadgeOnly(Long userId) {
        sendPush(userId, null, null, "BADGE_SYNC");
    }

    private void sendPush(Long userId, String title, String body, String eventType) {
        List<PushDeviceToken> activeTokens = pushDeviceTokenRepository.findByUserIdAndIsActiveTrue(userId);
        if (activeTokens.isEmpty()) {
            return;
        }

        long badge = badgeCount(userId);
        List<String> tokens = activeTokens.stream()
                .map(PushDeviceToken::getToken)
                .toList();

        MulticastMessage.Builder messageBuilder = MulticastMessage.builder()
                .addAllTokens(tokens)
                .putData("eventType", eventType)
                .putData("badge", String.valueOf(badge));

        if (title != null && !title.isBlank() && body != null && !body.isBlank()) {
            messageBuilder
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder().setBadge((int) badge).build())
                            .build());
        } else {
            messageBuilder
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder().setBadge((int) badge).setContentAvailable(true).build())
                            .build());
        }
        MulticastMessage message = messageBuilder.build();

        try {
            BatchResponse response = FirebaseMessaging.getInstance(firebaseApp).sendEachForMulticast(message);
            deactivateInvalidTokens(activeTokens, response);
        } catch (Exception e) {
            log.warn("Push send failed for user {}: {}", userId, e.getMessage());
        }
    }

    private void deactivateInvalidTokens(List<PushDeviceToken> tokens, BatchResponse response) {
        List<SendResponse> responses = response.getResponses();
        for (int i = 0; i < responses.size() && i < tokens.size(); i++) {
            SendResponse sendResponse = responses.get(i);
            if (sendResponse.isSuccessful()) {
                continue;
            }
            FirebaseMessagingException ex = sendResponse.getException();
            if (ex == null || ex.getMessagingErrorCode() == null) {
                continue;
            }
            if (ex.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                    || ex.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                PushDeviceToken token = tokens.get(i);
                token.setIsActive(false);
                pushDeviceTokenRepository.save(token);
            }
        }
    }

    private static boolean isExternalPushType(InAppNotificationType type) {
        return type == InAppNotificationType.NEW_FOLLOWER
                || type == InAppNotificationType.REVIEW_LIKED;
    }

    private static String normalizeToken(String token) {
        if (token == null) {
            return null;
        }
        String normalized = token.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizePlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            return null;
        }
        return platform.trim().toLowerCase(Locale.ROOT);
    }
}

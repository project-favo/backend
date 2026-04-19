package com.favo.backend.Domain.notification;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InAppNotificationDto {
    private Long id;
    private InAppNotificationType type;
    private String actorDisplayName;
    private String title;
    private String body;
    /** İleride deep link için JSON string; şimdilik opsiyonel. */
    private String payloadJson;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;

    /** Bildirimi tetikleyen kullanıcı; actor yoksa null */
    private NotificationActorDto actor;
}

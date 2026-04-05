package com.favo.backend.Domain.notification;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * WebSocket (/user/queue/notifications) ile gönderilen hafif güncelleme.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPushDto {
    private long unreadCount;
    /** Yeni oluşan bildirim; okundu işaretlemede null olabilir. */
    private InAppNotificationDto notification;
}

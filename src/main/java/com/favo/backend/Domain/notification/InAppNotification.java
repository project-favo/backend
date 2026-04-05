package com.favo.backend.Domain.notification;

import com.favo.backend.Domain.Common.BaseEntity;
import com.favo.backend.Domain.user.SystemUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "in_app_notification", indexes = {
        @Index(name = "idx_in_app_notif_recipient_created", columnList = "recipient_id,created_at"),
        @Index(name = "idx_in_app_notif_recipient_read", columnList = "recipient_id,read_at")
})
@Getter
@Setter
public class InAppNotification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false, foreignKey = @ForeignKey(name = "fk_in_app_notif_recipient"))
    private SystemUser recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", foreignKey = @ForeignKey(name = "fk_in_app_notif_actor"))
    private SystemUser actor;

    /** Liste için önceden hesaplanmış görünen ad (actor silinse bile metin kalır). */
    @Column(name = "actor_display_name", nullable = false, length = 200)
    private String actorDisplayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 64)
    private InAppNotificationType type;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "body", nullable = false, length = 1000)
    private String body;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "read_at")
    private LocalDateTime readAt;
}

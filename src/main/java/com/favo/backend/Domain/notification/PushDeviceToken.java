package com.favo.backend.Domain.notification;

import com.favo.backend.Domain.Common.BaseEntity;
import com.favo.backend.Domain.user.SystemUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "push_device_token",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_push_device_token_value", columnNames = {"token"})
        },
        indexes = {
                @Index(name = "idx_push_device_token_user_active", columnList = "user_id,is_active")
        }
)
@Getter
@Setter
public class PushDeviceToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_push_device_token_user"))
    private SystemUser user;

    @Column(name = "token", nullable = false, length = 512)
    private String token;

    @Column(name = "platform", length = 32)
    private String platform;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;
}

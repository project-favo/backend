package com.favo.backend.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Pre-account registration data; no row in {@code system_user} until e-posta OTP onaylanır.
 * <p>
 * Username eşsizliği: {@code username} sütunu uygulama tarafında kontrol edilir; kalıcı çözüm için
 * MySQL 8'de {@code LOWER(username)} üzerinde generated stored column + UNIQUE index önerilir.
 */
@Entity
@Table(name = "pending_registrations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "firebase_uid", nullable = false, length = 128)
    private String firebaseUid;

    /**
     * Lowercase, trimmed — lookup ve eşleşme için.
     */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "username", nullable = false, length = 50)
    private String userName;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "verification_code_hash", length = 255)
    private String verificationCodeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PendingRegistrationStatus status;
}

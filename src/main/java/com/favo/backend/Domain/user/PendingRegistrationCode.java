package com.favo.backend.Domain.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Kayıt öncesi e-posta doğrulama kodu.
 * Kullanıcı henüz oluşturulmamıştır; kodu doğrulandıktan SONRA Firebase + DB kaydı yapılır.
 */
@Entity
@Table(
    name = "pending_registration_code",
    indexes = @Index(name = "idx_prc_email", columnList = "email")
)
@Getter
@Setter
public class PendingRegistrationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "code_hash", nullable = false, length = 120)
    private String codeHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** null iken doğrulanmamış; dolu ise kod eşleşti, kayıt yapılabilir. */
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    /** Kayıt tamamlandığında dolar → tek kullanımlık. */
    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;
}

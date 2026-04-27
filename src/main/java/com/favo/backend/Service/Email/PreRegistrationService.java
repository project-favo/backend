package com.favo.backend.Service.Email;

import com.favo.backend.Domain.user.PendingRegistrationCode;
import com.favo.backend.Domain.user.Repository.PendingRegistrationCodeRepository;
import com.favo.backend.Domain.user.Repository.SystemUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Kayıt ÖNCE e-posta doğrulama akışı.
 * Henüz hiçbir kullanıcı kaydı yokken 5 haneli kod gönderir ve doğrular.
 * Kod doğrulandıktan sonra Firebase + DB kaydı yapılır.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PreRegistrationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(10);

    private final PendingRegistrationCodeRepository codeRepository;
    private final SystemUserRepository systemUserRepository;
    private final EmailVerificationService emailVerificationService;

    @Value("${app.email.verification.code-ttl-minutes:15}")
    private int codeTtlMinutes;

    @Value("${app.email.verification.resend-cooldown-seconds:60}")
    private int resendCooldownSeconds;

    /**
     * E-postaya 5 haneli doğrulama kodu gönderir.
     * Doğrulanmış aktif kullanıcı varsa 409; cooldown dolmamışsa 400 RESEND_COOLDOWN.
     */
    @Transactional
    public MailSendResult sendCode(String email) {
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("EMAIL_REQUIRED");
        }
        String normalized = email.trim().toLowerCase();

        // Zaten doğrulanmış aktif hesap var mı?
        systemUserRepository.findByEmail(normalized).ifPresent(existing -> {
            if (Boolean.TRUE.equals(existing.getEmailVerified()) && Boolean.TRUE.equals(existing.getIsActive())) {
                throw new RuntimeException("EMAIL_ALREADY_REGISTERED");
            }
        });

        // Cooldown kontrolü
        LocalDateTime now = LocalDateTime.now();
        codeRepository.findTopByEmailOrderByCreatedAtDesc(normalized).ifPresent(last -> {
            long seconds = ChronoUnit.SECONDS.between(last.getCreatedAt(), now);
            if (seconds < resendCooldownSeconds) {
                throw new IllegalArgumentException("RESEND_COOLDOWN");
            }
        });

        // Önceki bekleyen kodları tüket
        codeRepository.consumePendingForEmail(normalized, now);

        String plainCode = String.format("%05d", RANDOM.nextInt(100_000));
        LocalDateTime expires = now.plus(codeTtlMinutes, ChronoUnit.MINUTES);

        PendingRegistrationCode row = new PendingRegistrationCode();
        row.setEmail(normalized);
        row.setCodeHash(BCRYPT.encode(plainCode));
        row.setCreatedAt(now);
        row.setExpiresAt(expires);
        codeRepository.save(row);

        log.info("Pre-registration code issued for email={}", normalized);

        return emailVerificationService.sendSystemPlainEmail(
                normalized,
                "Favo — kayıt doğrulama kodunuz",
                "Kayıt doğrulama kodunuz: " + plainCode
                        + "\n\nBu kod " + codeTtlMinutes + " dakika geçerlidir."
        );
    }

    /**
     * Kullanıcının girdiği kodu doğrular; eşleşirse {@code verifiedAt} doldurulur.
     * Hatalı kod → {@code WRONG_CODE}, aktif kod yok → {@code NO_ACTIVE_CODE}.
     */
    @Transactional
    public void verifyCode(String email, String code) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(code)) {
            throw new IllegalArgumentException("EMAIL_AND_CODE_REQUIRED");
        }
        String normalized = email.trim().toLowerCase();
        String trimmed = code.trim();
        if (!trimmed.matches("\\d{5}")) {
            throw new IllegalArgumentException("INVALID_CODE_FORMAT");
        }

        LocalDateTime now = LocalDateTime.now();
        PendingRegistrationCode pending = codeRepository
                .findTopByEmailAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(normalized, now)
                .orElseThrow(() -> new IllegalArgumentException("NO_ACTIVE_CODE"));

        if (!BCRYPT.matches(trimmed, pending.getCodeHash())) {
            throw new IllegalArgumentException("WRONG_CODE");
        }

        pending.setVerifiedAt(now);
        codeRepository.save(pending);
        log.info("Pre-registration code verified for email={}", normalized);
    }

    /**
     * E-posta için doğrulanmış + kullanılmamış + süresi dolmamış kayıt kodu var mı?
     * (register endpoint'inden önce çağrılır.)
     */
    @Transactional(readOnly = true)
    public boolean isEmailPreVerified(String email) {
        if (!StringUtils.hasText(email)) return false;
        String normalized = email.trim().toLowerCase();
        return codeRepository
                .findTopByEmailAndVerifiedAtIsNotNullAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                        normalized, LocalDateTime.now())
                .isPresent();
    }

    /**
     * Kayıt tamamlandıktan sonra kodu tüket (tek kullanımlık).
     */
    @Transactional
    public void consumeVerification(String email) {
        if (!StringUtils.hasText(email)) return;
        codeRepository.consumePendingForEmail(email.trim().toLowerCase(), LocalDateTime.now());
    }
}

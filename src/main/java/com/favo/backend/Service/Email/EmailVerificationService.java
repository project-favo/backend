package com.favo.backend.Service.Email;

import com.favo.backend.Domain.user.EmailVerificationCode;
import com.favo.backend.Domain.user.Repository.EmailVerificationCodeRepository;
import com.favo.backend.Domain.user.Repository.SystemUserRepository;
import com.favo.backend.Domain.user.SystemUser;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(10);

    private final EmailVerificationCodeRepository codeRepository;
    private final SystemUserRepository systemUserRepository;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.mail.from:}")
    private String mailFrom;

    @Value("${app.email.verification.log-code-if-mail-disabled:false}")
    private boolean logCodeIfMailDisabled;

    @Value("${app.email.verification.resend-cooldown-seconds:60}")
    private int resendCooldownSeconds;

    @Value("${app.email.verification.code-ttl-minutes:15}")
    private int codeTtlMinutes;

    /**
     * Kodu DB'ye yazar ve SMTP ile göndermeyi dener.
     *
     * @return SMTP ile gönderim başarılıysa true; yapılandırma eksik / gönderim hatası / alıcı e-posta boşsa false.
     *         Zaten doğrulanmış kullanıcı için işlem yapılmaz ve true döner.
     */
    @Transactional
    public boolean issueVerificationEmail(SystemUser user) {
        SystemUser u = systemUserRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalStateException("User not found: " + user.getId()));
        if (Boolean.TRUE.equals(u.getEmailVerified())) {
            return true;
        }

        String plainCode = String.format("%05d", RANDOM.nextInt(100_000));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expires = now.plus(codeTtlMinutes, ChronoUnit.MINUTES);

        codeRepository.consumePendingForUser(u.getId(), now);

        EmailVerificationCode row = new EmailVerificationCode();
        row.setUser(u);
        row.setCodeHash(BCRYPT.encode(plainCode));
        row.setExpiresAt(expires);
        row.setCreatedAt(now);
        row.setIsActive(true);
        codeRepository.save(row);

        return sendOrLog(u.getEmail(), plainCode);
    }

    /**
     * Kod doğrulanır, email_verified DB'ye yazılır ve UserType ile birlikte yeniden yüklenmiş kullanıcı döner.
     * (OSIV / önbellekte eski entity kalmaması için flush + fetch join ile tekrar okuma.)
     */
    @Transactional
    public SystemUser verifyCode(SystemUser user, String rawCode) {
        SystemUser u = systemUserRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND"));
        if (Boolean.TRUE.equals(u.getEmailVerified())) {
            return systemUserRepository.findByIdWithUserType(u.getId()).orElse(u);
        }
        if (!StringUtils.hasText(rawCode) || !rawCode.trim().matches("\\d{5}")) {
            throw new IllegalArgumentException("INVALID_CODE_FORMAT");
        }
        String code = rawCode.trim();
        LocalDateTime now = LocalDateTime.now();

        EmailVerificationCode pending = codeRepository
                .findFirstByUser_IdAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(u.getId(), now)
                .orElseThrow(() -> new IllegalArgumentException("NO_ACTIVE_CODE"));

        if (!BCRYPT.matches(code, pending.getCodeHash())) {
            throw new IllegalArgumentException("WRONG_CODE");
        }

        pending.setConsumedAt(now);
        codeRepository.save(pending);

        int updated = systemUserRepository.markEmailVerifiedTrue(u.getId());
        if (updated != 1) {
            log.error("email_verified güncellenemedi: userId={} etkilenenSatir={}", u.getId(), updated);
            throw new IllegalStateException("EMAIL_VERIFY_PERSIST_FAILED");
        }

        return systemUserRepository.findByIdWithUserType(u.getId())
                .orElseThrow(() -> new IllegalStateException("USER_NOT_FOUND_AFTER_VERIFY"));
    }

    @Transactional
    public void resendVerificationEmail(SystemUser user) {
        SystemUser u = systemUserRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND"));
        if (Boolean.TRUE.equals(u.getEmailVerified())) {
            throw new IllegalArgumentException("ALREADY_VERIFIED");
        }
        LocalDateTime now = LocalDateTime.now();
        if (u.getVerificationEmailLastResendAt() != null) {
            long seconds = ChronoUnit.SECONDS.between(u.getVerificationEmailLastResendAt(), now);
            if (seconds < resendCooldownSeconds) {
                throw new IllegalArgumentException("RESEND_COOLDOWN");
            }
        }
        boolean sent = issueVerificationEmail(u);
        if (sent) {
            SystemUser fresh = systemUserRepository.findById(u.getId()).orElseThrow();
            fresh.setVerificationEmailLastResendAt(now);
            systemUserRepository.save(fresh);
        }
    }

    private boolean sendOrLog(String toEmail, String plainCode) {
        if (!StringUtils.hasText(toEmail)) {
            log.error("Doğrulama e-postası gönderilemedi: kullanıcı e-postası boş (Firebase token'da email yok olabilir).");
            return false;
        }
        String user = mailUsername != null ? mailUsername.trim() : "";
        String fromProp = mailFrom != null ? mailFrom.trim() : "";
        String from = StringUtils.hasText(fromProp) ? fromProp : user;
        if (mailSender == null) {
            log.warn(
                    "Doğrulama e-postası gönderilmedi: JavaMailSender yok (spring-boot-starter-mail classpath'te değil veya mail otoconfig kapalı). "
                            + "Kod veritabanına yazıldı; Railway'de MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM ayarlayın.");
            maybeLogCode(toEmail, plainCode);
            return false;
        }
        if (!StringUtils.hasText(user)) {
            log.warn(
                    "Doğrulama e-postası gönderilmedi: MAIL_USERNAME / spring.mail.username boş. "
                            + "SMTP kullanıcı ve şifre (ör. Gmail App Password) ortam değişkenlerinde tanımlı olmalı.");
            maybeLogCode(toEmail, plainCode);
            return false;
        }
        if (!StringUtils.hasText(from)) {
            log.warn(
                    "Doğrulama e-postası gönderilmedi: MAIL_FROM / app.mail.from boş ve MAIL_USERNAME da boş sayıldı. "
                            + "Gmail'de MAIL_FROM genelde MAIL_USERNAME ile aynı adrestir.");
            maybeLogCode(toEmail, plainCode);
            return false;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(toEmail);
            msg.setSubject("Favo — e-posta doğrulama kodunuz");
            msg.setText("Doğrulama kodunuz: " + plainCode + "\n\nBu kod " + codeTtlMinutes + " dakika geçerlidir.");
            mailSender.send(msg);
            log.info("Verification email sent to {}", toEmail);
            return true;
        } catch (Exception e) {
            log.error(
                    "SMTP gönderimi başarısız (to={}). Gmail: Uygulama şifresi, 2FA, MAIL_FROM=MAIL_USERNAME. Tam hata:",
                    toEmail, e);
            maybeLogCode(toEmail, plainCode);
            return false;
        }
    }

    private void maybeLogCode(String toEmail, String plainCode) {
        if (logCodeIfMailDisabled) {
            log.warn("DEV: verification code for {} is {}", toEmail, plainCode);
        }
    }
}

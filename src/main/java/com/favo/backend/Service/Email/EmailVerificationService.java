package com.favo.backend.Service.Email;

import com.favo.backend.Domain.user.EmailVerificationCode;
import com.favo.backend.Domain.user.Repository.EmailVerificationCodeRepository;
import com.favo.backend.Domain.user.Repository.SystemUserRepository;
import com.favo.backend.Domain.user.SystemUser;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
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

    @Transactional
    public void issueVerificationEmail(SystemUser user) {
        SystemUser u = systemUserRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalStateException("User not found: " + user.getId()));
        if (Boolean.TRUE.equals(u.getEmailVerified())) {
            return;
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

        sendOrLog(u.getEmail(), plainCode);
    }

    @Transactional
    public void verifyCode(SystemUser user, String rawCode) {
        SystemUser u = systemUserRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND"));
        if (Boolean.TRUE.equals(u.getEmailVerified())) {
            return;
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

        u.setEmailVerified(true);
        systemUserRepository.save(u);
    }

    @Transactional
    public void resendVerificationEmail(SystemUser user) {
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new IllegalArgumentException("ALREADY_VERIFIED");
        }
        LocalDateTime now = LocalDateTime.now();
        codeRepository.findTopByUser_IdOrderByCreatedAtDesc(user.getId()).ifPresent(last -> {
            if (last.getCreatedAt() != null) {
                long seconds = ChronoUnit.SECONDS.between(last.getCreatedAt(), now);
                if (seconds < resendCooldownSeconds) {
                    throw new IllegalArgumentException("RESEND_COOLDOWN");
                }
            }
        });
        issueVerificationEmail(user);
    }

    private void sendOrLog(String toEmail, String plainCode) {
        if (!StringUtils.hasText(toEmail)) {
            log.error("Doğrulama e-postası gönderilemedi: kullanıcı e-postası boş (Firebase token'da email yok olabilir).");
            return;
        }
        String from = StringUtils.hasText(mailFrom) ? mailFrom : mailUsername;
        if (mailSender == null) {
            log.warn(
                    "Doğrulama e-postası gönderilmedi: JavaMailSender yok (spring-boot-starter-mail classpath'te değil veya mail otoconfig kapalı). "
                            + "Kod veritabanına yazıldı; Railway'de MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM ayarlayın.");
            maybeLogCode(toEmail, plainCode);
            return;
        }
        if (!StringUtils.hasText(mailUsername)) {
            log.warn(
                    "Doğrulama e-postası gönderilmedi: MAIL_USERNAME / spring.mail.username boş. "
                            + "SMTP kullanıcı ve şifre (ör. Gmail App Password) ortam değişkenlerinde tanımlı olmalı.");
            maybeLogCode(toEmail, plainCode);
            return;
        }
        if (!StringUtils.hasText(from)) {
            log.warn(
                    "Doğrulama e-postası gönderilmedi: MAIL_FROM / app.mail.from boş. "
                            + "Gmail'de genelde MAIL_FROM, MAIL_USERNAME ile aynı adrestir.");
            maybeLogCode(toEmail, plainCode);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(toEmail);
            msg.setSubject("Favo — e-posta doğrulama kodunuz");
            msg.setText("Doğrulama kodunuz: " + plainCode + "\n\nBu kod " + codeTtlMinutes + " dakika geçerlidir.");
            mailSender.send(msg);
            log.info("Verification email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("SMTP gönderimi başarısız ({}): {} — MAIL_HOST/PORT veya şifre kontrol edin.",
                    toEmail, e.getMessage());
            maybeLogCode(toEmail, plainCode);
        }
    }

    private void maybeLogCode(String toEmail, String plainCode) {
        if (logCodeIfMailDisabled) {
            log.warn("DEV: verification code for {} is {}", toEmail, plainCode);
        }
    }
}

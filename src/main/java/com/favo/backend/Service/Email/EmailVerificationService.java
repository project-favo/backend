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
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
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
    private final Environment environment;
    private final ResendEmailClient resendEmailClient;

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
     * Zaten doğrulanmış kullanıcı için kod üretilmez; {@link MailSendResult#ok()} döner.
     */
    @Transactional
    public MailSendResult issueVerificationEmail(SystemUser user) {
        SystemUser u = systemUserRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalStateException("User not found: " + user.getId()));
        if (Boolean.TRUE.equals(u.getEmailVerified())) {
            return MailSendResult.ok();
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

        return trySendVerificationMail(u.getEmail(), plainCode);
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
    public MailSendResult resendVerificationEmail(SystemUser user) {
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
        MailSendResult mail = issueVerificationEmail(u);
        if (mail.sent()) {
            SystemUser fresh = systemUserRepository.findById(u.getId()).orElseThrow();
            fresh.setVerificationEmailLastResendAt(now);
            systemUserRepository.save(fresh);
        }
        return mail;
    }

    private MailSendResult trySendVerificationMail(String toEmail, String plainCode) {
        if (!StringUtils.hasText(toEmail)) {
            log.error("Doğrulama e-postası gönderilemedi: kullanıcı e-postası boş (Firebase token'da email yok olabilir).");
            return MailSendResult.fail("EMPTY_USER_EMAIL");
        }

        if (resendEmailClient.isConfigured()) {
            String subject = "Favo — e-posta doğrulama kodunuz";
            String text = "Doğrulama kodunuz: " + plainCode + "\n\nBu kod " + codeTtlMinutes + " dakika geçerlidir.";
            MailSendResult r = resendEmailClient.sendPlain(toEmail.trim(), subject, text);
            if (!r.sent()) {
                maybeLogCode(toEmail, plainCode);
            }
            return r;
        }

        syncMailSenderFromEnvironmentIfNeeded();

        String user = resolveSmtpUsername();
        String from = resolveFromAddress(user);
        if (mailSender == null) {
            log.warn("Doğrulama e-postası gönderilmedi: JavaMailSender yok. Railway'de MAIL_* ayarlayın.");
            maybeLogCode(toEmail, plainCode);
            return MailSendResult.fail("NO_MAIL_SENDER_BEAN");
        }
        if (!StringUtils.hasText(user)) {
            log.warn("Doğrulama e-postası gönderilmedi: MAIL_USERNAME boş.");
            maybeLogCode(toEmail, plainCode);
            return MailSendResult.fail("MISSING_MAIL_USERNAME");
        }
        if (!StringUtils.hasText(from)) {
            log.warn("Doğrulama e-postası gönderilmedi: MAIL_FROM / MAIL_USERNAME gönderen için yetersiz.");
            maybeLogCode(toEmail, plainCode);
            return MailSendResult.fail("MISSING_MAIL_FROM");
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(toEmail);
            msg.setSubject("Favo — e-posta doğrulama kodunuz");
            msg.setText("Doğrulama kodunuz: " + plainCode + "\n\nBu kod " + codeTtlMinutes + " dakika geçerlidir.");
            mailSender.send(msg);
            log.info("Verification email sent to {}", toEmail);
            return MailSendResult.ok();
        } catch (Exception e) {
            String hint = e.getClass().getSimpleName() + ": "
                    + (e.getMessage() != null ? e.getMessage().replaceAll("\\s+", " ").trim() : "");
            if (hint.length() > 200) {
                hint = hint.substring(0, 200) + "...";
            }
            log.error("SMTP gönderimi başarısız (to={}). {}", toEmail, hint, e);
            maybeLogCode(toEmail, plainCode);
            return MailSendResult.fail("SMTP_REJECTED", hint);
        }
    }

    private void maybeLogCode(String toEmail, String plainCode) {
        if (logCodeIfMailDisabled) {
            log.warn("DEV: verification code for {} is {}", toEmail, plainCode);
        }
    }

    /** @Value bazen boş kalır; Railway OS env doğrudan okunur. */
    private String resolveSmtpUsername() {
        if (StringUtils.hasText(mailUsername)) {
            return mailUsername.trim();
        }
        String u = environment.getProperty("MAIL_USERNAME");
        if (!StringUtils.hasText(u)) {
            u = environment.getProperty("SPRING_MAIL_USERNAME");
        }
        return u != null ? u.trim() : "";
    }

    private String resolveFromAddress(String smtpUsername) {
        if (StringUtils.hasText(mailFrom)) {
            return mailFrom.trim();
        }
        String f = environment.getProperty("MAIL_FROM");
        if (!StringUtils.hasText(f)) {
            f = environment.getProperty("SPRING_MAIL_FROM");
        }
        if (StringUtils.hasText(f)) {
            return f.trim();
        }
        return smtpUsername;
    }

    /** SMTP gönderiminden hemen önce: bean boş kaldıysa OS env ile doldur. */
    private void syncMailSenderFromEnvironmentIfNeeded() {
        if (!(mailSender instanceof JavaMailSenderImpl impl)) {
            return;
        }
        String envUser = firstEnv("MAIL_USERNAME", "SPRING_MAIL_USERNAME");
        String envPass = firstEnv("MAIL_PASSWORD", "SPRING_MAIL_PASSWORD");
        if (!StringUtils.hasText(impl.getUsername()) && StringUtils.hasText(envUser)) {
            impl.setUsername(envUser.trim());
        }
        if (!StringUtils.hasText(impl.getPassword()) && StringUtils.hasText(envPass)) {
            impl.setPassword(envPass.replaceAll("\\s+", ""));
        }
    }

    private String firstEnv(String a, String b) {
        String x = environment.getProperty(a);
        if (StringUtils.hasText(x)) {
            return x;
        }
        x = environment.getProperty(b);
        return StringUtils.hasText(x) ? x : null;
    }
}

package com.favo.backend.config;

import com.favo.backend.Service.Email.ResendEmailClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Açılışta SMTP yapılandırmasının özetini loglar (şifre asla yazılmaz). Railway deploy loglarında teşhis için.
 */
@Component
@Slf4j
public class MailStartupDiagnostics {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final ResendEmailClient resendEmailClient;

    @Value("${spring.mail.host}")
    private String host;

    @Value("${spring.mail.port}")
    private int port;

    @Value("${spring.mail.username:}")
    private String username;

    @Value("${spring.mail.password:}")
    private String password;

    @Value("${app.mail.from:}")
    private String from;

    public MailStartupDiagnostics(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            ResendEmailClient resendEmailClient) {
        this.mailSenderProvider = mailSenderProvider;
        this.resendEmailClient = resendEmailClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logMailReadiness() {
        boolean userSet = StringUtils.hasText(username);
        boolean passSet = StringUtils.hasText(password);
        boolean fromSet = StringUtils.hasText(from);
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        boolean resend = resendEmailClient.isConfigured();

        log.info(
                "E-posta (doğrulama): ResendHttp={} | SMTP host={} port={} JavaMailSenderBean={} usernameSet={} passwordSet={} mailFromSet={}",
                resend, host, port, sender != null, userSet, passSet, fromSet);

        if (resend) {
            log.info("Resend aktif: Railway SMTP engeli olsa bile doğrulama maili HTTPS ile gider. RESEND_FROM Resend panelinde doğrulanmış olmalı.");
        }

        if (!resend && (!userSet || !passSet)) {
            log.warn(
                    "Doğrulama e-postası gönderilemez: MAIL_USERNAME / MAIL_PASSWORD boş ve RESEND_API_KEY yok. "
                            + "Railway Hobby: Resend önerilir (RESEND_API_KEY + RESEND_FROM).");
        } else if (!resend && !fromSet) {
            log.info("MAIL_FROM boş; gönderen adresi MAIL_USERNAME ile aynı kabul edilir (EmailVerificationService).");
        }

        if (!resend && userSet && passSet && sender == null) {
            log.warn("JavaMailSender bean yok; spring-boot-starter-mail ve spring.mail.host beklenir.");
        }
    }
}

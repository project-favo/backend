package com.favo.backend.config;

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

    public MailStartupDiagnostics(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logMailReadiness() {
        boolean userSet = StringUtils.hasText(username);
        boolean passSet = StringUtils.hasText(password);
        boolean fromSet = StringUtils.hasText(from);
        JavaMailSender sender = mailSenderProvider.getIfAvailable();

        log.info(
                "E-posta (doğrulama): host={} port={} JavaMailSenderBean={} usernameSet={} passwordSet={} mailFromSet={}",
                host, port, sender != null, userSet, passSet, fromSet);

        if (!userSet || !passSet) {
            log.warn(
                    "Doğrulama e-postası gönderilemez: MAIL_USERNAME / MAIL_PASSWORD (veya SPRING_MAIL_*) boş. "
                            + "Değişkenler backend Railway servisinde tanımlı olmalı; deploy sonrası bu logu kontrol et.");
        } else if (!fromSet) {
            log.info("MAIL_FROM boş; gönderen adresi MAIL_USERNAME ile aynı kabul edilir (EmailVerificationService).");
        }

        if (userSet && passSet && sender == null) {
            log.warn("JavaMailSender bean yok; spring-boot-starter-mail ve spring.mail.host beklenir.");
        }
    }
}

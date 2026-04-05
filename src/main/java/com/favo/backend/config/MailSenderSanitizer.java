package com.favo.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/**
 * Gmail uygulama şifresindeki boşlukları kaldırır; Railway'de yanlış yapıştırmayı tolere eder.
 */
@Component
@Slf4j
public class MailSenderSanitizer implements ApplicationRunner {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    public MailSenderSanitizer(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    @Override
    public void run(ApplicationArguments args) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender instanceof JavaMailSenderImpl impl) {
            String u = impl.getUsername();
            if (u != null) {
                impl.setUsername(u.trim());
            }
            String p = impl.getPassword();
            if (p != null) {
                String compact = p.replaceAll("\\s+", "");
                if (!compact.equals(p)) {
                    impl.setPassword(compact);
                    log.info("MAIL_PASSWORD içindeki boşluklar kaldırıldı (Gmail uygulama şifresi).");
                }
            }
        }
    }
}

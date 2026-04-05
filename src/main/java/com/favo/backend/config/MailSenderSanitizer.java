package com.favo.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Railway: MAIL_* bazen Spring'in property zincirine düşmeden OS env'de kalır; bean boş kalabiliyor.
 * Burada doğrudan Environment + trim + uygulama şifresi boşlukları.
 */
@Component
@Slf4j
public class MailSenderSanitizer implements ApplicationRunner {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final Environment environment;

    public MailSenderSanitizer(ObjectProvider<JavaMailSender> mailSenderProvider, Environment environment) {
        this.mailSenderProvider = mailSenderProvider;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (!(sender instanceof JavaMailSenderImpl impl)) {
            return;
        }

        String envUser = firstNonBlank(
                environment.getProperty("MAIL_USERNAME"),
                environment.getProperty("SPRING_MAIL_USERNAME"));
        String envPass = firstNonBlank(
                environment.getProperty("MAIL_PASSWORD"),
                environment.getProperty("SPRING_MAIL_PASSWORD"));

        if (!StringUtils.hasText(impl.getUsername()) && StringUtils.hasText(envUser)) {
            impl.setUsername(envUser.trim());
            log.info("JavaMailSender username OS env'den dolduruldu (MAIL_USERNAME / SPRING_MAIL_USERNAME).");
        } else if (StringUtils.hasText(impl.getUsername())) {
            impl.setUsername(impl.getUsername().trim());
        }

        if (!StringUtils.hasText(impl.getPassword()) && StringUtils.hasText(envPass)) {
            impl.setPassword(envPass.replaceAll("\\s+", ""));
            log.info("JavaMailSender password OS env'den dolduruldu (MAIL_PASSWORD).");
        } else if (StringUtils.hasText(impl.getPassword())) {
            String compact = impl.getPassword().replaceAll("\\s+", "");
            if (!compact.equals(impl.getPassword())) {
                impl.setPassword(compact);
                log.info("MAIL_PASSWORD içindeki boşluklar kaldırıldı (Gmail uygulama şifresi).");
            }
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) {
            return a;
        }
        return StringUtils.hasText(b) ? b : null;
    }
}

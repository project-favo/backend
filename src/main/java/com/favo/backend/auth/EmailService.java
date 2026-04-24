package com.favo.backend.auth;

import com.favo.backend.Service.Email.MailSendResult;
import com.favo.backend.Service.Email.ResendEmailClient;
import com.favo.backend.common.error.EmailErrorCode;
import com.favo.backend.common.error.FavoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Kayıt OTP dâhil transactional e-posta gönderimleri. Resend HTTPS API kullanır
 * (yapılandırma: {@code resend.api.key}, {@code resend.from}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private static final int OTP_EXPIRY_MINUTES = 15;

    private final ResendEmailClient resendEmailClient;

    /**
     * Favo kayıt doğrulama kodu gönderir. Başarısız Resend cevabında
     * {@link EmailErrorCode#EMAIL_DELIVERY_FAILED} fırlatılır.
     */
    public void sendVerificationEmail(String email, String rawOtp) {
        String subject = "Your Favo verification code";
        String text = "Your Favo verification code is: " + rawOtp + ". Expires in " + OTP_EXPIRY_MINUTES + " minutes.";

        MailSendResult r = resendEmailClient.sendPlain(email, subject, text);
        if (r == null || !r.sent()) {
            String code = r != null && r.failureCode() != null ? r.failureCode() : "UNKNOWN";
            log.error("Resend failed for email={}, failureCode={}", email, code);
            throw new FavoException(EmailErrorCode.EMAIL_DELIVERY_FAILED, Map.of("failureCode", code));
        }
    }
}

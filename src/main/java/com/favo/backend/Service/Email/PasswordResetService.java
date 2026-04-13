package com.favo.backend.Service.Email;

import com.favo.backend.Domain.user.Repository.SystemUserRepository;
import com.favo.backend.Domain.user.SystemUser;
import com.favo.backend.Service.Firebase.FirebaseAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Firebase Auth şifre sıfırlama bağlantısı üretir; Resend/SMTP ile gönderir.
 * Hesap yoksa veya pasifse aynı yanıt (e-posta sızdırmama).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final SystemUserRepository systemUserRepository;
    private final FirebaseAuthService firebaseAuthService;
    private final EmailVerificationService emailVerificationService;

    @Transactional(readOnly = true)
    public void requestPasswordReset(String rawEmail) {
        if (!StringUtils.hasText(rawEmail)) {
            return;
        }
        String email = rawEmail.trim();
        SystemUser user = systemUserRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getIsActive())) {
            log.debug("Şifre sıfırlama isteği: kayıt yok veya pasif (email hashlenmez).");
            return;
        }

        try {
            String link = firebaseAuthService.generatePasswordResetLink(user.getEmail());
            String subject = "Favo — şifre sıfırlama";
            String text = "Şifrenizi sıfırlamak için aşağıdaki bağlantıya tıklayın:\n\n"
                    + link
                    + "\n\nBağlantı süreli olarak geçerlidir. Bu isteği siz yapmadıysanız bu e-postayı yok sayabilirsiniz.";
            MailSendResult mail = emailVerificationService.sendSystemPlainEmail(user.getEmail(), subject, text);
            if (!mail.sent()) {
                log.error(
                        "Şifre sıfırlama e-postası gönderilemedi email={} failureCode={} detail={}",
                        user.getEmail(), mail.failureCode(), mail.smtpDetail());
            }
        } catch (RuntimeException e) {
            log.error("Şifre sıfırlama bağlantısı veya gönderim hatası email={}", user.getEmail(), e);
        }
    }
}

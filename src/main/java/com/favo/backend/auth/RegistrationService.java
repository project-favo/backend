package com.favo.backend.auth;

import com.favo.backend.Domain.user.FirebaseUserInfo;
import com.favo.backend.Domain.user.GeneralUser;
import com.favo.backend.Domain.user.Repository.SystemUserRepository;
import com.favo.backend.Domain.user.Repository.UserTypeRepository;
import com.favo.backend.Domain.user.SystemUser;
import com.favo.backend.Security.SecurityRoles;
import com.favo.backend.Service.User.ProfileImageUrlService;
import com.favo.backend.auth.entity.PendingRegistration;
import com.favo.backend.auth.entity.PendingRegistrationStatus;
import com.favo.backend.auth.event.UserRegisteredEvent;
import com.favo.backend.auth.repository.PendingRegistrationRepository;
import com.favo.backend.auth.repository.UserRepository;
import com.favo.backend.common.error.AuthErrorCode;
import com.favo.backend.common.error.EmailErrorCode;
import com.favo.backend.common.error.FavoException;
import com.favo.backend.common.error.SystemErrorCode;
import com.favo.backend.common.error.UserErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Firebase kimliği doğrulandıktan sonra (controller öncesi filtre) kullanıcı adı kontrolü, bekleyen
 * kayıt, OTP e-posta ve (ayrı uç) doğrulamadan sonra kalıcı {@link com.favo.backend.Domain.user.SystemUser} oluşumu.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(10);

    private final UserRepository userRepository;
    private final SystemUserRepository systemUserRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final RegistrationPersistence registrationPersistence;
    private final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;
    private final UserTypeRepository userTypeRepository;
    private final ProfileImageUrlService profileImageUrlService;

    /**
     * Önkoşul: {@link com.favo.backend.Security.FirebaseAuthenticationFilter} Firebase token'ı doğrulamış ve
     * e-posta / uid sağlanmış. Sonrasında: kullanıcı adı müsaitliği, {@code pending_registrations} satırı,
     * 6 haneli OTP, BCrypt hash ve Resend ile gönderim.
     *
     * @throws FavoException {@link UserErrorCode#USER_FIELD_VALIDATION_FAILED}, {@link AuthErrorCode#AUTH_ACCOUNT_ALREADY_REGISTERED},
     *                       {@link UserErrorCode#USER_USERNAME_TAKEN}, {@link EmailErrorCode#EMAIL_ADDRESS_FORMAT_INVALID},
     *                       {@link EmailErrorCode#EMAIL_DELIVERY_FAILED}
     */
    public void initiateRegistration(FirebaseUserInfo firebase, RegisterRequestDto dto) {
        if (firebase == null || !StringUtils.hasText(firebase.getUid())) {
            throw new FavoException(UserErrorCode.USER_FIELD_VALIDATION_FAILED, Map.of("field", "firebase"));
        }
        if (!firebase.getUid().equals(dto.getFirebaseUid())) {
            throw new FavoException(UserErrorCode.USER_FIELD_VALIDATION_FAILED, Map.of("field", "firebaseUid"));
        }
        if (systemUserRepository.existsByFirebaseUid(firebase.getUid())) {
            throw new FavoException(AuthErrorCode.AUTH_ACCOUNT_ALREADY_REGISTERED);
        }
        if (userRepository.isUsernameTakenInUsersOrPending(dto.getUsername())) {
            throw new FavoException(UserErrorCode.USER_USERNAME_TAKEN);
        }
        String email = normalizeEmail(firebase.getEmail());
        if (!StringUtils.hasText(email) || !email.contains("@")) {
            throw new FavoException(EmailErrorCode.EMAIL_ADDRESS_FORMAT_INVALID);
        }

        registrationPersistence.deletePendingForFirebase(firebase.getUid());
        Instant expires = Instant.now().plus(15, ChronoUnit.MINUTES);
        PendingRegistration shell = registrationPersistence.createPendingShell(
                firebase.getUid(),
                email,
                dto.getUsername().trim(),
                dto.getDisplayName().trim(),
                expires);

        int numericOtp = 100_000 + RANDOM.nextInt(900_000);
        String rawOtp = String.format("%06d", numericOtp);
        String hash = BCRYPT.encode(rawOtp);
        registrationPersistence.applyOtpHash(shell.getId(), hash);
        emailService.sendVerificationEmail(email, rawOtp);
        log.info("Verification email sent (username={})", dto.getUsername());
    }

    /**
     * E-posta + OTP: deneme sınırı, süre, hash eşleşmesi. Başarılı eşleşmede
     * {@link com.favo.backend.auth.entity.PendingRegistration} hâlâ PENDING; kalıcı kullanıcı
     * {@link #finalizeRegistrationAfterOtp(long)} içinde yaratılır.
     *
     * @return tamamlanacak {@link PendingRegistration} birincil anahtarı
     * @throws FavoException {@link EmailErrorCode#EMAIL_VERIFICATION_TOKEN_NOT_FOUND},
     *                       {@link EmailErrorCode#EMAIL_VERIFICATION_TOKEN_EXPIRED},
     *                       {@link EmailErrorCode#EMAIL_VERIFICATION_RATE_LIMIT},
     *                       {@link EmailErrorCode#EMAIL_VERIFICATION_TOKEN_INVALID}
     */
    @Transactional(rollbackFor = Exception.class)
    public long validateOtpForRegistration(String emailRaw, String code) {
        String email = normalizeEmail(emailRaw);
        String submitted = code != null ? code.trim() : "";
        if (!submitted.matches("\\d{6}")) {
            throw new FavoException(EmailErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID);
        }
        Optional<PendingRegistration> opt = pendingRegistrationRepository
                .findByEmailLowerAndStatus(email, PendingRegistrationStatus.PENDING);
        if (opt.isEmpty()) {
            throw new FavoException(EmailErrorCode.EMAIL_VERIFICATION_TOKEN_NOT_FOUND);
        }
        PendingRegistration p = opt.get();
        if (p.getExpiresAt().isBefore(Instant.now())) {
            p.setStatus(PendingRegistrationStatus.EXPIRED);
            pendingRegistrationRepository.save(p);
            throw new FavoException(EmailErrorCode.EMAIL_VERIFICATION_TOKEN_EXPIRED);
        }
        if (p.getAttemptCount() >= 5) {
            throw new FavoException(EmailErrorCode.EMAIL_VERIFICATION_RATE_LIMIT);
        }
        p.setAttemptCount(p.getAttemptCount() + 1);
        pendingRegistrationRepository.save(p);
        if (p.getVerificationCodeHash() == null) {
            throw new FavoException(EmailErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID);
        }
        if (!BCRYPT.matches(submitted, p.getVerificationCodeHash())) {
            log.warn("Invalid verification attempt for email prefix={}", emailPrefix(email));
            throw new FavoException(EmailErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID);
        }
        return p.getId();
    }

    /**
     * Sadece başarılı OTP sonrası; tek seferde REPEATABLE_READ altında tekrar kullanıcı adı kontrolü,
     * {@link com.favo.backend.Domain.user.GeneralUser} ekleme, pending COMPLETED, etkinlik yayını.
     *
     * @throws FavoException {@link UserErrorCode#USER_USERNAME_TAKEN}, {@link AuthErrorCode#AUTH_ACCOUNT_ALREADY_REGISTERED},
     *                       {@link SystemErrorCode#SYSTEM_DB_TRANSACTION_ROLLBACK} (beklenmeyen kalıcılık hataları)
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.REPEATABLE_READ)
    public com.favo.backend.auth.UserResponseDto finalizeRegistrationAfterOtp(long pendingId) {
        try {
            PendingRegistration p = pendingRegistrationRepository.findById(pendingId)
                    .orElseThrow(() -> new FavoException(EmailErrorCode.EMAIL_VERIFICATION_TOKEN_NOT_FOUND));
            if (p.getStatus() != PendingRegistrationStatus.PENDING) {
                throw new FavoException(EmailErrorCode.EMAIL_VERIFICATION_TOKEN_NOT_FOUND);
            }
            if (systemUserRepository.existsByUserNameIgnoreCaseAndIsActiveTrue(p.getUserName())) {
                throw new FavoException(UserErrorCode.USER_USERNAME_TAKEN);
            }
            if (systemUserRepository.existsByFirebaseUid(p.getFirebaseUid())) {
                throw new FavoException(AuthErrorCode.AUTH_ACCOUNT_ALREADY_REGISTERED);
            }
            var userType = userTypeRepository
                    .findByName(SecurityRoles.ROLE_USER)
                    .orElseThrow(() -> new FavoException(SystemErrorCode.SYSTEM_CONFIGURATION_INVALID));
            SystemUser user = new GeneralUser();
            user.setFirebaseUid(p.getFirebaseUid());
            user.setEmail(p.getEmail());
            user.setUserName(p.getUserName());
            user.setName(p.getDisplayName());
            user.setSurname(null);
            user.setBirthdate(null);
            user.setUserType(userType);
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(LocalDateTime.now());
            user.setProfileAnonymous(false);
            user.setIsActive(true);
            user.setCreatedAt(LocalDateTime.now());
            SystemUser saved = systemUserRepository.save(user);
            p.setStatus(PendingRegistrationStatus.COMPLETED);
            pendingRegistrationRepository.save(p);
            eventPublisher.publishEvent(
                    new UserRegisteredEvent(saved.getId(), saved.getUserName(), saved.getEmail()));
            return toAuthUserResponse(saved, profileImageUrlService.buildProfileImageUrl(saved.getId()));
        } catch (FavoException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Registration DB error pendingId={}", pendingId, e);
            throw new FavoException(SystemErrorCode.SYSTEM_DB_TRANSACTION_ROLLBACK, e);
        } catch (Exception e) {
            log.error("Registration commit failed pendingId={}", pendingId, e);
            throw new FavoException(SystemErrorCode.SYSTEM_DB_TRANSACTION_ROLLBACK, e);
        }
    }

    private static com.favo.backend.auth.UserResponseDto toAuthUserResponse(SystemUser user, String profileImageUrl) {
        return new com.favo.backend.auth.UserResponseDto(
                user.getId(),
                user.getEmail(),
                user.getUserName(),
                user.getName(),
                user.getUserType() != null ? user.getUserType().getName() : SecurityRoles.ROLE_USER,
                Boolean.TRUE.equals(user.getIsActive()),
                !Boolean.FALSE.equals(user.getEmailVerified()),
                profileImageUrl
        );
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String emailPrefix(String email) {
        if (email == null || email.length() < 2) {
            return "**";
        }
        return email.substring(0, 2) + "***";
    }
}

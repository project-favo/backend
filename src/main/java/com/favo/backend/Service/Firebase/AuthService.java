package com.favo.backend.Service.Firebase;

import com.favo.backend.Domain.user.FirebaseUserInfo;
import com.favo.backend.Domain.user.GeneralUser;
import com.favo.backend.Domain.user.ProfilePhoto;
import com.favo.backend.Domain.user.Repository.ProfilePhotoRepository;
import com.favo.backend.Domain.user.Repository.SystemUserRepository;
import com.favo.backend.Domain.user.Repository.UserTypeRepository;
import com.favo.backend.Domain.user.SystemUser;
import com.favo.backend.Domain.user.UserType;
import com.favo.backend.Security.SecurityRoles;
import com.favo.backend.Service.Email.EmailVerificationService;
import com.favo.backend.common.error.FavoException;
import com.favo.backend.common.error.UserErrorCode;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final SystemUserRepository systemUserRepository;
    private final UserTypeRepository userTypeRepository;
    private final FirebaseAuthService firebaseAuthService;
    private final ProfilePhotoRepository profilePhotoRepository;
    private final EmailVerificationService emailVerificationService;

    public AuthService(
            SystemUserRepository systemUserRepository,
            UserTypeRepository userTypeRepository,
            FirebaseAuthService firebaseAuthService,
            ProfilePhotoRepository profilePhotoRepository,
            EmailVerificationService emailVerificationService
    ) {
        this.systemUserRepository = systemUserRepository;
        this.userTypeRepository = userTypeRepository;
        this.firebaseAuthService = firebaseAuthService;
        this.profilePhotoRepository = profilePhotoRepository;
        this.emailVerificationService = emailVerificationService;
    }

    @Transactional(readOnly = true)
    public SystemUser login(@NonNull String firebaseIdToken) {
        FirebaseUserInfo info = firebaseAuthService.verify(firebaseIdToken);
        log.info("Firebase token verified. Looking up user with firebaseUid: {}", info.getUid());

        Optional<SystemUser> userOpt = systemUserRepository.findByFirebaseUidAndIsActiveTrue(info.getUid());

        if (userOpt.isEmpty()) {
            Optional<SystemUser> inactiveUser = systemUserRepository.findByFirebaseUid(info.getUid());
            if (inactiveUser.isPresent()) {
                log.warn("User found but is inactive. firebaseUid: {}, isActive: {}",
                        info.getUid(), inactiveUser.get().getIsActive());
                throw new RuntimeException("USER_INACTIVE");
            }
            log.warn("User not found in database. firebaseUid: {}", info.getUid());
            throw new RuntimeException("NO_SUCH_ACCOUNT");
        }

        SystemUser user = userOpt.get();
        if (Boolean.FALSE.equals(user.getEmailVerified())) {
            throw new RuntimeException("EMAIL_NOT_VERIFIED");
        }

        log.info("User found and active. userId: {}, firebaseUid: {}",
                user.getId(), user.getFirebaseUid());
        return user;
    }

    public SystemUser register(@NonNull String firebaseIdToken,
                               @NonNull String userName,
                               @NonNull String name,
                               @NonNull String surname,
                               @NonNull LocalDate birthdate,
                               byte[] profilePhotoData,
                               String profilePhotoMimeType) {

        FirebaseUserInfo info = firebaseAuthService.verify(firebaseIdToken);

        if (systemUserRepository.existsByFirebaseUid(info.getUid())) {
            throw new RuntimeException("USER_ALREADY_EXISTS");
        }

        if (userName.isBlank()) {
            throw new RuntimeException("USERNAME_REQUIRED");
        }

        if (systemUserRepository.existsByUserNameAndIsActiveTrue(userName)) {
            throw new FavoException(UserErrorCode.USERNAME_ALREADY_TAKEN);
        }

        SystemUser user = registerNewUser(info, userName, name, surname, birthdate);

        if (profilePhotoData != null && profilePhotoData.length > 0) {
            createProfilePhoto(user, profilePhotoData, profilePhotoMimeType);
        }

        try {
            var mail = emailVerificationService.issueVerificationEmail(user);
            if (!mail.sent()) {
                log.error(
                        "Kayıt sonrası doğrulama e-postası gönderilmedi userId={} email={} failureCode={} detail={}",
                        user.getId(), user.getEmail(), mail.failureCode(), mail.smtpDetail());
            }
        } catch (Exception e) {
            log.error("Could not complete verification email flow for user id={}", user.getId(), e);
        }

        return user;
    }

    private SystemUser registerNewUser(FirebaseUserInfo info, String userName, String name, String surname, LocalDate birthdate) {
        UserType userType = userTypeRepository
                .findByName(SecurityRoles.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("UserType " + SecurityRoles.ROLE_USER + " not found. Run application once to seed roles."));

        SystemUser user = new GeneralUser();
        user.setFirebaseUid(info.getUid());
        user.setEmail(info.getEmail());
        user.setUserName(userName);
        user.setName(name);
        user.setSurname(surname);
        user.setBirthdate(birthdate);
        user.setUserType(userType);
        user.setEmailVerified(false);
        user.setProfileAnonymous(false);

        return systemUserRepository.save(user);
    }

    @Transactional(readOnly = true)
    public boolean isUsernameAvailable(@NonNull String userName) {
        if (userName.isBlank()) {
            throw new FavoException(UserErrorCode.USERNAME_FORMAT_INVALID);
        }
        return !systemUserRepository.existsByUserNameAndIsActiveTrue(userName);
    }

    // For verify-email / resend: no EMAIL_NOT_VERIFIED check
    @Transactional(readOnly = true)
    public SystemUser loadActiveUserByFirebaseToken(@NonNull String firebaseIdToken) {
        FirebaseUserInfo info = firebaseAuthService.verify(firebaseIdToken);
        return systemUserRepository.findByFirebaseUidAndIsActiveTrue(info.getUid())
                .orElseThrow(() -> new RuntimeException("NO_SUCH_ACCOUNT"));
    }

    private void createProfilePhoto(SystemUser user, byte[] imageData, String mimeType) {
        ProfilePhoto photo = new ProfilePhoto();
        photo.setUser(user);
        photo.setImageData(imageData);
        photo.setMimeType(mimeType);
        photo.setUploadDate(LocalDateTime.now());
        photo.setIsActive(true);
        profilePhotoRepository.save(photo);
    }
}

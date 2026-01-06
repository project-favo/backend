package com.favo.backend.Service.Firebase;

import com.favo.backend.Domain.user.FirebaseUserInfo;
import com.favo.backend.Domain.user.GeneralUser;
import com.favo.backend.Domain.user.ProfilePhoto;
import com.favo.backend.Domain.user.Repository.ProfilePhotoRepository;
import com.favo.backend.Domain.user.Repository.SystemUserRepository;
import com.favo.backend.Domain.user.Repository.UserTypeRepository;
import com.favo.backend.Domain.user.SystemUser;
import com.favo.backend.Domain.user.UserType;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {
    private final SystemUserRepository systemUserRepository;
    private final UserTypeRepository userTypeRepository;
    private final FirebaseAuthService firebaseAuthService;
    private final ProfilePhotoRepository profilePhotoRepository;

    /**
     * 🔓 Login only
     * - Firebase token'ı verify eder
     * - DB'de karşılığı olan AKTİF kullanıcıyı döner (UserType ile birlikte)
     * - Kullanıcı yoksa veya pasifse hata fırlatır
     * 
     * @Transactional(readOnly = true) ile session'ı açık tutar ve UserType'ı eager load eder
     * FirebaseAuthenticationFilter içinde user.getUserType().getName() çağrıldığı için gerekli
     */
    @Transactional(readOnly = true)
    public SystemUser login(@NonNull String firebaseIdToken) {
        FirebaseUserInfo info = firebaseAuthService.verify(firebaseIdToken);
        log.info("Firebase token verified. Looking up user with firebaseUid: {}", info.getUid());

        // UserType fetch join ile yüklenir (SystemUserRepository'de tanımlı)
        var user = systemUserRepository.findByFirebaseUidAndIsActiveTrue(info.getUid());
        
        if (user.isEmpty()) {
            // Kullanıcı var mı ama pasif mi kontrol et
            var inactiveUser = systemUserRepository.findByFirebaseUid(info.getUid());
            if (inactiveUser.isPresent()) {
                log.warn("User found but is inactive. firebaseUid: {}, isActive: {}", 
                    info.getUid(), inactiveUser.get().getIsActive());
                throw new RuntimeException("USER_INACTIVE");
            } else {
                log.warn("User not found in database. firebaseUid: {}", info.getUid());
                throw new RuntimeException("NO_SUCH_ACCOUNT");
            }
        }
        
        log.info("User found and active. userId: {}, firebaseUid: {}", 
            user.get().getId(), user.get().getFirebaseUid());
        return user.get();
    }

    /**
     * 🆕 Register
     * - Firebase token'ı verify eder
     * - Kullanıcı zaten kayıtlıysa hata fırlatır
     * - UI'dan gelen username ile yeni user oluşturur
     * - Profile photo opsiyonel olarak eklenebilir
     */
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

        // Sadece aktif kullanıcılar arasında username kontrolü yap
        if (systemUserRepository.existsByUserNameAndIsActiveTrue(userName)) {
            throw new RuntimeException("USERNAME_ALREADY_TAKEN");
        }

        SystemUser user = registerNewUser(info, userName, name, surname, birthdate);
        
        // Profile photo varsa ekle
        if (profilePhotoData != null && profilePhotoData.length > 0) {
            createProfilePhoto(user, profilePhotoData, profilePhotoMimeType);
        }
        
        return user;
    }

    private SystemUser registerNewUser(FirebaseUserInfo info, String userName, String name, String surname, LocalDate birthdate) {
        // 2️⃣ Business role
        UserType userType = userTypeRepository
                .findByName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("DEF UserType not found"));

        // 3️⃣ Polymorphic creation
        SystemUser user = new GeneralUser();
        user.setFirebaseUid(info.getUid());
        user.setEmail(info.getEmail());
        user.setUserName(userName);
        user.setName(name);
        user.setSurname(surname);
        user.setBirthdate(birthdate);
        user.setUserType(userType);

        return systemUserRepository.save(user);
    }

    /**
     * Kullanıcı için profil fotoğrafı oluşturur
     */
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
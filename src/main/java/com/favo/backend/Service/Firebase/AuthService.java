package com.favo.backend.Service.Firebase;

import com.favo.backend.Domain.user.FirebaseUserInfo;
import com.favo.backend.Domain.user.GeneralUser;
import com.favo.backend.Domain.user.Repository.SystemUserRepository;
import com.favo.backend.Domain.user.Repository.UserTypeRepository;
import com.favo.backend.Domain.user.SystemUser;
import com.favo.backend.Domain.user.UserType;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {
    private final SystemUserRepository systemUserRepository;
    private final UserTypeRepository userTypeRepository;
    private final FirebaseAuthService firebaseAuthService;

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

        // UserType fetch join ile yüklenir (SystemUserRepository'de tanımlı)
        return systemUserRepository
                .findByFirebaseUidAndIsActiveTrue(info.getUid())
                .orElseThrow(() -> new RuntimeException("NO_SUCH_ACCOUNT"));
    }

    /**
     * 🆕 Register
     * - Firebase token'ı verify eder
     * - Kullanıcı zaten kayıtlıysa hata fırlatır
     * - UI'dan gelen username ile yeni user oluşturur
     */
    public SystemUser register(@NonNull String firebaseIdToken,
                               @NonNull String userName,
                               @NonNull String name,
                               @NonNull String surname,
                               @NonNull LocalDate birthdate) {

        FirebaseUserInfo info = firebaseAuthService.verify(firebaseIdToken);

        if (systemUserRepository.existsByFirebaseUid(info.getUid())) {
            throw new RuntimeException("USER_ALREADY_EXISTS");
        }

        if (userName.isBlank()) {
            throw new RuntimeException("USERNAME_REQUIRED");
        }

        if (systemUserRepository.existsByUserName(userName)) {
            throw new RuntimeException("USERNAME_ALREADY_TAKEN");
        }

        return registerNewUser(info, userName);
    }

    private SystemUser registerNewUser(FirebaseUserInfo info, String userName) {
        // 2️⃣ Business role
        UserType userType = userTypeRepository
                .findByName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("DEF UserType not found"));

        // 3️⃣ Polymorphic creation
        SystemUser user = new GeneralUser();
        user.setFirebaseUid(info.getUid());
        user.setEmail(info.getEmail());
        user.setUserName(userName);
        user.setUserType(userType);

        return systemUserRepository.save(user);
    }
}
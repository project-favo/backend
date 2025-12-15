package com.favo.backend.Service.Firebase;

import com.favo.backend.Domain.user.FirebaseUserInfo;
import com.favo.backend.Domain.user.GeneralUser;
import com.favo.backend.Domain.user.Repository.SystemUserRepository;
import com.favo.backend.Domain.user.Repository.UserTypeRepository;
import com.favo.backend.Domain.user.SystemUser;
import com.favo.backend.Domain.user.UserType;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {
    private final SystemUserRepository systemUserRepository;
    private final UserTypeRepository userTypeRepository;
    private final FirebaseAuthService firebaseAuthService; // arkadaşın yazacak



    public SystemUser loginOrRegister(String firebaseIdToken) {

        FirebaseUserInfo info = firebaseAuthService.verify(firebaseIdToken);

        // 1️⃣ DB'de user var mı?
        return systemUserRepository
                .findByFirebaseUid(info.getUid())
                .map(this::validateActive)
                .orElseGet(() -> registerNewUser(info));
    }

    private SystemUser registerNewUser(FirebaseUserInfo info) {

        // 2️⃣ Business role
        UserType userType = userTypeRepository
                .findByName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("Default UserType not found"));

        // 3️⃣ Polymorphic creation
        SystemUser user = new GeneralUser();
        user.setFirebaseUid(info.getUid());
        user.setEmail(info.getEmail());

        String username = info.getDisplayName();
        if (username == null || username.isBlank()) {
            if (info.getEmail() != null && info.getEmail().contains("@")) {
                username = info.getEmail().substring(0, info.getEmail().indexOf('@'));
            } else {
                username = "user_" + info.getUid().substring(0, Math.min(8, info.getUid().length()));
            }
        }

        user.setUserName(username);
        user.setUserType(userType);

        return systemUserRepository.save(user);
    }

    private SystemUser validateActive(SystemUser user) {
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new RuntimeException("User is deactivated");
        }
        return user;
    }
}
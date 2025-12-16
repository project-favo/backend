package com.favo.backend.Service.User;

import com.favo.backend.Domain.user.Repository.SystemUserRepository;
import com.favo.backend.Domain.user.SystemUser;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final SystemUserRepository systemUserRepository;

    public SystemUser updateUserName(SystemUser user, String newUserName) {

        if (newUserName == null || newUserName.isBlank()) {
            throw new RuntimeException("USERNAME_REQUIRED");
        }

        if (!newUserName.equals(user.getUserName())
                && systemUserRepository.existsByUserName(newUserName)) {
            throw new RuntimeException("USERNAME_ALREADY_TAKEN");
        }

        user.setUserName(newUserName);
        return systemUserRepository.save(user);
    }

    public void deactivateUser(Long userId) {

        SystemUser user = systemUserRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 🔥 POLYMORPHIC CALL
        user.deactivate();

        systemUserRepository.save(user);
    }

    public SystemUser getById(Long userId) {
        return systemUserRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}

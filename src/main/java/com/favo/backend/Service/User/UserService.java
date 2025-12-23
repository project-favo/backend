package com.favo.backend.Service.User;

import com.favo.backend.Domain.user.Repository.SystemUserRepository;
import com.favo.backend.Domain.user.SystemUser;
import com.favo.backend.Domain.user.UserUpdateRequestDto;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final SystemUserRepository systemUserRepository;

    /**
     * Kullanıcı profil bilgilerini günceller.
     * - userName: Unique olmalı, boş olamaz
     * - name, surname: Boş olabilir (opsiyonel)
     * - birthdate: Geçmiş bir tarih olmalı, null olabilir
     */
    public SystemUser updateUserProfile(SystemUser user, UserUpdateRequestDto request) {

        // Username güncelleme
        if (request.getUserName() != null) {
            String newUserName = request.getUserName().trim();
            if (newUserName.isBlank()) {
                throw new RuntimeException("USERNAME_REQUIRED");
            }

            if (!newUserName.equals(user.getUserName())
                    && systemUserRepository.existsByUserName(newUserName)) {
                throw new RuntimeException("USERNAME_ALREADY_TAKEN");
            }

            user.setUserName(newUserName);
        }

        // Name güncelleme
        if (request.getName() != null) {
            user.setName(request.getName().trim().isEmpty() ? null : request.getName().trim());
        }

        // Surname güncelleme
        if (request.getSurname() != null) {
            user.setSurname(request.getSurname().trim().isEmpty() ? null : request.getSurname().trim());
        }

        // Birthdate güncelleme
        if (request.getBirthdate() != null) {
            LocalDate birthdate = request.getBirthdate();
            LocalDate today = LocalDate.now();
            
            // Doğum tarihi bugünden ileri olamaz
            if (birthdate.isAfter(today)) {
                throw new RuntimeException("BIRTHDATE_CANNOT_BE_FUTURE");
            }
            
            user.setBirthdate(birthdate);
        }

        return systemUserRepository.save(user);
    }

    /**
     * @deprecated Bu metod yerine updateUserProfile kullanılmalı.
     * Geriye dönük uyumluluk için bırakıldı.
     */
    @Deprecated
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

package com.favo.backend.Service.User;

import com.favo.backend.Domain.user.ProfilePhoto;
import com.favo.backend.Domain.user.Repository.ProfilePhotoRepository;
import com.favo.backend.Domain.user.Repository.SystemUserRepository;
import com.favo.backend.Domain.user.SystemUser;
import com.favo.backend.Domain.user.UserUpdateRequestDto;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final SystemUserRepository systemUserRepository;
    private final ProfilePhotoRepository profilePhotoRepository;

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

            // Sadece aktif kullanıcılar arasında username kontrolü yap
            if (!newUserName.equals(user.getUserName())
                    && systemUserRepository.existsByUserNameAndIsActiveTrue(newUserName)) {
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

        if (request.getProfileAnonymous() != null) {
            user.setProfileAnonymous(request.getProfileAnonymous());
        }

        // Profile photo güncelleme
        if (request.getProfilePhotoData() != null && request.getProfilePhotoData().length > 0) {
            String mimeType = request.getProfilePhotoMimeType();
            if (mimeType == null || mimeType.isEmpty()) {
                mimeType = "image/jpeg"; // Default mime type
            }
            updateProfilePhoto(user, request.getProfilePhotoData(), mimeType);
        }

        SystemUser saved = systemUserRepository.save(user);
        
        // Transaction'ı flush et (fotoğrafın kaydedildiğinden emin ol)
        systemUserRepository.flush();
        
        return saved;
    }

    /**
     * Kullanıcının profil fotoğrafını günceller
     * Eski aktif fotoğrafı soft delete eder, yeni fotoğrafı aktif yapar
     */
    private void updateProfilePhoto(SystemUser user, byte[] imageData, String mimeType) {
        // Eski aktif fotoğrafı bul ve soft delete yap
        profilePhotoRepository.findActiveByUserId(user.getId())
                .ifPresent(oldPhoto -> {
                    oldPhoto.setIsActive(false);
                    profilePhotoRepository.save(oldPhoto);
                });

        // Yeni profil fotoğrafı oluştur
        ProfilePhoto newPhoto = new ProfilePhoto();
        newPhoto.setUser(user);
        newPhoto.setImageData(imageData);
        newPhoto.setMimeType(mimeType != null ? mimeType : "image/jpeg");
        newPhoto.setUploadDate(LocalDateTime.now());
        newPhoto.setIsActive(true);
        profilePhotoRepository.save(newPhoto);
        
        // Transaction'ı flush et (fotoğrafın kaydedildiğinden emin ol)
        profilePhotoRepository.flush();
    }

    /**
     * Kullanıcının aktif profil fotoğrafını getirir
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ProfilePhoto getActiveProfilePhoto(Long userId) {
        return profilePhotoRepository.findActiveByUserId(userId).orElse(null);
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

        // Sadece aktif kullanıcılar arasında username kontrolü yap
        if (!newUserName.equals(user.getUserName())
                && systemUserRepository.existsByUserNameAndIsActiveTrue(newUserName)) {
            throw new RuntimeException("USERNAME_ALREADY_TAKEN");
        }

        user.setUserName(newUserName);
        return systemUserRepository.save(user);
    }

    public void deactivateUser(Long userId) {
        SystemUser user = systemUserRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.deactivate();
        systemUserRepository.save(user);
    }

    /** Admin: Kullanıcıyı tekrar aktif eder */
    public void activateUser(Long userId) {
        SystemUser user = systemUserRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setIsActive(true);
        systemUserRepository.save(user);
    }

    public SystemUser getById(Long userId) {
        return systemUserRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<SystemUser> findByIdIfPresent(Long userId) {
        return systemUserRepository.findById(userId);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public SystemUser getActiveUserWithRelationsById(Long userId) {
        return systemUserRepository.findByIdWithUserType(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    /**
     * Mevcut kullanıcıyı UserType ile birlikte getirir.
     * Transaction içinde çalışır, böylece lazy loading sorunları olmaz.
     * 
     * @param user SecurityContext'ten gelen user (sadece ID için kullanılır)
     * @return UserType ile birlikte yüklenmiş SystemUser
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public SystemUser getCurrentUserWithRelations(SystemUser user) {
        return systemUserRepository.findByIdWithUserType(user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    /**
     * Kullanıcı adına göre aktif kullanıcılarda arama yapar.
     * Büyük/küçük harf duyarsız, substring eşleşmesi. Starts-with sonuçlar önce gelir.
     *
     * @param query  Aranacak kullanıcı adı (veya bir kısmı)
     * @param size   Maksimum sonuç sayısı (1–50 arası)
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<SystemUser> searchByUserName(String query, int size) {
        if (query == null || query.isBlank()) return List.of();
        int safeSize = Math.max(1, Math.min(size, 50));
        return systemUserRepository.searchActiveByUserName(query.trim(), PageRequest.of(0, safeSize)).getContent();
    }

    /**
     * Mevcut kullanıcının hesabını deaktive eder.
     * Kullanıcı kendi hesabını silebilir (soft delete - isActive = false).
     * 
     * @param user SecurityContext'ten gelen user
     */
    public void deactivateCurrentUser(SystemUser user) {
        // User'ı database'den yeniden yükle (güncel state için)
        SystemUser currentUser = systemUserRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // 🔥 POLYMORPHIC CALL
        currentUser.deactivate();
        
        systemUserRepository.save(currentUser);
    }
}

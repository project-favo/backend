package com.favo.backend.controller;

import com.favo.backend.Domain.user.*;
import com.favo.backend.Security.SecurityRoles;
import com.favo.backend.Service.Firebase.AuthService;
import com.favo.backend.Service.User.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final UserMapper userMapper;

    /**
     * 🔓 Genel Firebase Login (User + Admin)
     * Authorization: Bearer <firebase-id-token>
     * Sadece daha önce register olmuş kullanıcılar için
     */
    @PostMapping("/login")
    public ResponseEntity<UserResponseDto> login(
            @RequestHeader("Authorization") String authorization
    ) {
        String token = authorization.replace("Bearer ", "").trim();
        SystemUser user = authService.login(token);
        return ResponseEntity.ok(userMapper.toDto(user));
    }

    /**
     * 🔐 Admin Login
     * Sadece ROLE_ADMIN userType'ına sahip kullanıcılar başarılı döner.
     * Normal kullanıcılar için 403 FORBIDDEN.
     */
    @PostMapping("/login/admin")
    public ResponseEntity<UserResponseDto> adminLogin(
            @RequestHeader("Authorization") String authorization
    ) {
        String token = authorization.replace("Bearer ", "").trim();
        SystemUser user = authService.login(token);

        String roleName = user.getUserType() != null ? user.getUserType().getName() : null;
        if (!SecurityRoles.ROLE_ADMIN.equals(roleName)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(userMapper.toDto(user));
    }

    /**
     * 🆕 Firebase Register (JSON Body)
     * Authorization: Bearer <firebase-id-token>
     * Content-Type: application/json
     * 
     * Body: { 
     *   "userName": "...", 
     *   "name": "...", 
     *   "surname": "...", 
     *   "birthdate": "YYYY-MM-DD",
     *   "profilePhotoBase64": "data:image/jpeg;base64,/9j/4AAQ...",  // Opsiyonel (Base64 string)
     *   "profilePhotoMimeType": "image/jpeg"                          // Opsiyonel
     * }
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponseDto> register(
            @RequestHeader("Authorization") String authorization,
            @RequestBody RegisterRequestDto request
    ) {
        String token = authorization.replace("Bearer ", "").trim();
        
        // Base64 string'i byte[]'e çevir
        byte[] photoData = convertToByteArray(request.getProfilePhotoBase64(), request.getProfilePhotoData());
        
        SystemUser user = authService.register(
                token,
                request.getUserName(),
                request.getName(),
                request.getSurname(),
                request.getBirthdate(),
                photoData,
                request.getProfilePhotoMimeType()
        );
        return ResponseEntity.ok(userMapper.toDto(user));
    }

    /**
     * 🆕 Firebase Register (Multipart - Postman için kolay)
     * Authorization: Bearer <firebase-id-token>
     * Content-Type: multipart/form-data
     * 
     * Form Data:
     *   - userName: "johndoe"
     *   - name: "John"
     *   - surname: "Doe"
     *   - birthdate: "1990-01-01"
     *   - profilePhoto: [FILE] (opsiyonel)
     */
    @PostMapping(value = "/register/multipart", consumes = "multipart/form-data")
    public ResponseEntity<UserResponseDto> registerMultipart(
            @RequestHeader("Authorization") String authorization,
            @RequestParam String userName,
            @RequestParam String name,
            @RequestParam String surname,
            @RequestParam String birthdate,
            @RequestParam(required = false) MultipartFile profilePhoto
    ) {
        String token = authorization.replace("Bearer ", "").trim();
        
        byte[] photoData = null;
        String photoMimeType = null;
        
        if (profilePhoto != null && !profilePhoto.isEmpty()) {
            try {
                photoData = profilePhoto.getBytes();
                photoMimeType = profilePhoto.getContentType();
            } catch (Exception e) {
                throw new RuntimeException("Failed to read profile photo: " + e.getMessage());
            }
        }
        
        SystemUser user = authService.register(
                token,
                userName,
                name,
                surname,
                java.time.LocalDate.parse(birthdate),
                photoData,
                photoMimeType
        );
        return ResponseEntity.ok(userMapper.toDto(user));
    }


    /**
     * 🔐 Me endpoint
     * SecurityContext içinden user gelir
     * Token burada tekrar parse edilmez
     * User'ı database'den yeniden çeker (UserType ile birlikte) - lazy loading sorunlarını önlemek için
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> me(
            @AuthenticationPrincipal SystemUser user
    ) {
        // User'ı database'den yeniden çek (UserType ile birlikte)
        // Bu, transaction içinde çalışır ve lazy loading sorunlarını önler
        SystemUser userWithRelations = userService.getCurrentUserWithRelations(user);
        return ResponseEntity.ok(userMapper.toDto(userWithRelations));
    }

    /**
     * ✏️ Me update endpoint (JSON Body)
     * - Sadece authenticated user kendi profilini günceller
     * - Güncellenebilir alanlar: userName, name, surname, birthdate, profilePhoto
     * - Tüm alanlar opsiyonel (sadece gönderilen alanlar güncellenir)
     * - userName unique olmalı ve boş olamaz
     * - birthdate geçmiş bir tarih olmalı
     * 
     * Body: {
     *   "userName": "...",  // Opsiyonel
     *   "name": "...",      // Opsiyonel
     *   "surname": "...",   // Opsiyonel
     *   "birthdate": "YYYY-MM-DD",  // Opsiyonel
     *   "profilePhotoBase64": "data:image/jpeg;base64,/9j/4AAQ...",  // Opsiyonel
     *   "profilePhotoMimeType": "image/jpeg"  // Opsiyonel
     * }
     */
    @PutMapping("/me")
    public ResponseEntity<UserResponseDto> updateMe(
            @AuthenticationPrincipal SystemUser user,
            @RequestBody UserUpdateRequestDto request
    ) {
        // Base64 string'i byte[]'e çevir
        byte[] photoData = convertToByteArray(request.getProfilePhotoBase64(), request.getProfilePhotoData());
        request.setProfilePhotoData(photoData);
        
        SystemUser updated = userService.updateUserProfile(user, request);
        
        // User'ı yeniden yükle (transaction commit edildikten sonra fotoğrafı çekmek için)
        SystemUser userWithRelations = userService.getCurrentUserWithRelations(updated);
        return ResponseEntity.ok(userMapper.toDto(userWithRelations));
    }

    /**
     * ✏️ Me update endpoint (Multipart - Postman için kolay)
     * Content-Type: multipart/form-data
     * 
     * Form Data:
     *   - userName: "johndoe" (opsiyonel)
     *   - name: "John" (opsiyonel)
     *   - surname: "Doe" (opsiyonel)
     *   - birthdate: "1990-01-01" (opsiyonel)
     *   - profilePhoto: [FILE] (opsiyonel) - ÖNEMLİ: Key adı "profilePhoto" olmalı, "profilePhotoBase64" değil!
     */
    @PutMapping(value = "/me/multipart", consumes = "multipart/form-data")
    public ResponseEntity<UserResponseDto> updateMeMultipart(
            @AuthenticationPrincipal SystemUser user,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String surname,
            @RequestParam(required = false) String birthdate,
            @RequestParam(required = false) MultipartFile profilePhoto
    ) {
        UserUpdateRequestDto request = new UserUpdateRequestDto();
        request.setUserName(userName);
        request.setName(name);
        request.setSurname(surname);
        
        if (birthdate != null && !birthdate.isEmpty()) {
            request.setBirthdate(java.time.LocalDate.parse(birthdate));
        }
        
        if (profilePhoto != null && !profilePhoto.isEmpty()) {
            try {
                request.setProfilePhotoData(profilePhoto.getBytes());
                request.setProfilePhotoMimeType(profilePhoto.getContentType());
            } catch (Exception e) {
                throw new RuntimeException("Failed to read profile photo: " + e.getMessage());
            }
        }
        
        SystemUser updated = userService.updateUserProfile(user, request);
        
        // User'ı yeniden yükle (transaction commit edildikten sonra fotoğrafı çekmek için)
        SystemUser userWithRelations = userService.getCurrentUserWithRelations(updated);
        return ResponseEntity.ok(userMapper.toDto(userWithRelations));
    }

    /**
     * Base64 string'i byte[]'e çevirir
     * Eğer Base64 string varsa onu kullanır, yoksa binary data'yı kullanır
     */
    private byte[] convertToByteArray(String base64String, byte[] binaryData) {
        if (base64String != null && !base64String.isEmpty()) {
            try {
                // "data:image/jpeg;base64," prefix'ini temizle
                String base64 = base64String;
                if (base64.contains(",")) {
                    base64 = base64.substring(base64.indexOf(",") + 1);
                }
                return Base64.getDecoder().decode(base64);
            } catch (Exception e) {
                throw new RuntimeException("Invalid Base64 string: " + e.getMessage());
            }
        }
        return binaryData;
    }


    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(
            @AuthenticationPrincipal SystemUser user
    ) {
        userService.deactivateCurrentUser(user);
        return ResponseEntity.noContent().build();
    }
}

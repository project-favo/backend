package com.favo.backend.Domain.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class UserResponseDto {

    private Long id;
    private String email;
    private String userName;
    private String name;
    private String surname;
    private LocalDate birthdate;
    private String userType;
    private boolean active;

    /** false = kayıt sonrası e-posta doğrulaması bekleniyor; null legacy kullanıcılar doğrulanmış sayılır */
    private boolean emailVerified;

    // Profile photo (nullable - eğer profil fotoğrafı yoksa null)
    private byte[] profilePhotoData; // Binary image data
    private String profilePhotoMimeType; // Örn: "image/jpeg", "image/png"
}

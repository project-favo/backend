package com.favo.backend.Domain.user;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequestDto {
    private String userName;
    private String name;
    private String surname;
    private LocalDate birthdate;
    
    // Profile photo (nullable - opsiyonel)
    // İki seçenek: Base64 string (profilePhotoBase64) veya binary array (profilePhotoData)
    private String profilePhotoBase64; // Base64 encoded image string (Postman için kolay)
    private byte[] profilePhotoData; // Binary image data (alternatif)
    private String profilePhotoMimeType; // Örn: "image/jpeg", "image/png"
}

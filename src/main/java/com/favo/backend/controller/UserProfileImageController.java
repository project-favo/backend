package com.favo.backend.controller;

import com.favo.backend.Domain.user.ProfilePhoto;
import com.favo.backend.Service.User.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kullanıcı profil fotoğrafını public URL ile sunar (liste / yorum avatarları için).
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserProfileImageController {

    private final UserService userService;

    @GetMapping("/{userId}/profile-image")
    public ResponseEntity<byte[]> getProfileImage(@PathVariable Long userId) {
        var user = userService.getById(userId);
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            return ResponseEntity.notFound().build();
        }
        ProfilePhoto photo = userService.getActiveProfilePhoto(userId);
        if (photo == null || photo.getImageData() == null || photo.getImageData().length == 0) {
            return ResponseEntity.notFound().build();
        }
        String mimeType = photo.getMimeType();
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = MediaType.IMAGE_JPEG_VALUE;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mimeType));
        headers.setContentLength(photo.getImageData().length);
        headers.setCacheControl("public, max-age=3600");
        return new ResponseEntity<>(photo.getImageData(), headers, HttpStatus.OK);
    }
}

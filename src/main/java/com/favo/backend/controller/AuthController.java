package com.favo.backend.controller;

import com.favo.backend.Domain.user.*;
import com.favo.backend.Service.Firebase.AuthService;
import com.favo.backend.Service.User.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    /**
     * 🔓 Firebase Login
     * Authorization: Bearer <firebase-id-token>
     * Sadece daha önce register olmuş kullanıcılar için
     */
    @PostMapping("/login")
    public ResponseEntity<UserResponseDto> login(
            @RequestHeader("Authorization") String authorization
    ) {
        String token = authorization.replace("Bearer ", "").trim();
        SystemUser user = authService.login(token);
        return ResponseEntity.ok(UserMapper.toDto(user));
    }

    /**
     * 🆕 Firebase Register
     * Authorization: Bearer <firebase-id-token>
     * Body: { "userName": "...", "name": "...", "surname": "...", "birthdate": "YYYY-MM-DD" }
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponseDto> register(
            @RequestHeader("Authorization") String authorization,
            @RequestBody RegisterRequestDto request
    ) {
        String token = authorization.replace("Bearer ", "").trim();
        SystemUser user = authService.register(
                token,
                request.getUserName(),
                request.getName(),
                request.getSurname(),
                request.getBirthdate()
        );
        return ResponseEntity.ok(UserMapper.toDto(user));
    }


    /**
     * 🔐 Me endpoint
     * SecurityContext içinden user gelir
     * Token burada tekrar parse edilmez
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> me(
            @AuthenticationPrincipal SystemUser user
    ) {
        return ResponseEntity.ok(UserMapper.toDto(user));
    }

    /**
     * ✏️ Me update endpoint
     * - Sadece authenticated user kendi profilini günceller
     * - Güncellenebilir alanlar: userName, name, surname, birthdate
     * - Tüm alanlar opsiyonel (sadece gönderilen alanlar güncellenir)
     * - userName unique olmalı ve boş olamaz
     * - birthdate geçmiş bir tarih olmalı
     */
    @PutMapping("/me")
    public ResponseEntity<UserResponseDto> updateMe(
            @AuthenticationPrincipal SystemUser user,
            @RequestBody UserUpdateRequestDto request
    ) {
        SystemUser updated = userService.updateUserProfile(user, request);
        return ResponseEntity.ok(UserMapper.toDto(updated));
    }
}

package com.favo.backend.controller;

import com.favo.backend.Domain.user.SystemUser;
import com.favo.backend.Domain.user.UserMapper;
import com.favo.backend.Domain.user.UserResponseDto;
import com.favo.backend.Service.Firebase.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 🔓 Firebase Login / Register
     * Authorization: Bearer <firebase-id-token>
     * Bu endpoint SADECE ilk giriş içindir
     */
    @PostMapping("/login")
    public ResponseEntity<UserResponseDto> login(
            @RequestHeader("Authorization") String authorization
    ) {
        String token = authorization.replace("Bearer ", "").trim();
        SystemUser user = authService.loginOrRegister(token);
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
}

package com.favo.backend.controller;

import com.favo.backend.Domain.user.SystemUser;
import com.favo.backend.Domain.user.UserMapper;
import com.favo.backend.Domain.user.UserResponseDto;
import com.favo.backend.Service.Firebase.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Firebase login
     * Authorization: Bearer <firebase-id-token>
     */

    @PostMapping("/login")
    public ResponseEntity<UserResponseDto> login(
            @RequestHeader("Authorization") String authorizationHeader
    ){

        String token = extractToken(authorizationHeader);

        SystemUser user = authService.loginOrRegister(token);
        return ResponseEntity.ok(UserMapper.toDto(user));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> me(
            @RequestHeader("Authorization") String authorizationHeader
    ) {

        String token = extractToken(authorizationHeader);

        SystemUser user = authService.loginOrRegister(token);

        return ResponseEntity.ok(UserMapper.toDto(user));
    }

    private String extractToken(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new RuntimeException("Invalid Authorization header");
        }
        return header.substring(7);
    }

}

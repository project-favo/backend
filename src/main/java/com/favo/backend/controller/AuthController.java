package com.favo.backend.controller;

import com.favo.backend.Domain.user.*;
import com.favo.backend.Security.BearerTokenParser;
import com.favo.backend.Security.SecurityRoles;
import com.favo.backend.Service.Email.EmailVerificationService;
import com.favo.backend.Service.Firebase.AuthService;
import com.favo.backend.Service.User.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final UserMapper userMapper;
    private final EmailVerificationService emailVerificationService;

    public AuthController(
            AuthService authService,
            UserService userService,
            UserMapper userMapper,
            EmailVerificationService emailVerificationService
    ) {
        this.authService = authService;
        this.userService = userService;
        this.userMapper = userMapper;
        this.emailVerificationService = emailVerificationService;
    }

    // POST /login — Bearer Firebase token; unverified email -> EMAIL_NOT_VERIFIED
    @PostMapping("/login")
    public ResponseEntity<UserResponseDto> login(
            @RequestHeader("Authorization") String authorization
    ) {
        String token = BearerTokenParser.extractToken(authorization);
        SystemUser user = authService.login(token);
        return ResponseEntity.ok(userMapper.toDto(user));
    }

    // POST /login/admin — ROLE_ADMIN only
    @PostMapping("/login/admin")
    public ResponseEntity<UserResponseDto> adminLogin(
            @RequestHeader("Authorization") String authorization
    ) {
        String token = BearerTokenParser.extractToken(authorization);
        SystemUser user = authService.login(token);

        String roleName = user.getUserType() != null ? user.getUserType().getName() : null;
        if (!SecurityRoles.ROLE_ADMIN.equals(roleName)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(userMapper.toDto(user));
    }

    // POST /register — JSON RegisterRequestDto
    @PostMapping("/register")
    public ResponseEntity<UserResponseDto> register(
            @RequestHeader("Authorization") String authorization,
            @RequestBody RegisterRequestDto request
    ) {
        String token = BearerTokenParser.extractToken(authorization);

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

    // POST /verify-email — Bearer + five-digit code JSON
    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody VerifyEmailRequestDto body
    ) {
        try {
            String token = BearerTokenParser.extractToken(authorization);
            SystemUser user = authService.loadActiveUserByFirebaseToken(token);
            SystemUser verified = emailVerificationService.verifyCode(user, body.getCode());
            return ResponseEntity.ok(userMapper.toDto(verified));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    // POST /resend-verification — Bearer only
    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(
            @RequestHeader("Authorization") String authorization
    ) {
        try {
            String token = BearerTokenParser.extractToken(authorization);
            SystemUser user = authService.loadActiveUserByFirebaseToken(token);
            var mail = emailVerificationService.resendVerificationEmail(user);
            if (!mail.sent()) {
                Map<String, String> body = new LinkedHashMap<>();
                body.put("error", "VERIFICATION_EMAIL_NOT_SENT");
                body.put("code", mail.failureCode() != null ? mail.failureCode() : "UNKNOWN");
                if (mail.smtpDetail() != null && !mail.smtpDetail().isBlank()) {
                    body.put("smtpDetail", mail.smtpDetail());
                }
                body.put("message", "E-posta gönderilemedi. Railway Hobby: SMTP egress bloklu olabilir — RESEND_API_KEY + RESEND_FROM (Resend HTTPS) kullanın. SMTP için: MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM. Deploy log: 'E-posta (doğrulama)'.");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
            }
            return ResponseEntity.accepted().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping(value = "/register/multipart", consumes = "multipart/form-data")
    public ResponseEntity<UserResponseDto> registerMultipart(
            @RequestHeader("Authorization") String authorization,
            @RequestParam String userName,
            @RequestParam String name,
            @RequestParam String surname,
            @RequestParam String birthdate,
            @RequestParam(required = false) MultipartFile profilePhoto
    ) {
        String token = BearerTokenParser.extractToken(authorization);

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

    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> me(
            @AuthenticationPrincipal SystemUser user
    ) {
        SystemUser userWithRelations = userService.getCurrentUserWithRelations(user);
        return ResponseEntity.ok(userMapper.toDto(userWithRelations));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponseDto> updateMe(
            @AuthenticationPrincipal SystemUser user,
            @RequestBody UserUpdateRequestDto request
    ) {
        byte[] photoData = convertToByteArray(request.getProfilePhotoBase64(), request.getProfilePhotoData());
        request.setProfilePhotoData(photoData);

        SystemUser updated = userService.updateUserProfile(user, request);

        SystemUser userWithRelations = userService.getCurrentUserWithRelations(updated);
        return ResponseEntity.ok(userMapper.toDto(userWithRelations));
    }

    @PutMapping(value = "/me/multipart", consumes = "multipart/form-data")
    public ResponseEntity<UserResponseDto> updateMeMultipart(
            @AuthenticationPrincipal SystemUser user,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String surname,
            @RequestParam(required = false) String birthdate,
            @RequestParam(required = false) Boolean profileAnonymous,
            @RequestParam(required = false) MultipartFile profilePhoto
    ) {
        UserUpdateRequestDto request = new UserUpdateRequestDto();
        request.setUserName(userName);
        request.setName(name);
        request.setSurname(surname);
        request.setProfileAnonymous(profileAnonymous);

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

        SystemUser userWithRelations = userService.getCurrentUserWithRelations(updated);
        return ResponseEntity.ok(userMapper.toDto(userWithRelations));
    }

    private byte[] convertToByteArray(String base64String, byte[] binaryData) {
        if (base64String != null && !base64String.isEmpty()) {
            try {
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

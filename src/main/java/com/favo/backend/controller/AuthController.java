package com.favo.backend.controller;

import com.favo.backend.Domain.user.*;
import com.favo.backend.Security.BearerTokenParser;
import com.favo.backend.Security.SecurityRoles;
import com.favo.backend.Service.Email.EmailVerificationService;
import com.favo.backend.Service.Email.PasswordResetService;
import com.favo.backend.Service.Email.PreRegistrationService;
import com.favo.backend.Service.Firebase.AuthService;
import com.favo.backend.Service.User.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.data.domain.Page;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final UserMapper userMapper;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final PreRegistrationService preRegistrationService;

    public AuthController(
            AuthService authService,
            UserService userService,
            UserMapper userMapper,
            EmailVerificationService emailVerificationService,
            PasswordResetService passwordResetService,
            PreRegistrationService preRegistrationService
    ) {
        this.authService = authService;
        this.userService = userService;
        this.userMapper = userMapper;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
        this.preRegistrationService = preRegistrationService;
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

    // GET /check-username?userName=xxx — public; kullanıcı adı müsait mi kontrol eder
    @GetMapping("/check-username")
    public ResponseEntity<Map<String, Object>> checkUsername(@RequestParam String userName) {
        boolean available = authService.isUsernameAvailable(userName.trim());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("available", available);
        body.put("userName", userName.trim());
        if (available) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
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

    /**
     * Şifre sıfırlama: Firebase OOB bağlantısı e-posta ile gönderilir. Bearer gerekmez.
     * Hesap yoksa da aynı 202 (e-posta sızdırmama).
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody PasswordResetRequestDto body) {
        passwordResetService.requestPasswordReset(body.getEmail());
        return ResponseEntity.accepted().body(Map.of(
                "message",
                "Bu e-posta adresiyle kayıtlı bir hesap varsa, şifre sıfırlama bağlantısı gönderildi."
        ));
    }

    /**
     * Kayıt öncesi e-posta doğrulama kodu gönder — public, kullanıcı kaydı yok.
     * POST /api/auth/pre-register/send-code   body: { "email": "..." }
     */
    @PostMapping("/pre-register/send-code")
    public ResponseEntity<?> sendPreRegistrationCode(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        try {
            preRegistrationService.sendCode(email);
            return ResponseEntity.accepted().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "UNKNOWN_ERROR";
            if (msg.contains("EMAIL_ALREADY_REGISTERED")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", msg));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", msg));
        }
    }

    /**
     * Kayıt öncesi kodu doğrula — public.
     * POST /api/auth/pre-register/verify-code   body: { "email": "...", "code": "12345" }
     */
    @PostMapping("/pre-register/verify-code")
    public ResponseEntity<?> verifyPreRegistrationCode(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String code = body.get("code");
        try {
            preRegistrationService.verifyCode(email, code);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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

    @GetMapping("/user/{id}")
    public ResponseEntity<UserResponseDto> getUserById(
            @PathVariable Long id,
            @AuthenticationPrincipal SystemUser user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        SystemUser target = userService.getActiveUserWithRelationsById(id);
        return ResponseEntity.ok(userMapper.toDtoForList(target));
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

    /**
     * Aktif kullanıcı dizini — mobil preload için.
     * GET /api/auth/users?page=0&size=100
     */
    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getUserDirectory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @AuthenticationPrincipal SystemUser user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Page<SystemUser> resultPage = userService.findActiveNonAdminUsers(page, size);
        List<UserResponseDto> content = resultPage.getContent()
                .stream()
                .map(userMapper::toDtoForList)
                .collect(Collectors.toList());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", content);
        body.put("totalElements", resultPage.getTotalElements());
        body.put("totalPages", resultPage.getTotalPages());
        body.put("number", resultPage.getNumber());
        body.put("last", resultPage.isLast());
        return ResponseEntity.ok(body);
    }

    /**
     * Kullanıcı adına göre aktif kullanıcılarda arama. Giriş yapılmış olması gerekir.
     * GET /api/auth/users/search?q=ali&size=20
     */
    @GetMapping("/users/search")
    public ResponseEntity<List<UserResponseDto>> searchUsers(
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal SystemUser user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (q == null || q.isBlank()) {
            return ResponseEntity.ok(List.of());
        }
        List<UserResponseDto> results = userService.searchByUserName(q, size)
                .stream()
                .map(userMapper::toDtoForList)
                .collect(Collectors.toList());
        return ResponseEntity.ok(results);
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(
            @AuthenticationPrincipal SystemUser user
    ) {
        userService.deactivateCurrentUser(user);
        return ResponseEntity.noContent().build();
    }
}

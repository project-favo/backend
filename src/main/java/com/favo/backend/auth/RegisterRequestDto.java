package com.favo.backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequestDto {

    @NotBlank
    @Size(min = 3, max = 30)
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$")
    private String username;

    @NotBlank
    @Size(max = 60)
    private String displayName;

    @NotBlank
    private String firebaseUid;
}

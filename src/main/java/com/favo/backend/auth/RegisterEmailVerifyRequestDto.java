package com.favo.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterEmailVerifyRequestDto {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 6, max = 6)
    private String code;
}

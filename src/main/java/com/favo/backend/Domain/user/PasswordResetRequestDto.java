package com.favo.backend.Domain.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordResetRequestDto {

    @NotBlank
    @Email
    private String email;
}

package com.favo.backend.Domain.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyEmailRequestDto {

    @NotBlank
    @Pattern(regexp = "\\d{5}", message = "Code must be exactly 5 digits")
    private String code;
}

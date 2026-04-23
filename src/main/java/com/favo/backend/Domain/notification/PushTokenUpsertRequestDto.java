package com.favo.backend.Domain.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PushTokenUpsertRequestDto {

    @NotBlank
    @Size(max = 512)
    private String token;

    @Size(max = 32)
    private String platform;
}

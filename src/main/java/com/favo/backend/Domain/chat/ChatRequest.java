package com.favo.backend.Domain.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatRequest {

    @NotBlank(message = "message must not be blank")
    @Size(max = 8000)
    private String message;
}

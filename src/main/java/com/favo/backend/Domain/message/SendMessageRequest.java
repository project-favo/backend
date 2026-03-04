package com.favo.backend.Domain.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendMessageRequest {

    private Long recipientId;
    private Long conversationId;

    @NotBlank
    @Size(max = 1000)
    private String content;
}


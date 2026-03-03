package com.favo.backend.Domain.message;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ConversationDto {

    private Long id;
    private UserSummaryDto otherParticipant;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private long unreadCount;
}


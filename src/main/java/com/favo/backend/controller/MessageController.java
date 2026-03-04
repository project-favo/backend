package com.favo.backend.controller;

import com.favo.backend.Domain.message.ConversationDto;
import com.favo.backend.Domain.message.MessageDto;
import com.favo.backend.Domain.message.SendMessageRequest;
import com.favo.backend.Domain.user.SystemUser;
import com.favo.backend.Service.Message.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final ConversationService conversationService;

    @PostMapping("/send")
    public ResponseEntity<MessageDto> sendMessage(
            @AuthenticationPrincipal SystemUser user,
            @RequestBody SendMessageRequest request
    ) {
        Long currentUserId = user != null ? user.getId() : null;
        MessageDto dto = conversationService.sendMessage(currentUserId, request);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/conversations")
    public ResponseEntity<Page<ConversationDto>> getMyConversations(
            @AuthenticationPrincipal SystemUser user,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Long currentUserId = user != null ? user.getId() : null;
        Page<ConversationDto> page = conversationService.getMyConversations(currentUserId, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<Page<MessageDto>> getMessages(
            @AuthenticationPrincipal SystemUser user,
            @PathVariable Long conversationId,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        Long currentUserId = user != null ? user.getId() : null;
        Page<MessageDto> page = conversationService.getMessages(currentUserId, conversationId, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<?> getTotalUnreadCount(
            @AuthenticationPrincipal SystemUser user
    ) {
        Long currentUserId = user != null ? user.getId() : null;
        Long count = conversationService.getTotalUnreadCount(currentUserId);
        return ResponseEntity.ok(java.util.Map.of("count", count));
    }
}


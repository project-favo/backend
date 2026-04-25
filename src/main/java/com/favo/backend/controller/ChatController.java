package com.favo.backend.controller;

import com.favo.backend.Domain.chat.ChatRequest;
import com.favo.backend.Domain.chat.ChatResponse;
import com.favo.backend.Domain.user.SystemUser;
import com.favo.backend.Service.Chat.PersonalizedChatService;
import com.favo.backend.Service.Chat.ProductChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final PersonalizedChatService personalizedChatService;
    private final ProductChatService productChatService;

    /**
     * Personalized assistant: requires Bearer token. Conversation is stored per user.
     */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @AuthenticationPrincipal SystemUser user,
            @Valid @RequestBody ChatRequest request
    ) {
        ChatResponse response = personalizedChatService.chat(user, request.getMessage());
        return ResponseEntity.ok(response);
    }

    /**
     * Ürün detayından açılan sohbet: aynı kullanıcı + ürün için ayrı geçmiş; yanıtta ürün kartı listesi yok.
     */
    @PostMapping("/product/{productId}")
    public ResponseEntity<ChatResponse> chatAboutProduct(
            @AuthenticationPrincipal SystemUser user,
            @PathVariable long productId,
            @Valid @RequestBody ChatRequest request
    ) {
        ChatResponse response = productChatService.chat(user, productId, request.getMessage());
        return ResponseEntity.ok(response);
    }
}

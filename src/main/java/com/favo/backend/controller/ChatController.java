package com.favo.backend.controller;

import com.favo.backend.Domain.chat.ChatRequest;
import com.favo.backend.Domain.chat.ChatResponse;
import com.favo.backend.Service.Chat.OpenAIChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final OpenAIChatService openAIChatService;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        ChatResponse response = openAIChatService.chat(request.getMessage());
        return ResponseEntity.ok(response);
    }
}

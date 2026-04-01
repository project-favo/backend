package com.favo.backend.Domain.chat;

/**
 * One turn in the OpenAI Chat Completions message list (user or assistant), excluding the system message.
 */
public record OpenAiChatTurn(String role, String content) {
}

package com.favo.backend.Service.Chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.favo.backend.Domain.chat.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAIChatService {

    private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4.1-mini";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openai.api.key:}")
    private String apiKey;

    public ChatResponse chat(String userMessage) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("OpenAI API key is not set. Set OPENAI_API_KEY env or openai.api.key property.");
            throw new IllegalStateException("CHATBOT_NOT_CONFIGURED");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", MODEL);
        ArrayNode messages = objectMapper.createArrayNode();
        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);
        body.set("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey.trim());

        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);
        ResponseEntity<String> response = restTemplate.exchange(
                OPENAI_CHAT_URL,
                HttpMethod.POST,
                entity,
                String.class
        );

        if (response.getStatusCode() != HttpStatusCode.valueOf(200) || response.getBody() == null) {
            log.warn("OpenAI API non-OK response: {}", response.getStatusCode());
            throw new RuntimeException("CHATBOT_ERROR");
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode choices = root.path("choices");
            if (choices.isEmpty()) {
                throw new RuntimeException("CHATBOT_ERROR");
            }
            String content = choices.get(0).path("message").path("content").asText("");
            return new ChatResponse(content);
        } catch (Exception e) {
            log.error("Failed to parse OpenAI response", e);
            throw new RuntimeException("CHATBOT_ERROR", e);
        }
    }
}

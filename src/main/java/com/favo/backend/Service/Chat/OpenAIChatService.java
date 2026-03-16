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

    /** Bot sadece Favo uygulamasına özel yardım etsin; uygulama bilgisi burada veriliyor. */
    private static final String SYSTEM_PROMPT =
            "You are the in-app support assistant for Favo. Favo is a mobile app where users discover products, "
            + "read and write reviews, and like products.\n\n"
            + "How Favo works (use this to give accurate help):\n"
            + "- Home: Top 10 products carousel, category chips (All + categories), product grid. Tap a product to open its detail.\n"
            + "- Bottom navigation: Home, Search, Add, Favorites, Profile.\n"
            + "- Product detail (Review page): product info, reviews list, option to write a review or like the product.\n"
            + "- Search: use the search icon in the bottom bar to search products.\n"
            + "- Profile: view/edit profile, settings, change password, delete account.\n"
            + "- Only help with Favo features. Do not suggest other shopping sites or platforms. "
            + "Keep answers short and clear. Reply in the same language the user writes in.";

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
        ObjectNode systemMsg = objectMapper.createObjectNode();
        systemMsg.put("role", "system");
        systemMsg.put("content", SYSTEM_PROMPT);
        messages.add(systemMsg);
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

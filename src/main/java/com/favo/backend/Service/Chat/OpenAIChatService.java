package com.favo.backend.Service.Chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.favo.backend.Domain.chat.ChatResponse;
import com.favo.backend.Domain.chat.OpenAiChatTurn;

import java.util.ArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@Slf4j
public class OpenAIChatService {

    private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4.1-mini";

    /**
     * Base instructions; {@link com.favo.backend.Service.Chat.PersonalizedChatService} appends user-specific context.
     */
    public static final String BASE_SYSTEM_PROMPT =
            "You are the in-app support assistant for Favo. Favo is a mobile app where users discover products, "
                    + "read and write reviews, follow other users, and like products. "
                    + "The app does not display purchase prices or checkout; focus on discovery and community reviews—never invent or guess prices.\n\n"
                    + "How Favo works (use this to give accurate help):\n"
                    + "- Home: Top 10 products carousel, category chips (All + categories), product grid. Tap a product to open its detail.\n"
                    + "- Bottom navigation: Home, Search, Add, Favorites, Profile.\n"
                    + "- Product detail (Review page): product info, reviews list, option to write a review or like the product.\n"
                    + "- Search: use the search icon in the bottom bar to search products.\n"
                    + "- Profile: view/edit profile, settings, change password, delete account.\n"
                    + "- Only help with Favo features. Do not suggest other shopping sites or platforms. "
                    + "Keep answers short and clear. Reply in the same language as the user's latest substantive message in this thread.\n"
                    + "Use the personalized context below when it helps (preferences, recent activity), but do not reveal private data of other users. "
                    + "Prioritize the ongoing conversation topic over wishlist or old likes unless the user explicitly asks for suggestions from their favorites or likes.";

    /**
     * Ürün detayından açılan sohbet: aşağıya ürün özeti ve yorum alıntıları eklenir.
     */
    public static final String PRODUCT_CHAT_SYSTEM_PREFIX =
            "You are Favo's assistant for questions about ONE specific product the user opened from the app. "
                    + "Answer only using the product facts and review excerpts in the context below. "
                    + "If something is not in the context, say you do not have that information. "
                    + "Favo does not show prices or selling—if the user asks for price, shipping, or where to buy, explain that Favo is for discovery and reviews, not purchases. "
                    + "Treat review excerpts as community opinions, not guaranteed facts. Do not name or identify individual reviewers. "
                    + "Keep replies concise unless the user asks for more detail. "
                    + "Reply in the same language as the user's latest message in this thread.\n\n";

    /**
     * İki ürün karşılaştırma özeti: Favo bağlamı + modelin genel ürün bilgisi. Canlı web araması yok.
     */
    public static final String COMPARE_SYSTEM_PROMPT =
            "You are Favo's in-app product comparison assistant. The user is viewing two products side by side. "
                    + "You are given: (1) for each product—name, category, description, community rating, and short review excerpts from Favo, "
                    + "(2) you may also use your general knowledge about the product type, common specs, and typical use cases where it helps, "
                    + "as long as you do not present guesses as certainties. If you are uncertain about a specific spec, say so. "
                    + "Do not mention, estimate, or compare prices, discounts, or deals. Do not say where to buy. "
                    + "Favo is for discovery and community reviews, not shopping checkout. "
                    + "Treat review excerpts as user opinions, not facts. Do not name or identify individual reviewers. "
                    + "Write 2-4 short paragraphs: main differences, tradeoffs, and who might prefer which. "
                    + "If the product titles or descriptions in the context are clearly in one language, reply in that language; if empty or clearly mixed, use English. "
                    + "End with one short line that the summary is AI-generated and not a substitute for checking current details yourself.\n\n";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openai.api.key:}")
    private String apiKey;

    /**
     * Sends system prompt + prior turns (oldest first) + the new user message to OpenAI.
     */
    public ChatResponse completeConversation(String fullSystemPrompt, List<OpenAiChatTurn> priorTurnsOldestFirst, String newUserMessage) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("OpenAI API key is not set. Set OPENAI_API_KEY env or openai.api.key property.");
            throw new IllegalStateException("CHATBOT_NOT_CONFIGURED");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", MODEL);
        ArrayNode messages = objectMapper.createArrayNode();

        ObjectNode systemMsg = objectMapper.createObjectNode();
        systemMsg.put("role", "system");
        systemMsg.put("content", fullSystemPrompt);
        messages.add(systemMsg);

        for (OpenAiChatTurn turn : priorTurnsOldestFirst) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("role", turn.role());
            node.put("content", turn.content());
            messages.add(node);
        }

        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", newUserMessage);
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
            return new ChatResponse(content, new ArrayList<>());
        } catch (Exception e) {
            log.error("Failed to parse OpenAI response", e);
            throw new RuntimeException("CHATBOT_ERROR", e);
        }
    }
}

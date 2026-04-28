package com.favo.backend.Service.Chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.favo.backend.Domain.chat.ChatResponse;
import com.favo.backend.Domain.chat.OpenAiChatTurn;
import com.favo.backend.Domain.chat.OpenAiIntentResult;

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
                    + "If one product has no community reviews or no rating in the context while the other has a low average (for example under 3/5) or clearly negative review sentiment, "
                    + "do not frame the rated product as the safer or default pick solely because it has a score. "
                    + "Call out weak community feedback honestly; for the product with no score, state that user reception is unknown, not that it is worse. "
                    + "If both differ in style or use case, you may still say who might prefer which, but do not imply the community prefers the low-rated product over an unrated one. "
                    + "Write 2-4 short paragraphs: main differences, tradeoffs, and who might prefer which. "
                    + "If the product titles or descriptions in the context are clearly in one language, reply in that language; if empty or clearly mixed, use English. "
                    + "End with one short line that the summary is AI-generated and not a substitute for checking current details yourself.\n\n";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openai.api.key:}")
    private String apiKey;

    /**
     * JSON instruction appended to the system prompt for the general chat flow.
     * Instructs the model to return a structured JSON so the backend can extract
     * the product search query directly from the AI instead of relying on regex heuristics.
     */
    private static final String JSON_INTENT_INSTRUCTION =
            "\n\nIMPORTANT: You MUST respond with a valid JSON object containing exactly these fields:\n"
                    + "{\n"
                    + "  \"reply\": \"<your conversational response here>\",\n"
                    + "  \"product_search\": \"<1-3 word English search term if the user wants product recommendations or discovery, otherwise null>\",\n"
                    + "  \"prefer_high_rated\": <true if user explicitly wants highly rated / best-reviewed products, false otherwise>,\n"
                    + "  \"use_my_likes\": <true if user explicitly asks for suggestions based on their liked or favorited products, false otherwise>\n"
                    + "}\n"
                    + "Rules for product_search:\n"
                    + "- Use a specific category or product type (e.g. \"smartphone\", \"gaming laptop\", \"running shoes\", \"bluetooth headphone\").\n"
                    + "- Set to null when the user is asking a general question, greeting, or anything unrelated to discovering products.\n"
                    + "- Do NOT set product_search just because products are mentioned in personalization context; only set it when the user actively wants recommendations now.\n"
                    + "- When product_search is non-null, keep reply to 1-2 sentences that align with that product category.";

    /**
     * Calls OpenAI with JSON-mode structured output to extract both the conversational reply
     * and the product search intent from the user's message. Falls back to a plain text reply
     * with no product intent on any parse failure.
     */
    public OpenAiIntentResult completeConversationWithIntent(
            String fullSystemPrompt,
            List<OpenAiChatTurn> priorTurnsOldestFirst,
            String newUserMessage) {

        String systemWithJson = fullSystemPrompt + JSON_INTENT_INSTRUCTION;

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", MODEL);

        ObjectNode responseFormat = objectMapper.createObjectNode();
        responseFormat.put("type", "json_object");
        body.set("response_format", responseFormat);

        ArrayNode messages = buildMessages(systemWithJson, priorTurnsOldestFirst, newUserMessage);
        body.set("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey.trim());

        try {
            HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    OPENAI_CHAT_URL, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() != HttpStatusCode.valueOf(200) || response.getBody() == null) {
                log.warn("OpenAI intent call non-OK: {}", response.getStatusCode());
                return fallbackIntentResult(newUserMessage);
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            String rawContent = root.path("choices").get(0).path("message").path("content").asText("");
            JsonNode parsed = objectMapper.readTree(rawContent);

            String reply = parsed.path("reply").asText("").trim();
            if (reply.isEmpty()) {
                reply = rawContent;
            }

            JsonNode psNode = parsed.path("product_search");
            String productSearch = (psNode.isNull() || psNode.isMissingNode()) ? null : psNode.asText("").trim();
            if (productSearch != null && productSearch.isEmpty()) {
                productSearch = null;
            }

            boolean preferHighRated = parsed.path("prefer_high_rated").asBoolean(false);
            boolean useMyLikes = parsed.path("use_my_likes").asBoolean(false);

            return new OpenAiIntentResult(reply, productSearch, preferHighRated, useMyLikes);

        } catch (Exception e) {
            log.warn("Failed to parse OpenAI intent response, falling back to plain call: {}", e.getMessage());
            return fallbackIntentResult(newUserMessage);
        }
    }

    private OpenAiIntentResult fallbackIntentResult(String userMessage) {
        try {
            ChatResponse plain = completeConversation(
                    BASE_SYSTEM_PROMPT, List.of(), userMessage);
            return new OpenAiIntentResult(plain.getReply(), null, false, false);
        } catch (Exception ex) {
            log.error("Fallback plain call also failed", ex);
            return new OpenAiIntentResult("Sorry, I'm having trouble right now. Please try again.", null, false, false);
        }
    }

    private ArrayNode buildMessages(String systemPrompt, List<OpenAiChatTurn> priorTurns, String newUserMessage) {
        ArrayNode messages = objectMapper.createArrayNode();

        ObjectNode systemMsg = objectMapper.createObjectNode();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        messages.add(systemMsg);

        for (OpenAiChatTurn turn : priorTurns) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("role", turn.role());
            node.put("content", turn.content());
            messages.add(node);
        }

        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", newUserMessage);
        messages.add(userMsg);

        return messages;
    }

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
        body.set("messages", buildMessages(fullSystemPrompt, priorTurnsOldestFirst, newUserMessage));

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

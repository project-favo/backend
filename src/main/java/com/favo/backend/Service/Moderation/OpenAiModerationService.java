package com.favo.backend.Service.Moderation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.favo.backend.Domain.review.ToxicityResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAiModerationService {

    private static final String MODERATION_MODEL = "gpt-4o-mini";
    private static final int TOXICITY_THRESHOLD = 70;
    private static final String SYSTEM_PROMPT = "Sen kati ve acimasiz bir Turkce icerik moderatorusun. "
            + "Gorevin, sana verilen metni analiz edip, icinde en ufak bir kufur, argo, hakaret, asagilama veya toksik soylem varsa bunu yakalamaktir. "
            + "Yanitini SADECE asagidaki JSON formatinda vermelisin, baska hicbir aciklama yazma: "
            + "{ \"isToxic\": boolean, \"score\": number (0-100 arasi), \"reason\": string }";

    private final RestTemplate restTemplate;
    private final Environment environment;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openai.moderation.url:https://api.openai.com/v1/chat/completions}")
    private String moderationUrl;

    public ToxicityResultDto analyze(String text) {
        if (text == null || text.isBlank()) {
            return new ToxicityResultDto(null, false);
        }

        String apiKey = resolveOpenAiApiKey();
        if (apiKey.isBlank()) {
            log.warn("OPENAI_API_KEY missing; moderation check skipped.");
            return new ToxicityResultDto(null, false);
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> systemMessage = Map.of(
                    "role", "system",
                    "content", SYSTEM_PROMPT
            );
            Map<String, Object> userMessage = Map.of(
                    "role", "user",
                    "content", text
            );
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", MODERATION_MODEL);
            requestBody.put("messages", List.of(systemMessage, userMessage));
            requestBody.put("response_format", Map.of("type", "json_object"));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(moderationUrl, entity, String.class);

            String rawResponse = response.getBody();
            log.info("OpenAI moderation raw response: {}", rawResponse);
            return extractResult(rawResponse);
        } catch (Exception ex) {
            log.warn("OpenAI moderation call failed", ex);
            return new ToxicityResultDto(null, false);
        }
    }

    private ToxicityResultDto extractResult(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return new ToxicityResultDto(null, false);
        }

        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.asText("").isBlank()) {
                return new ToxicityResultDto(null, false);
            }

            JsonNode moderationJson = objectMapper.readTree(contentNode.asText());
            boolean modelToxic = moderationJson.path("isToxic").asBoolean(false);
            int toxicityScore = clampScore(moderationJson.path("score").asInt(0));
            String reason = moderationJson.path("reason").asText("Belirtilmedi");

            log.info("Analiz Sonucu: Skor: {}, Neden: {}", toxicityScore, reason);

            boolean isToxic = modelToxic || toxicityScore > TOXICITY_THRESHOLD;
            return new ToxicityResultDto((double) toxicityScore, isToxic);
        } catch (Exception ex) {
            log.warn("Failed to parse moderation JSON response", ex);
            return new ToxicityResultDto(null, false);
        }
    }

    private int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private String resolveOpenAiApiKey() {
        String v = trimToEmpty(environment.getProperty("openai.api.key"));
        if (!v.isEmpty()) {
            return v;
        }
        v = trimToEmpty(environment.getProperty("OPENAI_API_KEY"));
        if (!v.isEmpty()) {
            return v;
        }
        return trimToEmpty(System.getenv("OPENAI_API_KEY"));
    }

    private static String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

}


package com.favo.backend.Service.Moderation;

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
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAiModerationService {

    private static final String MODERATION_MODEL = "omni-moderation-latest";

    private final RestTemplate restTemplate;
    private final Environment environment;

    @Value("${openai.moderation.url:https://api.openai.com/v1/moderations}")
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

            Map<String, Object> requestBody = Map.of(
                    "model", MODERATION_MODEL,
                    "input", text
            );
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Object> response = restTemplate.postForEntity(moderationUrl, entity, Object.class);
            Map<?, ?> responseMap = response.getBody() instanceof Map<?, ?> m ? m : null;
            ToxicityResultDto result = extractResult(responseMap);
            if (result.isToxic()) {
                throw new RuntimeException("Text violates moderation policy");
            }
            return result;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("OpenAI moderation call failed: {}", ex.getMessage());
            return new ToxicityResultDto(null, false);
        }
    }

    private ToxicityResultDto extractResult(Map<?, ?> body) {
        if (body == null) {
            return new ToxicityResultDto(null, false);
        }
        Object resultsObj = body.get("results");
        if (!(resultsObj instanceof List<?> results) || results.isEmpty()) {
            return new ToxicityResultDto(null, false);
        }
        Object firstObj = results.get(0);
        if (!(firstObj instanceof Map<?, ?> first)) {
            return new ToxicityResultDto(null, false);
        }

        boolean flagged = Boolean.TRUE.equals(first.get("flagged"));
        Double maxScore = extractMaxCategoryScore(first.get("category_scores"));
        return new ToxicityResultDto(maxScore, flagged);
    }

    private Double extractMaxCategoryScore(Object categoryScoresObj) {
        if (!(categoryScoresObj instanceof Map<?, ?> categoryScores) || categoryScores.isEmpty()) {
            return null;
        }
        return categoryScores.values().stream()
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::doubleValue)
                .max(Double::compareTo)
                .orElse(null);
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


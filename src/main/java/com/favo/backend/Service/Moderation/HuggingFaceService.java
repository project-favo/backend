package com.favo.backend.Service.Moderation;

import com.favo.backend.Domain.review.ToxicityResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class HuggingFaceService {

    private final RestTemplate restTemplate;
    private final Environment environment;

    @Value("${huggingface.api.url}")
    private String apiUrl;

    public ToxicityResultDto analyze(String text) {
        if (text == null || text.isBlank()) {
            return new ToxicityResultDto(null, false);
        }
        String apiToken = resolveHuggingFaceToken();
        if (apiToken.isBlank()) {
            log.warn("HuggingFace token missing; skipping toxicity call (set HUGGINGFACE_API_TOKEN on Railway or in application-local.properties).");
            return new ToxicityResultDto(null, false);
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiToken);

            Map<String, String> body = Map.of("inputs", text);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<List> response =
                    restTemplate.postForEntity(apiUrl, entity, List.class);

            Double toxicScore = extractToxicScore(response.getBody());
            boolean isToxic = toxicScore != null && toxicScore >= 0.80;
            return new ToxicityResultDto(toxicScore, isToxic);
        } catch (Exception ex) {
            log.warn("HuggingFace toxicity call failed: {}", ex.getMessage());
            return new ToxicityResultDto(null, false);
        }
    }

    private String resolveHuggingFaceToken() {
        String v = trimToEmpty(environment.getProperty("huggingface.api.token"));
        if (!v.isEmpty()) {
            return v;
        }
        v = trimToEmpty(environment.getProperty("HUGGINGFACE_API_TOKEN"));
        if (!v.isEmpty()) {
            return v;
        }
        return trimToEmpty(System.getenv("HUGGINGFACE_API_TOKEN"));
    }

    private static String trimToEmpty(String s) {
        if (s == null) {
            return "";
        }
        return s.trim();
    }

    @SuppressWarnings("unchecked")
    private Double extractToxicScore(List<?> root) {
        if (root == null || root.isEmpty()) {
            return null;
        }
        Object firstList = root.get(0);
        if (!(firstList instanceof List<?> inner) || inner.isEmpty()) {
            return null;
        }
        return inner.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(m -> "toxic".equals(m.get("label")))
                .map(m -> (Number) m.get("score"))
                .map(Number::doubleValue)
                .findFirst()
                .orElse(null);
    }
}

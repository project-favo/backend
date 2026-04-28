package com.favo.backend.Service.Moderation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class HuggingFaceService {

    private final RestTemplate restTemplate;
    private final Environment environment;

    @Value("${huggingface.api.url:https://api-inference.huggingface.co/models/unitary/toxic-bert}")
    private String apiUrl;

    public HuggingFaceService(
            @Qualifier("toxicityRestTemplate") RestTemplate restTemplate,
            Environment environment
    ) {
        this.restTemplate = restTemplate;
        this.environment = environment;
    }

    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 3,
            backoff = @Backoff(
                    delay = 300,
                    multiplier = 2.0,
                    maxDelay = 3000
            )
    )
    public Map<String, Double> analyzeLabelScores(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }

        String apiToken = resolveHuggingFaceToken();
        if (apiToken.isBlank()) {
            throw new IllegalStateException("HuggingFace token missing");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiToken);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("inputs", text), headers);
        ResponseEntity<?> response = restTemplate.postForEntity(apiUrl, entity, List.class);
        Object responseBody = response.getBody();
        if (!(responseBody instanceof List<?> rootList)) {
            throw new IllegalStateException("Unexpected HuggingFace response body");
        }
        return extractLabelScores(rootList);
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
        return s == null ? "" : s.trim();
    }

    private Map<String, Double> extractLabelScores(List<?> root) {
        if (root == null || root.isEmpty()) {
            throw new IllegalStateException("Empty HuggingFace response");
        }
        Object firstList = root.get(0);
        if (!(firstList instanceof List<?> inner) || inner.isEmpty()) {
            throw new IllegalStateException("Missing HuggingFace label scores");
        }

        Map<String, Double> labelToScore = new LinkedHashMap<>();
        for (Object item : inner) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            Object labelObj = m.get("label");
            Object scoreObj = m.get("score");
            if (!(labelObj instanceof String label) || !(scoreObj instanceof Number num) || Objects.isNull(num)) {
                continue;
            }
            labelToScore.put(label.toLowerCase().trim(), num.doubleValue());
        }

        if (labelToScore.isEmpty()) {
            throw new IllegalStateException("No parseable labels from HuggingFace response");
        }
        return labelToScore;
    }

    @Recover
    public Map<String, Double> recoverAnalyze(Exception ex, String text) {
        log.error("HuggingFace analyze failed after retries. textLength={}, message={}",
                text != null ? text.length() : 0,
                ex.getMessage());
        throw new IllegalStateException("Toxicity analysis failed after retries", ex);
    }
}

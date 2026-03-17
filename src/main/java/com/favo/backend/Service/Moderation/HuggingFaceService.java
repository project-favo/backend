package com.favo.backend.Service.Moderation;

import com.favo.backend.Domain.review.ToxicityResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${huggingface.api.url}")
    private String apiUrl;

    @Value("${huggingface.api.token}")
    private String apiToken;

    public ToxicityResultDto analyze(String text) {
        if (text == null || text.isBlank()) {
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


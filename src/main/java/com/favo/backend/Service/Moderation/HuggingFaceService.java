package com.favo.backend.Service.Moderation;

import com.favo.backend.Domain.review.ToxicityResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class HuggingFaceService {

    /**
     * unitary/toxic-bert ve benzeri çok başlıklı toxicity modellerinde yaygın etiketler.
     * Farklı Jigsaw varyantları (toxicity / identity_attack vb.) için ek isimler de dahil.
     */
    private static final Set<String> TOXICITY_HEAD_LABELS = new LinkedHashSet<>(List.of(
            "toxic",
            "severe_toxic",
            "obscene",
            "threat",
            "insult",
            "identity_hate",
            "toxicity",
            "severe_toxicity",
            "identity_attack",
            "sexual_explicit"
    ));

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

            Map<String, String> requestBody = Map.of("inputs", text);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<?> response = restTemplate.postForEntity(apiUrl, entity, List.class);
            Object responseBody = response.getBody();
            if (!(responseBody instanceof List<?> rootList)) {
                return new ToxicityResultDto(null, false);
            }
            Double toxicScore = extractCombinedToxicityScore(rootList);
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

    /**
     * HF text-classification cevabı: {@code [[{label, score}, ...]]} biçiminde.
     * Birleşik skor = bilinen toxicity başlıkları arasındaki maksimum skor;
     * hiçbiri yoksa (farklı model) tüm etiket skorlarının maksimumu kullanılır.
     */
    private Double extractCombinedToxicityScore(List<?> root) {
        if (root == null || root.isEmpty()) {
            return null;
        }
        Object firstList = root.get(0);
        if (!(firstList instanceof List<?> inner) || inner.isEmpty()) {
            return null;
        }
        Map<String, Double> labelToScore = new LinkedHashMap<>();
        for (Object item : inner) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            Object labelObj = m.get("label");
            Object scoreObj = m.get("score");
            if (!(labelObj instanceof String label) || !(scoreObj instanceof Number num)) {
                continue;
            }
            labelToScore.put(label, num.doubleValue());
        }
        if (labelToScore.isEmpty()) {
            return null;
        }
        double maxAmongHeads = TOXICITY_HEAD_LABELS.stream()
                .mapToDouble(l -> labelToScore.getOrDefault(l, Double.NEGATIVE_INFINITY))
                .max()
                .orElse(Double.NEGATIVE_INFINITY);
        if (maxAmongHeads > Double.NEGATIVE_INFINITY) {
            return maxAmongHeads;
        }
        return labelToScore.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .getAsDouble();
    }
}

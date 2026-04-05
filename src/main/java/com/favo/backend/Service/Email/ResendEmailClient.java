package com.favo.backend.Service.Email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <a href="https://resend.com/docs/api-reference/emails/send-email">Resend</a> — HTTPS, Railway Hobby SMTP engelini aşar.
 */
@Service
@Slf4j
public class ResendEmailClient {

    private static final String API_URL = "https://api.resend.com/emails";

    private final RestTemplate restTemplate;

    @Value("${resend.api.key:}")
    private String apiKey;

    @Value("${resend.from:}")
    private String from;

    public ResendEmailClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(apiKey) && StringUtils.hasText(from);
    }

    public MailSendResult sendPlain(String to, String subject, String textBody) {
        String key = apiKey != null ? apiKey.trim() : "";
        String sender = from != null ? from.trim() : "";
        if (!StringUtils.hasText(key) || !StringUtils.hasText(sender)) {
            return MailSendResult.fail("RESEND_NOT_CONFIGURED");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", sender);
        body.put("to", List.of(to.trim()));
        body.put("subject", subject);
        body.put("text", textBody);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(key);

        try {
            restTemplate.postForEntity(API_URL, new HttpEntity<>(body, headers), String.class);
            log.info("Resend: verification email queued/sent to {}", to);
            return MailSendResult.ok();
        } catch (HttpStatusCodeException e) {
            String detail = e.getStatusCode() + ": " + truncate(safeBody(e));
            log.error("Resend API error: {}", detail);
            return MailSendResult.fail("RESEND_API_REJECTED", detail);
        } catch (Exception e) {
            String detail = e.getClass().getSimpleName() + ": " + truncate(e.getMessage());
            log.error("Resend request failed: {}", detail, e);
            return MailSendResult.fail("RESEND_REQUEST_FAILED", detail);
        }
    }

    private static String safeBody(HttpStatusCodeException e) {
        try {
            String r = e.getResponseBodyAsString();
            return r != null ? r.replaceAll("\\s+", " ").trim() : e.getMessage();
        } catch (Exception ex) {
            return e.getMessage();
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}

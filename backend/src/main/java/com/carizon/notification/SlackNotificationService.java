package com.carizon.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Slack Incoming Webhook 알림 서비스.
 * system_config.notification.slack.webhook_url 기반으로 URL을 가져오며,
 * 값이 없으면 app.notification.slack.webhook-url(Fallback)로 동작.
 */
@Slf4j
@Service
public class SlackNotificationService {

    private static final String CONFIG_KEY = "notification.slack.webhook_url";
    private static final long CONFIG_CACHE_TTL_MS = 60_000L;

    private final String fallbackWebhookUrl;
    private final JdbcTemplate jdbcTemplate;
    private volatile String cachedWebhookUrl;
    private volatile long webhookCacheAt = 0L;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public SlackNotificationService(
            @Value("${app.notification.slack.webhook-url:}") String webhookUrl,
            JdbcTemplate jdbcTemplate
    ) {
        this.fallbackWebhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.jdbcTemplate = jdbcTemplate;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public boolean isEnabled() {
        return !getWebhookUrl().isEmpty();
    }

    /** 단순 텍스트 메시지 전송 */
    public void send(String text) {
        if (!isEnabled()) return;
        try {
            String body = mapper.writeValueAsString(Map.of("text", text));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(getWebhookUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("[slack-notify] send failed: status={} body={}", res.statusCode(), res.body());
            }
        } catch (Exception e) {
            log.warn("[slack-notify] send error: {}", e.getMessage());
        }
    }

    /** Block Kit 메시지 (섹션 구분선 포함) */
    public void sendBlocks(String text) {
        send(text); // 단순화: text 형식으로 전송
    }

    private String getWebhookUrl() {
        long now = System.currentTimeMillis();
        if (cachedWebhookUrl != null && (now - webhookCacheAt) < CONFIG_CACHE_TTL_MS) {
            return cachedWebhookUrl;
        }

        String dbUrl = loadWebhookUrlFromDb();
        String resolved = dbUrl.isBlank() ? fallbackWebhookUrl : dbUrl;
        resolved = resolved == null ? "" : resolved.trim();

        cachedWebhookUrl = resolved;
        webhookCacheAt = now;
        return resolved;
    }

    private String loadWebhookUrlFromDb() {
        try {
            String value = jdbcTemplate.queryForObject(
                    "SELECT config_value FROM system_config WHERE config_key = ?",
                    String.class,
                    CONFIG_KEY
            );
            return value == null ? "" : value.trim();
        } catch (Exception e) {
            log.debug("[slack-notify] failed to load webhook url from system_config: {}", e.getMessage());
            return "";
        }
    }
}

package com.carizon.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Slack Incoming Webhook 알림 서비스.
 * SLACK_WEBHOOK_URL 환경변수 미설정 시 조용히 skip.
 */
@Slf4j
@Service
public class SlackNotificationService {

    private final String webhookUrl;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public SlackNotificationService(@Value("${app.notification.slack.webhook-url:}") String webhookUrl) {
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public boolean isEnabled() {
        return !webhookUrl.isEmpty();
    }

    /** 단순 텍스트 메시지 전송 */
    public void send(String text) {
        if (!isEnabled()) return;
        try {
            String body = mapper.writeValueAsString(Map.of("text", text));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
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
}

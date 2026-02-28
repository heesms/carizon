package com.carizon.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 방문자 Slack 알림 서비스.
 * - IP → 지역 변환 (ip-api.com, 무료, 키 불필요)
 * - 같은 IP 재알림 억제 (dedup-minutes 설정)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitorNotificationService {

    private final SlackNotificationService slack;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Value("${app.notification.slack.visitor:true}")
    private boolean visitorEnabled;

    @Value("${app.notification.slack.visitor-dedup-minutes:30}")
    private int dedupMinutes;

    // IP별 마지막 알림 시간 캐시 (최대 500개)
    private final Map<String, Instant> recentIps = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Instant> eldest) {
            return size() > 500;
        }
    };

    public void notifyVisit(String ip, String page, String query, String userAgent, String referer) {
        log.info("[visitor-notify] visit ip={} page={} query={}", ip, page, query);
        if (!slack.isEnabled()) { log.warn("[visitor-notify] slack disabled (webhook-url empty)"); return; }
        if (!visitorEnabled)    { log.warn("[visitor-notify] visitor notification disabled"); return; }
        if (isPrivateIp(ip))    { log.info("[visitor-notify] skip private ip={}", ip); return; }

        // 중복 억제
        synchronized (recentIps) {
            Instant last = recentIps.get(ip);
            if (last != null && Instant.now().isBefore(last.plusSeconds(dedupMinutes * 60L))) {
                log.info("[visitor-notify] dedup skip ip={} (last={})", ip, last);
                return;
            }
            recentIps.put(ip, Instant.now());
        }

        log.info("[visitor-notify] sending slack for ip={}", ip);
        // 비동기로 처리 (응답 지연 방지)
        Thread.ofVirtual().start(() -> {
            try {
                String location = resolveLocation(ip);
                log.info("[visitor-notify] location={} for ip={}", location, ip);
                String msg = buildMessage(ip, location, page, query, userAgent, referer);
                slack.send(msg);
                log.info("[visitor-notify] slack sent ok");
            } catch (Exception e) {
                log.warn("[visitor-notify] error: {}", e.getMessage(), e);
            }
        });
    }

    private String resolveLocation(String ip) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://ip-api.com/json/" + ip + "?fields=status,country,regionName,city&lang=ko"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode node = mapper.readTree(res.body());
            if ("success".equals(node.path("status").asText())) {
                String country = node.path("country").asText("");
                String region  = node.path("regionName").asText("");
                String city    = node.path("city").asText("");
                return String.join(" ", country, region, city).trim();
            }
        } catch (Exception e) {
            log.debug("[visitor-notify] geo lookup failed: {}", e.getMessage());
        }
        return "알 수 없음";
    }

    private String buildMessage(String ip, String location, String page, String query, String userAgent, String referer) {
        StringBuilder sb = new StringBuilder();
        sb.append("🧑‍💻 *새 방문자*\n");
        sb.append("📍 위치: ").append(location).append(" (").append(ip).append(")\n");
        sb.append("📄 페이지: ").append(page).append("\n");
        if (query != null && !query.isBlank()) {
            sb.append("🔍 검색어: `").append(query).append("`\n");
        }
        if (referer != null && !referer.isBlank()) {
            sb.append("🔗 유입: ").append(referer).append("\n");
        }
        if (userAgent != null && !userAgent.isBlank()) {
            String ua = userAgent.length() > 80 ? userAgent.substring(0, 80) + "…" : userAgent;
            sb.append("🖥️ UA: `").append(ua).append("`");
        }
        return sb.toString();
    }

    private boolean isPrivateIp(String ip) {
        if (ip == null || ip.isBlank()) return true;
        return ip.startsWith("127.") || ip.startsWith("10.") || ip.startsWith("192.168.")
                || ip.startsWith("172.") || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1");
    }
}

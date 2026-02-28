package com.carizon.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
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
 * 방문자 및 사용자 액션 Slack 알림 서비스.
 * - IP → 지역 변환 (ip-api.com, 무료, 키 불필요)
 * - 같은 IP 재알림 억제 (dedup-minutes 설정)
 * - 로컬 테스트 모드: private IP도 [local-test] 태그로 알림 발송
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

    @Value("${app.notification.slack.local-test-enabled:false}")
    private boolean localTestEnabled;

    // IP별 마지막 알림 시간 캐시 (최대 500개)
    private final Map<String, Instant> recentIps = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Instant> eldest) {
            return size() > 500;
        }
    };

    /** HTTP 요청에서 클라이언트 실제 IP 추출 (X-Forwarded-For → X-Real-IP → remoteAddr) */
    public static String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    public void notifyVisit(String ip, String page, String query, String userAgent, String referer) {
        log.info("[slack-notify] visit ip={} page={} query={}", ip, page, query);
        if (!slack.isEnabled()) { log.warn("[slack-notify] slack disabled (webhook-url empty)"); return; }
        if (!visitorEnabled)    { log.warn("[slack-notify] visitor notification disabled"); return; }

        if (isPrivateIp(ip)) {
            if (localTestEnabled) {
                log.info("[slack-notify] local-test visit for private ip={}", ip);
                Thread.ofVirtual().start(() -> {
                    try {
                        String msg = buildVisitMessage(ip, "로컬", page, query, userAgent, referer, true);
                        slack.send(msg);
                        log.info("[slack-notify] local-test slack sent ok");
                    } catch (Exception e) {
                        log.warn("[slack-notify] local-test error: {}", e.getMessage());
                    }
                });
            } else {
                log.info("[slack-notify] skip private ip={}", ip);
            }
            return;
        }

        // 중복 억제
        synchronized (recentIps) {
            Instant last = recentIps.get(ip);
            if (last != null && Instant.now().isBefore(last.plusSeconds(dedupMinutes * 60L))) {
                log.info("[slack-notify] dedup skip ip={} (last={})", ip, last);
                return;
            }
            recentIps.put(ip, Instant.now());
        }

        log.info("[slack-notify] sending slack for ip={}", ip);
        Thread.ofVirtual().start(() -> {
            try {
                String location = resolveLocation(ip);
                log.info("[slack-notify] location={} for ip={}", location, ip);
                String msg = buildVisitMessage(ip, location, page, query, userAgent, referer, false);
                slack.send(msg);
                log.info("[slack-notify] slack sent ok");
            } catch (Exception e) {
                log.warn("[slack-notify] error: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 사용자 액션 알림 (추천, 랭킹, 찜 등).
     * private IP는 localTestEnabled=true 일 때만 [local-test] 태그로 발송.
     *
     * @param ip      클라이언트 IP
     * @param ua      User-Agent
     * @param emoji   이모지 (예: "🤖")
     * @param title   알림 제목 (예: "AI 추천 요청")
     * @param details 상세 내용 (개행 포함 가능)
     */
    public void notifyUserAction(String ip, String ua, String emoji, String title, String details) {
        if (!slack.isEnabled()) return;
        boolean isPrivate = isPrivateIp(ip);
        if (isPrivate && !localTestEnabled) {
            log.info("[slack-notify] skip private ip for user action ip={}", ip);
            return;
        }
        final String prefix = isPrivate ? "[local-test] " : "";
        Thread.ofVirtual().start(() -> {
            try {
                String location = isPrivate ? "로컬" : resolveLocation(ip);
                StringBuilder sb = new StringBuilder();
                sb.append(emoji).append(" *").append(prefix).append(title).append("*\n");
                if (details != null && !details.isBlank()) {
                    sb.append(details).append("\n");
                }
                sb.append("📍 위치: ").append(location).append(" (").append(ip).append(")");
                if (ua != null && !ua.isBlank()) {
                    String uaShort = ua.length() > 80 ? ua.substring(0, 80) + "…" : ua;
                    sb.append("\n🖥️ UA: `").append(uaShort).append("`");
                }
                slack.send(sb.toString());
                log.info("[slack-notify] user action '{}' slack sent ok ip={}", title, ip);
            } catch (Exception e) {
                log.warn("[slack-notify] user action notify error: {}", e.getMessage());
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
            log.debug("[slack-notify] geo lookup failed: {}", e.getMessage());
        }
        return "알 수 없음";
    }

    private String buildVisitMessage(String ip, String location, String page, String query,
                                     String userAgent, String referer, boolean isLocalTest) {
        StringBuilder sb = new StringBuilder();
        if (isLocalTest) {
            sb.append("🧑‍💻 *[local-test] 새 방문자*\n");
        } else {
            sb.append("🧑‍💻 *새 방문자*\n");
        }
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

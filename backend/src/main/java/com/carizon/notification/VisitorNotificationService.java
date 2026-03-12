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

    @Value("${app.notification.slack.user-action-dedup-minutes:2}")
    private int userActionDedupMinutes;

    @Value("${app.notification.slack.local-test-enabled:false}")
    private boolean localTestEnabled;

    // IP별 마지막 알림 시간 캐시 (최대 500개)
    private final Map<String, Instant> recentIps = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Instant> eldest) {
            return size() > 500;
        }
    };

    // 사용자 액션 단위(제목+상세+IP) 중복 방지 캐시 (최대 1000개)
    private final Map<String, Instant> recentUserActions = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Instant> eldest) {
            return size() > 1000;
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
        if (isBotUserAgent(userAgent)) { log.debug("[slack-notify] skip bot ua={}", userAgent); return; }

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
        if (isBotUserAgent(ua)) { log.debug("[slack-notify] skip bot ua={}", ua); return; }
        boolean isPrivate = isPrivateIp(ip);
        if (isPrivate && !localTestEnabled) {
            log.info("[slack-notify] skip private ip for user action ip={}", ip);
            return;
        }

        String normalizedTitle = title == null ? "" : title.trim();
        String normalizedDetails = details == null ? "" : details.trim();
        String actionKey = String.format("%s|%s|%s", ip, normalizedTitle, normalizedDetails);

        int ttlMinutes = Math.max(1, userActionDedupMinutes);
        synchronized (recentUserActions) {
            Instant last = recentUserActions.get(actionKey);
            if (last != null && Instant.now().isBefore(last.plusSeconds(ttlMinutes * 60L))) {
                log.info("[slack-notify] user action dedup skip ip={} title={} key={}", ip, normalizedTitle, toHashKey(actionKey));
                return;
            }
            recentUserActions.put(actionKey, Instant.now());
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

    private String toHashKey(String key) {
        return Integer.toHexString(key.hashCode());
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


    /** UA 문자열 → "Chrome 131 / Windows 10 (모바일)" 형태로 파싱 */
    // 삼성 모델번호 → 제품명 (SM-XXXXX 앞 코드 기준, 지역 suffix 무시)
    private static final java.util.Map<String, String> SAMSUNG_MODELS = java.util.Map.ofEntries(
        // Galaxy S25 시리즈
        java.util.Map.entry("SM-S931", "갤럭시 S25"),
        java.util.Map.entry("SM-S936", "갤럭시 S25+"),
        java.util.Map.entry("SM-S938", "갤럭시 S25 Ultra"),
        // Galaxy S24 시리즈
        java.util.Map.entry("SM-S921", "갤럭시 S24"),
        java.util.Map.entry("SM-S926", "갤럭시 S24+"),
        java.util.Map.entry("SM-S928", "갤럭시 S24 Ultra"),
        // Galaxy S24 FE
        java.util.Map.entry("SM-S721", "갤럭시 S24 FE"),
        // Galaxy S23 시리즈
        java.util.Map.entry("SM-S911", "갤럭시 S23"),
        java.util.Map.entry("SM-S916", "갤럭시 S23+"),
        java.util.Map.entry("SM-S918", "갤럭시 S23 Ultra"),
        // Galaxy S23 FE
        java.util.Map.entry("SM-S711", "갤럭시 S23 FE"),
        // Galaxy S22 시리즈
        java.util.Map.entry("SM-S901", "갤럭시 S22"),
        java.util.Map.entry("SM-S906", "갤럭시 S22+"),
        java.util.Map.entry("SM-S908", "갤럭시 S22 Ultra"),
        // Galaxy S21 시리즈
        java.util.Map.entry("SM-G991", "갤럭시 S21"),
        java.util.Map.entry("SM-G996", "갤럭시 S21+"),
        java.util.Map.entry("SM-G998", "갤럭시 S21 Ultra"),
        // Galaxy Z Fold
        java.util.Map.entry("SM-F956", "갤럭시 Z Fold 6"),
        java.util.Map.entry("SM-F946", "갤럭시 Z Fold 5"),
        java.util.Map.entry("SM-F936", "갤럭시 Z Fold 4"),
        java.util.Map.entry("SM-F926", "갤럭시 Z Fold 3"),
        // Galaxy Z Flip
        java.util.Map.entry("SM-F741", "갤럭시 Z Flip 6"),
        java.util.Map.entry("SM-F731", "갤럭시 Z Flip 5"),
        java.util.Map.entry("SM-F721", "갤럭시 Z Flip 4"),
        java.util.Map.entry("SM-F711", "갤럭시 Z Flip 3"),
        // Galaxy A 시리즈 (인기 모델)
        java.util.Map.entry("SM-A566", "갤럭시 A56"),
        java.util.Map.entry("SM-A546", "갤럭시 A54"),
        java.util.Map.entry("SM-A536", "갤럭시 A53"),
        java.util.Map.entry("SM-A526", "갤럭시 A52"),
        java.util.Map.entry("SM-A356", "갤럭시 A35"),
        java.util.Map.entry("SM-A346", "갤럭시 A34"),
        java.util.Map.entry("SM-A256", "갤럭시 A25"),
        java.util.Map.entry("SM-A246", "갤럭시 A24"),
        java.util.Map.entry("SM-A156", "갤럭시 A15"),
        java.util.Map.entry("SM-A146", "갤럭시 A14"),
        java.util.Map.entry("SM-A736", "갤럭시 A73"),
        java.util.Map.entry("SM-A528", "갤럭시 A52s"),
        // Galaxy Tab S
        java.util.Map.entry("SM-X916", "갤럭시 Tab S9 Ultra"),
        java.util.Map.entry("SM-X816", "갤럭시 Tab S9+"),
        java.util.Map.entry("SM-X716", "갤럭시 Tab S9"),
        java.util.Map.entry("SM-X900", "갤럭시 Tab S8 Ultra"),
        java.util.Map.entry("SM-X800", "갤럭시 Tab S8+"),
        java.util.Map.entry("SM-X700", "갤럭시 Tab S8")
    );

    private String resolveSamsungModel(String ua) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("; (SM-[A-Z0-9]+)").matcher(ua);
        if (!m.find()) return null;
        String rawModel = m.group(1).toUpperCase();
        // 앞 7자리(SM-XXXX)로 매핑 시도, 없으면 원본 코드 반환
        String prefix = rawModel.length() >= 7 ? rawModel.substring(0, 7) : rawModel;
        return SAMSUNG_MODELS.getOrDefault(prefix, rawModel);
    }

    private String parseUa(String ua) {
        if (ua == null || ua.isBlank()) return "알 수 없음";

        // ── 기기 타입 ──
        String device;
        if (ua.contains("Mobile") || (ua.contains("Android") && !ua.contains("Tablet"))) {
            device = "모바일";
        } else if (ua.contains("Tablet") || ua.contains("iPad")) {
            device = "태블릿";
        } else {
            device = "데스크톱";
        }

        // ── OS + 기기모델 ──
        String os;
        String modelLabel = null;
        if (ua.contains("iPhone")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("OS ([\\d_]+)").matcher(ua);
            os = "iOS" + (m.find() ? " " + m.group(1).replace("_", ".") : "");
            modelLabel = "iPhone";
        } else if (ua.contains("iPad")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("OS ([\\d_]+)").matcher(ua);
            os = "iOS" + (m.find() ? " " + m.group(1).replace("_", ".") : "");
            modelLabel = "iPad";
        } else if (ua.contains("Android")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("Android ([\\d.]+)").matcher(ua);
            os = "Android" + (m.find() ? " " + m.group(1) : "");
            if (ua.contains("SM-")) {
                modelLabel = resolveSamsungModel(ua);
            } else if (ua.contains("Pixel")) {
                java.util.regex.Matcher pm = java.util.regex.Pattern.compile("(Pixel [\\w]+)").matcher(ua);
                modelLabel = pm.find() ? pm.group(1) : null;
            }
        } else if (ua.contains("Windows NT")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("Windows NT ([\\d.]+)").matcher(ua);
            String ver = m.find() ? m.group(1) : "";
            os = switch (ver) { case "10.0" -> "Windows 10/11"; case "6.3" -> "Windows 8.1";
                                 case "6.2" -> "Windows 8";     case "6.1" -> "Windows 7"; default -> "Windows"; };
        } else if (ua.contains("Mac OS X")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("Mac OS X ([\\d_]+)").matcher(ua);
            os = "macOS" + (m.find() ? " " + m.group(1).replace("_", ".") : "");
        } else if (ua.contains("Linux")) {
            os = "Linux";
        } else {
            os = "기타 OS";
        }

        // ── 브라우저 ──
        String browser;
        java.util.regex.Matcher m;
        if (ua.contains("Edg/")) {
            m = java.util.regex.Pattern.compile("Edg/([\\d.]+)").matcher(ua);
            browser = "Edge" + (m.find() ? " " + majorVer(m.group(1)) : "");
        } else if (ua.contains("OPR/") || ua.contains("Opera/")) {
            m = java.util.regex.Pattern.compile("(?:OPR|Opera)/([\\d.]+)").matcher(ua);
            browser = "Opera" + (m.find() ? " " + majorVer(m.group(1)) : "");
        } else if (ua.contains("SamsungBrowser/")) {
            m = java.util.regex.Pattern.compile("SamsungBrowser/([\\d.]+)").matcher(ua);
            browser = "삼성인터넷" + (m.find() ? " " + majorVer(m.group(1)) : "");
        } else if (ua.contains("KAKAOTALK")) {
            browser = "카카오톡";
        } else if (ua.contains("NAVER")) {
            browser = "네이버앱";
        } else if (ua.contains("Chrome/")) {
            m = java.util.regex.Pattern.compile("Chrome/([\\d.]+)").matcher(ua);
            browser = "Chrome" + (m.find() ? " " + majorVer(m.group(1)) : "");
        } else if (ua.contains("Firefox/")) {
            m = java.util.regex.Pattern.compile("Firefox/([\\d.]+)").matcher(ua);
            browser = "Firefox" + (m.find() ? " " + majorVer(m.group(1)) : "");
        } else if (ua.contains("Safari/") && ua.contains("Version/")) {
            m = java.util.regex.Pattern.compile("Version/([\\d.]+)").matcher(ua);
            browser = "Safari" + (m.find() ? " " + majorVer(m.group(1)) : "");
        } else {
            browser = "기타";
        }

        String osAndModel = modelLabel != null ? os + " · " + modelLabel : os;
        return browser + " / " + osAndModel + " (" + device + ")";
    }

    private String majorVer(String ver) {
        return ver.contains(".") ? ver.substring(0, ver.indexOf('.')) : ver;
    }

    private boolean isPrivateIp(String ip) {
        if (ip == null || ip.isBlank()) return true;
        return ip.startsWith("127.") || ip.startsWith("10.") || ip.startsWith("192.168.")
                || ip.startsWith("172.") || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1");
    }

    private static final java.util.regex.Pattern BOT_UA_PATTERN = java.util.regex.Pattern.compile(
        "(?i)bot|crawler|spider|slurp|facebookexternalhit|meta-externalagent|" +
        "Twitterbot|LinkedInBot|WhatsApp|TelegramBot|Discordbot|Slackbot|" +
        "Googlebot|bingbot|Baiduspider|YandexBot|DuckDuckBot|" +
        "AhrefsBot|SemrushBot|MJ12bot|DotBot|PetalBot|GPTBot|" +
        "python-requests|okhttp|Wget|curl|libwww"
    );

    private boolean isBotUserAgent(String ua) {
        if (ua == null || ua.isBlank()) return false;
        return BOT_UA_PATTERN.matcher(ua).find();
    }
}

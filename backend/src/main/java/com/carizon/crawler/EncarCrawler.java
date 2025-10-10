package com.carizon.crawler;

import com.carizon.batch.CrawlRunRecorder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class EncarCrawler {

    private final JdbcTemplate jdbc;
    private final CrawlRunRecorder recorder;
    private final ObjectMapper mapper = new ObjectMapper();

    /** IP 변경은 하지 않음(프록시 NO). */
    private final OkHttpClient http = new OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    private static final int PAGE_SIZE = 200;

    /** 브라우저 UA 후보들(라운드로빈). */
    private static final List<String> USER_AGENTS = List.of(
            // DevTools에서 보인 모바일 UA
            "Mozilla/5.0 (Linux; Android 13; SM-G981B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36",
            // 데스크탑 크롬
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36",
            // iPhone 사파리 느낌
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
    );
    private final AtomicInteger uaIdx = new AtomicInteger(0);
    private String nextUA() {
        int i = Math.abs(uaIdx.getAndIncrement());
        return USER_AGENTS.get(i % USER_AGENTS.size());
    }

    /** 브라우저스러운 헤더 셋(Origin/Referer 중요). */
    private Request buildBrowserLikeGet(String url, String ua) {
        boolean mobile = ua.contains("Mobile") || ua.contains("Android") || ua.contains("iPhone");
        String platform = ua.contains("Android") ? "\"Android\"" : (ua.contains("iPhone") ? "\"iOS\"" : "\"Windows\"");
        return new Request.Builder()
                .url(url)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Origin", "https://car.encar.com")      // ★ 중요
                .header("Referer", "https://car.encar.com/")    // ★ 중요 (기존 오타 carencar.com 교정)
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-site")
                .header("Sec-CH-UA", "\"Chromium\";v=\"140\", \"Not=A?Brand\";v=\"24\", \"Google Chrome\";v=\"140\"")
                .header("Sec-CH-UA-Mobile", mobile ? "?1" : "?0")
                .header("Sec-CH-UA-Platform", platform)
                .header("User-Agent", ua)
                .build();
    }

    public void runOnce() {
        Instant started = Instant.now();
        String runId = recorder.recordStart("ENCAR", started);

        int totalFetched = 0;
        String cursor = "";
        int offset = 0;
        int tryCount = 0;

        try {
            jdbc.update("TRUNCATE TABLE raw_encar");
            log.info("[ENCAR] TRUNCATE raw_encar 완료");

            while (true) {
                StringBuilder url = new StringBuilder(
                        "https://api.encar.com/search/car/list/mobile?count=true" +
                                "&q=(And.Hidden.N._.CarType.A.)" +
                                "&sr=%7CMobilePriceAsc%7C" + offset + "%7C" + PAGE_SIZE +
                                "&inav=%7CMetadata%7CSort"
                );
                if (!cursor.isBlank()) {
                    // 브라우저와 동일하게 ',' 등을 그대로 보냄(인코딩하지 않음)
                    url.append("&cursor=").append(cursor);
                }

                log.debug("[ENCAR] 목록 요청: {}", url);

                try {
                    Object any = getJsonAny(url.toString());
                    if (!(any instanceof Map)) {
                        log.warn("[ENCAR] 목록 응답이 Map 아님 → 종료");
                        break;
                    }
                    Map<String, Object> obj = (Map<String, Object>) any;

                    List<Map<String, Object>> list = (List<Map<String, Object>>) Optional
                            .ofNullable(obj.get("SearchResults"))
                            .orElse(List.of());

                    Map<String, Object> paging = (Map<String, Object>) Optional
                            .ofNullable(obj.get("paging"))
                            .orElse(obj.get("Paging"));

                    String nextCursor = "";
                    if (paging != null && paging.get("next") != null) {
                        nextCursor = String.valueOf(paging.get("next")).trim();
                    }

                    if (list.isEmpty()) {
                        log.info("[ENCAR] SearchResults 비어있음 → 종료");
                        break;
                    }

                    log.info("[ENCAR] 목록 batch={} 누적={} nextCursor={}",
                            list.size(), totalFetched + list.size(), nextCursor.isBlank() ? "없음" : nextCursor);

                    int inserted = handleDetails(list);
                    totalFetched += inserted;

                    // 종료 조건
                    if (list.size() < PAGE_SIZE || (nextCursor.isBlank() && !cursor.isBlank())) {
                        log.warn("[ENCAR] 마지막 페이지 추정 → 종료");
                        break;
                    }
                    if (!nextCursor.isBlank() && nextCursor.equals(cursor)) {
                        log.warn("[ENCAR] nextCursor 동일 → 종료");
                        break;
                    }

                    if (!nextCursor.isBlank()) cursor = nextCursor;
                    offset += PAGE_SIZE;

                    Thread.sleep(500);
                    tryCount = 0;
                } catch (Exception e) {
                    log.warn("[ENCAR] 목록 오류: {}", e.toString());
                    if (++tryCount > 5) {
                        log.warn("[ENCAR] 재시도 한도 초과 → 종료");
                        break;
                    }
                    Thread.sleep(1000L * tryCount);
                }
            }

            recorder.recordEnd(runId, totalFetched, Instant.now());
        } catch (Exception e) {
            recorder.recordFail(runId, totalFetched, Instant.now(), e.toString());
            log.error("[ENCAR] runOnce 실패", e);
        }
    }

    private int handleDetails(List<Map<String, Object>> list) throws Exception {
        int inserted = 0;
        for (int i = 0; i < list.size(); i += 20) {
            List<Map<String, Object>> slice = list.subList(i, Math.min(i + 20, list.size()));
            List<String> ids = new ArrayList<>();
            for (Map<String, Object> item : slice) {
                Object id = item.get("Id");
                if (id != null) ids.add(String.valueOf(id));
            }
            if (ids.isEmpty()) continue;

            String detailUrl = "https://api.encar.com/v1/readside/vehicles/view?vehicleIds=" + String.join(",", ids);
            try {
                Object any = getJsonAny(detailUrl);
                List<Map<String, Object>> vehicles;
                if (any instanceof List) {
                    vehicles = (List<Map<String, Object>>) any;
                } else {
                    Map<String, Object> obj = (Map<String, Object>) any;
                    Object v = obj.getOrDefault("Vehicles", obj.get("vehicles"));
                    vehicles = (v instanceof List) ? (List<Map<String, Object>>) v : List.of();
                }

                if (!vehicles.isEmpty()) {
                    String sql = "INSERT INTO raw_encar(payload) VALUES (CAST(? AS JSON)) " +
                            "ON DUPLICATE KEY UPDATE payload=VALUES(payload), fetched_at=CURRENT_TIMESTAMP";
                    List<Object[]> params = new ArrayList<>(vehicles.size());
                    for (Map<String, Object> v : vehicles) {
                        params.add(new Object[]{mapper.writeValueAsString(v)});
                    }
                    int[] res = jdbc.batchUpdate(sql, params);
                    inserted += res.length;
                }
            } catch (Exception ex) {
                log.warn("[ENCAR] 상세 오류 ids={} err={}", ids, ex.toString());
            }
            Thread.sleep(250);
        }
        return inserted;
    }

    /**
     * 브라우저와 동일한 헤더로 GET → JSON 파싱.
     * 403/429(차단/레이트리밋) 시 UA를 바꿔가며 재시도(backoff 포함).
     */
    private Object getJsonAny(String url) throws Exception {
        final int MAX_TRY = 4;
        final long BACKOFF_MS = 900L;
        Exception last = null;

        for (int attempt = 1; attempt <= MAX_TRY; attempt++) {
            String ua = nextUA();
            Request req = buildBrowserLikeGet(url, ua);

            try (Response resp = http.newCall(req).execute()) {
                int code = resp.code();
                String ctype = Optional.ofNullable(resp.header("Content-Type")).orElse("").toLowerCase(Locale.ROOT);
                byte[] body = resp.body() != null ? resp.body().bytes() : new byte[0];

                if (code == 200 && ctype.contains("application/json")) {
                    var root = mapper.readTree(body);
                    if (root.isArray()) {
                        return mapper.convertValue(root, new TypeReference<List<Map<String, Object>>>() {});
                    } else if (root.isObject()) {
                        return mapper.convertValue(root, new TypeReference<Map<String, Object>>() {});
                    } else {
                        return root; // 안전하게 원형 반환
                    }
                }

                String peek = new String(body, 0, Math.min(body.length, 300), StandardCharsets.UTF_8);

                if (code == 403 || code == 429) {
                    log.warn("[ENCAR] {} 차단/리밋 attempt={} ua={} peek={}", code, attempt, ua, peek);
                    last = new IllegalStateException("HTTP " + code);
                    Thread.sleep(BACKOFF_MS * attempt); // 지수 백오프
                    continue; // UA 바꿔 재시도
                }

                // 그 외 에러는 즉시 실패(원하면 재시도 그룹에 넣어도 됨)
                throw new IllegalStateException("HTTP " + code + " type=" + ctype + " peek=" + peek);

            } catch (Exception e) {
                last = e;
                log.warn("[ENCAR] fetch 오류 attempt={} ua={} err={}", attempt, ua, e.toString());
                Thread.sleep(BACKOFF_MS * attempt);
            }
        }
        throw (last != null ? last : new IllegalStateException("fetch failed"));
    }
}

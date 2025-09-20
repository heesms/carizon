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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class EncarCrawler {

    private final JdbcTemplate jdbc;
    private final CrawlRunRecorder recorder;
    private final ObjectMapper mapper = new ObjectMapper();

    private final OkHttpClient http = new OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)   // 시스템 프록시 무시
            .build();

    private static final int PAGE_SIZE = 200;

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
                    String enc = URLEncoder.encode(cursor, StandardCharsets.UTF_8);
                    url.append("&cursor=").append(enc);
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

                    // 종료 조건 (JS와 동일)
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

    private Object getJsonAny(String url) throws Exception {
        Request req = new Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .addHeader("Referer", "https://carencar.com")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36")
                .build();

        try (Response resp = http.newCall(req).execute()) {
            int code = resp.code();
            String ctype = String.valueOf(resp.header("Content-Type"));
            byte[] body = resp.body() != null ? resp.body().bytes() : new byte[0];

            if (code != 200) {
                String peek = new String(body, 0, Math.min(body.length, 200));
                throw new IllegalStateException("HTTP " + code + " body=" + peek);
            }
            if (!ctype.contains("application/json")) {
                String peek = new String(body, 0, Math.min(body.length, 200));
                throw new IllegalStateException("NOT_JSON type=" + ctype + " peek=" + peek);
            }

            var root = mapper.readTree(body);
            if (root.isArray()) {
                return mapper.convertValue(root, new TypeReference<List<Map<String, Object>>>() {});
            } else if (root.isObject()) {
                return mapper.convertValue(root, new TypeReference<Map<String, Object>>() {});
            } else {
                throw new IllegalStateException("Unexpected JSON root=" + root.getNodeType());
            }
        }
    }
}
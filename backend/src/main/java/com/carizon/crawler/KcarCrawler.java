package com.carizon.crawler;

import com.carizon.batch.CrawlRunRecorder;
import com.carizon.common.KcarCrypto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class KcarCrawler {

    private final JdbcTemplate jdbc;
    private final CrawlRunRecorder recorder;
    private final ObjectMapper mapper = new ObjectMapper();

    // 프록시 강제 미사용 (필요 시 교체)
    private final OkHttpClient http = new OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .build();

    private static final int LIMIT = 30; // KCar 기본 페이지 크기
    private static final String URL = "https://mapi.kcar.com/bc/search/list/drct";

    public void runOnceFull() {
        Instant started = Instant.now();
        String runId = recorder.recordStart("KCAR", started);
        int totalInserted = 0;

        try {
            // 스냅샷 전략: 싹 비우고 시작 (원하면 upsert-only로 바꿔도 됨)
            jdbc.update("TRUNCATE TABLE raw_kcar");
            log.info("[KCAR] TRUNCATE raw_kcar 완료");

            int page = 1;
            int emptyCount = 0;

            while (true) {
                String paramJson = String.format(Locale.ROOT,
                        "{\"pageno\":%d,\"limit\":%d,\"orderFlag\":true," +
                                "\"orderBy\":\"time_deal_yn:desc|time_deal_end_dt:asc|event_ordr:asc\"," +
                                "\"wr_in_multi_columns\":\"cntr_rgn_cd|cntr_cd\"}",
                        page, LIMIT);

                String enc;
                try {
                    enc = KcarCrypto.encrypt(paramJson);
                } catch (Exception e) {
                    log.warn("[KCAR] 암호화 실패 page={} err={}", page, e.toString());
                    break;
                }

                String bodyJson = "{\"enc\":\"" + enc + "\"}";
                Request req = new Request.Builder()
                        .url(URL)
                        .post(RequestBody.create(bodyJson, MediaType.parse("application/json")))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json, text/plain, */*")
                        .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                        .header("Origin", "https://m.kcar.com")
                        .header("Referer", "https://m.kcar.com/")
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) " +
                                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36")
                        .build();

                try (Response resp = http.newCall(req).execute()) {
                    int code = resp.code();
                    String ctype = String.valueOf(resp.header("Content-Type"));
                    byte[] body = resp.body() != null ? resp.body().bytes() : new byte[0];

                    if (code != 200) {
                        String peek = new String(body, 0, Math.min(body.length, 200), StandardCharsets.ISO_8859_1)
                                .replaceAll("\\p{Cntrl}", ".");
                        log.warn("[KCAR] HTTP {} page={} peek={}", code, page, peek);
                        break;
                    }
                    if (!ctype.contains("application/json")) {
                        String peek = new String(body, 0, Math.min(body.length, 200), StandardCharsets.ISO_8859_1)
                                .replaceAll("\\p{Cntrl}", ".");
                        log.warn("[KCAR] NOT_JSON page={} type={} peek={}", page, ctype, peek);
                        break;
                    }

                    Map<String, Object> root = mapper.readValue(body, new TypeReference<>() {});
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) root.getOrDefault("data", Map.of());
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> rows = (List<Map<String, Object>>) data.getOrDefault("rows", List.of());

                    log.info("[KCAR] page={} rows={}", page, rows.size());

                    if (rows.isEmpty()) {
                        emptyCount++;
                        // 두 페이지 연속으로 비면 종료 (안정 종료)
                        if (emptyCount >= 2) {
                            log.info("[KCAR] 연속 빈 페이지 → 종료");
                            break;
                        }
                        page++;
                        Thread.sleep(500);
                        continue;
                    }
                    emptyCount = 0;

                    // UPSERT (car_cd UNIQUE)
                    String sql = "INSERT INTO raw_kcar(payload) VALUES (CAST(? AS JSON)) " +
                            "ON DUPLICATE KEY UPDATE payload=VALUES(payload), fetched_at=CURRENT_TIMESTAMP";
                    List<Object[]> params = new ArrayList<>(rows.size());
                    for (Map<String, Object> r : rows) {
                        params.add(new Object[]{ mapper.writeValueAsString(r) });
                    }
                    int[] res = jdbc.batchUpdate(sql, params);
                    totalInserted += res.length;

                    // 다음 페이지
                    page++;
                    Thread.sleep(350);
                }
            }

            recorder.recordEnd(runId, totalInserted, Instant.now());
        } catch (Exception e) {
            recorder.recordFail(runId, totalInserted, Instant.now(), e.toString());
            log.error("[KCAR] runOnceFull 실패", e);
        }
    }
}

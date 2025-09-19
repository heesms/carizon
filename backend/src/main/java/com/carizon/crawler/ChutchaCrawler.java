package com.carizon.crawler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChutchaCrawler {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient http = new OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .build();

    // ---------- Config ----------
    private static final String LIST_URL = "https://web.chutcha.net/web001/car/getSearchCarList";
    private static final int PAGE_SIZE = 50;          // 고정
    private static final int TOUCH_DELAY_MS = 120;    // 공유페이지 접근 간 딜레이
    private static final int PAGE_DELAY_MS = 250;     // 페이지 간 딜레이

    // ---------- Public entry ----------
    public void runOnceFull() {
        final Instant started = Instant.now();
        final String runId = recordStart("CHUTCHA", started);

        int total = 0;
        try {
            jdbc.update("TRUNCATE TABLE raw_chutcha");
            log.info("[CHUTCHA] TRUNCATE raw_chutcha 완료");

            // 페이지네이션 seed
            String cp = "";      // 첫 요청은 빈값
            String lp = "";      // 첫 요청은 빈값
            String ts = String.valueOf(Instant.now().getEpochSecond());

            // 첫 페이지
            PageResult first = fetchPage(cp, lp, ts);
            if (first == null || first.items.isEmpty()) {
                recordSuccess(runId, total);
                log.info("[CHUTCHA] 결과 0건 → 종료");
                return;
            }
            cp = first.nextCp;   // 다음 요청 cp는 첫 응답의 np
            lp = first.lastLp;   // 다음 요청 lp는 첫 응답의 lp
            total += persistBatchAndTouch(first.items);

            // 다음 페이지 반복
            while (true) {
                if (cp == null || cp.isBlank() || (lp != null && !lp.isBlank() && cp.equals(lp))) {
                    log.info("[CHUTCHA] 페이지 종료조건 도달 cp={}, lp={}", cp, lp);
                    break;
                }

                Thread.sleep(PAGE_DELAY_MS);
                PageResult pr = fetchPage(cp, lp, ts);
                if (pr == null || pr.items.isEmpty()) break;

                total += persistBatchAndTouch(pr.items);
                log.info("[CHUTCHA] 누적 저장 {}건 (cp={}, lp={})", total, cp, lp);

                cp = pr.nextCp;
                lp = pr.lastLp;
            }

            recordSuccess(runId, total);
            log.info("[CHUTCHA] 완료 total={}", total);

        } catch (Exception e) {
            recordFail(runId, total, e.getMessage());
            log.error("[CHUTCHA] runOnceFull 실패", e);
        }
    }

    // ---------- Core fetch ----------
    private PageResult fetchPage(String cp, String lp, String ts) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        // 필터 파라미터들: 빈값 유지
        body.put("brand_id", "");
        body.put("model_id", "");
        body.put("sub_model_id", "");
        body.put("grade_id", "");
        body.put("carType", "");
        body.put("fuel", "");
        body.put("fuel_cc_id", "");
        body.put("transmission", "");
        body.put("price_min", "");
        body.put("price_max", "");
        body.put("mile_min", "");
        body.put("mile_max", "");
        body.put("location", "");
        body.put("color", "");
        body.put("option", "");
        body.put("cpo", "");
        body.put("cpo_id", "");
        body.put("classify", "");
        body.put("import", 0);
        body.put("theme_id", "");
        body.put("year", "");
        body.put("saleType", "");
        // 정렬/페이징
        body.put("sort", "1");
        body.put("page_size", String.valueOf(PAGE_SIZE));
        body.put("cp", cp == null ? "" : cp);
        body.put("lp", lp == null ? "" : lp);
        body.put("ts", ts == null ? "" : ts);

        String json = mapper.writeValueAsString(body);

        Request req = new Request.Builder()
                .url(LIST_URL)
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .build();

        try (Response resp = http.newCall(req).execute()) {
            int code = resp.code();
            byte[] bytes = resp.body() != null ? resp.body().bytes() : new byte[0];
            if (code != 200) {
                String peek = new String(bytes, 0, Math.min(bytes.length, 160), StandardCharsets.ISO_8859_1)
                        .replaceAll("\\p{Cntrl}", ".");
                throw new IllegalStateException("HTTP " + code + " url=" + LIST_URL + " peek=" + peek);
            }

            Map<String, Object> root = mapper.readValue(bytes, new TypeReference<>() {});
            if (!"200".equals(String.valueOf(root.get("code")))) {
                throw new IllegalStateException("API code != 200 : " + root.get("code"));
            }

            Map<String, Object> data = (Map<String, Object>) root.get("data");
            Map<String, Object> pageInfo = (Map<String, Object>) data.get("page_info");
            String np = optStr(pageInfo, "np");
            String newLp = optStr(pageInfo, "lp");

            Map<String, Object> listMap = (Map<String, Object>) data.get("list");
            List<Map<String, Object>> items = new ArrayList<>();
            if (listMap != null) {
                for (Object v : listMap.values()) {
                    if (v instanceof List) {
                        //noinspection unchecked
                        items.addAll((List<Map<String, Object>>) v);
                    }
                }
            }

            log.debug("[CHUTCHA] page fetched: items={}, np={}, lp={}", items.size(), np, newLp);
            return new PageResult(items, np, newLp);
        }
    }

    // ---------- Persist & touch ----------
    private int persistBatchAndTouch(List<Map<String, Object>> items) throws Exception {
        int cnt = 0;
        for (Map<String, Object> car : items) {
            String payload = mapper.writeValueAsString(car);

            String hash = optStr(car, "detail_link_hash");
            if (hash == null) hash = optStr(car, "detailLinkHash");

            if (hash != null && !hash.isBlank()) {
                // payload + 해시 저장 (UPSERT)
                jdbc.update(
                        "INSERT INTO raw_chutcha(payload, share_hash) VALUES (CAST(? AS JSON), ?) " +
                                "ON DUPLICATE KEY UPDATE payload=VALUES(payload)",
                        payload, hash
                );

                // 공유 상세 페이지 터치 → __NEXT_DATA__ 파싱으로 번호판 등 보강
                ShareParsed parsed = fetchFromSharePage(hash);
                if (parsed != null) {
                    // 기본: number_plate만 업데이트
                    jdbc.update("UPDATE raw_chutcha SET number_plate=? WHERE share_hash=?",
                            parsed.numberPlate, hash);

                    // 선택: detail_keys / car_id 칼럼이 있다면 함께 업데이트 (없으면 무시)
                    try {
                        jdbc.update("UPDATE raw_chutcha SET detail_keys=?, car_id=? WHERE share_hash=?",
                                parsed.keys, parsed.carId, hash);
                    } catch (Exception ignore) { /* 칼럼 없으면 무시 */ }
                }
                Thread.sleep(TOUCH_DELAY_MS);
            } else {
                jdbc.update("INSERT INTO raw_chutcha(payload) VALUES (CAST(? AS JSON))", payload);
            }
            cnt++;
        }
        return cnt;
    }

    // ---------- Share page touch (parse __NEXT_DATA__) ----------
    private ShareParsed fetchFromSharePage(String hash) {
        String url = "https://www.chutcha.net/share/car/detail/" + hash;
        Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Referer", "https://web.chutcha.net/")
                .build();

        try (Response resp = http.newCall(req).execute()) {
            if (resp.code() != 200) {
                log.warn("[CHUTCHA] share touch HTTP {} url={}", resp.code(), url);
                return null;
            }
            byte[] body = resp.body() != null ? resp.body().bytes() : new byte[0];
            String html = new String(body, StandardCharsets.UTF_8);

            Document doc = Jsoup.parse(html);
            Element next = doc.selectFirst("script#__NEXT_DATA__");
            if (next == null) {
                log.warn("[CHUTCHA] __NEXT_DATA__ not found hash={}", hash);
                return null;
            }

            JsonNode root = mapper.readTree(next.data());

            // keys (pageProps.keys 또는 pageProps.qs.keys)
            String keys = textOrNull(root.at("/props/pageProps/keys"));
            if (keys == null || keys.isEmpty()) {
                keys = textOrNull(root.at("/props/pageProps/qs/keys"));
            }

            // number_plate & car_id
            String plate = null;
            String carId = null;
            JsonNode queries = root.at("/props/pageProps/dehydratedState/queries");
            if (queries.isArray()) {
                for (JsonNode q : queries) {
                    JsonNode baseInfo = q.at("/state/data/base_info");
                    if (!baseInfo.isMissingNode()) {
                        String np = textOrNull(baseInfo.get("number_plate"));
                        if (np != null && !np.isEmpty()) {
                            plate = np;
                            carId = textOrNull(baseInfo.get("car_id"));
                            break;
                        }
                    }
                }
            }

            if (plate == null) {
                // fallback: DOM에 노출된 번호판 (구조 바뀔 때 대비)
                Element plateEl = doc.selectFirst(".car_platenumber_wrap .txt");
                if (plateEl != null && !plateEl.text().isBlank()) {
                    plate = plateEl.text().trim();
                }
            }

            if (plate == null) {
                log.debug("[CHUTCHA] plate not found in NEXT_DATA hash={}", hash);
                return null;
            }

            return new ShareParsed(plate, keys, carId);

        } catch (Exception e) {
            log.warn("[CHUTCHA] share parse fail hash={} msg={}", hash, e.toString());
            return null;
        }
    }

    // ---------- Crawl-run logging ----------
    private String recordStart(String source, Instant startedAt) {
        String runId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO crawl_run(run_id, source, started_at, status, total_items) VALUES (?,?,?,?,?)",
                runId, source, Timestamp.from(startedAt), "STARTED", 0);
        log.info("[CRAWL-RUN] start runId={} source={} started={}", runId, source, startedAt);
        return runId;
    }

    private void recordSuccess(String runId, int total) {
        jdbc.update("UPDATE crawl_run SET ended_at=?, total_items=?, status=? WHERE run_id=?",
                Timestamp.from(Instant.now()), total, "SUCCESS", runId);
    }

    private void recordFail(String runId, int total, String message) {
        jdbc.update("UPDATE crawl_run SET ended_at=?, total_items=?, status=?, message=? WHERE run_id=?",
                Timestamp.from(Instant.now()), total, "FAIL", cut(message, 1000), runId);
    }

    // ---------- Utils ----------
    private static String optStr(Map<String, ?> m, String key) {
        if (m == null) return null;
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static String textOrNull(JsonNode n) {
        return (n == null || n.isMissingNode() || n.isNull()) ? null : n.asText();
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private record PageResult(List<Map<String, Object>> items, String nextCp, String lastLp) {}
    private record ShareParsed(String numberPlate, String keys, String carId) {}
}

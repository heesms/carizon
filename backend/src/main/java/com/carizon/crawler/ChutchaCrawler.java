package com.carizon.crawler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChutchaCrawler {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    // OkHttp: 커넥션 풀 + 동시성 튜닝
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectionPool(new ConnectionPool(100, 60, TimeUnit.SECONDS))
            .dispatcher(new Dispatcher(new ThreadPoolExecutor(
                    0, 64, 60L, TimeUnit.SECONDS, new SynchronousQueue<>())))
            .retryOnConnectionFailure(true)
            .build();

    // 상세 병렬 처리 풀 (너무 높이면 차단 위험 — 12~16 추천)
    private final ExecutorService detailPool = Executors.newFixedThreadPool(12);

    // --------------------- CONST ---------------------
    private static final String HOST = "https://web.chutcha.net";
    private static final String SEARCH_PAGE = HOST + "/bmc/search?brandGroup=1&modelTree=%7B%7D&priceRange=0,0&mileage=0,0&year=&saleType=&accident=&fuel=&transmission=&region=&color=&option=&cpo=&theme=&sort=1&carType=";
    private static final String LIST_URL = HOST + "/web001/car/getSearchCarList"; // 너가 준 JSON API
    private static final int PAGE_SIZE = 50;
    private static final int PAGE_DELAY_MS = 200;

    // --------------------- ENTRY ---------------------
    public void runOnceFull() {
        final Instant started = Instant.now();
        final String runId = recordStart("CHUTCHA", started);

        int total = 0;
        try {
            jdbc.update("TRUNCATE TABLE raw_chutcha");
            log.info("[CHUTCHA] TRUNCATE raw_chutcha 완료");

            // 상세 JSON 호출용 buildId 확보 (1회)
            String buildId = fetchBuildId();
            log.info("[CHUTCHA] buildId={}", buildId);

            String cp = "";  // next 요청에 사용될 cp
            String lp = "";  // next 요청에 사용될 lp
            String ts = String.valueOf(Instant.now().getEpochSecond());

            // 첫 페이지
            PageResult pr = fetchPage(cp, lp, ts);
            while (pr != null && !pr.items.isEmpty()) {
                total += persistAndEnrich(buildId, pr.items);
                log.info("[CHUTCHA] 누적 저장 {}건 (np={}, lp={})", total, pr.nextCp, pr.lastLp);

                if (pr.nextCp == null || pr.nextCp.isBlank() || (pr.lastLp != null && pr.nextCp.equals(pr.lastLp))) {
                    log.info("[CHUTCHA] 페이지 종료조건 도달 cp=np={}, lp={}", pr.nextCp, pr.lastLp);
                    break;
                }

                cp = pr.nextCp;
                lp = pr.lastLp;

                Thread.sleep(PAGE_DELAY_MS);
                pr = fetchPage(cp, lp, ts);
            }

            recordSuccess(runId, total);
            log.info("[CHUTCHA] 완료 total={}", total);
        } catch (Exception e) {
            recordFail(runId, total, e.toString());
            log.error("[CHUTCHA] runOnceFull 실패", e);
        } finally {
            detailPool.shutdown();
        }
    }

    // --------------------- LIST FETCH ---------------------
    private PageResult fetchPage(String cp, String lp, String ts) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        // 필터 파라미터: 빈값 유지
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
                String peek = new String(bytes, 0, Math.min(bytes.length, 200), StandardCharsets.ISO_8859_1)
                        .replaceAll("\\p{Cntrl}", ".");
                throw new IllegalStateException("HTTP " + code + " LIST peek=" + peek);
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

            log.debug("[CHUTCHA] LIST fetched: items={}, np={}, lp={}", items.size(), np, newLp);
            return new PageResult(items, np, newLp);
        }
    }

    // --------------------- DETAIL (JSON) + BATCH PERSIST ---------------------
    private int persistAndEnrich(String buildId, List<Map<String, Object>> items) throws Exception {
        // 1) raw payload + hash 선 저장 (UPSERT)
        List<Object[]> rawParams = new ArrayList<>(items.size());
        List<String> hashes = new ArrayList<>(items.size());

        for (Map<String, Object> car : items) {
            String payload = mapper.writeValueAsString(car);
            String hash = optStr(car, "detail_link_hash");
            if (hash == null || hash.isBlank()) {
                hash = optStr(car, "detailLinkHash"); // 혹시 키 이름이 다를 경우 대비
            }

            if (hash != null && !hash.isBlank()) {
                hashes.add(hash);
                rawParams.add(new Object[]{ payload, hash });
            } else {
                // hash가 없으면 일단 payload만 저장
                rawParams.add(new Object[]{ payload, null });
            }
        }

        if (!rawParams.isEmpty()) {
            jdbc.batchUpdate(
                    "INSERT INTO raw_chutcha(payload, share_hash) VALUES (CAST(? AS JSON), ?) " +
                            "ON DUPLICATE KEY UPDATE payload=VALUES(payload)",
                    rawParams
            );
        }

        // 2) 상세 JSON 병렬 조회
        List<CompletableFuture<DetailParsed>> futures = new ArrayList<>();
        for (String hash : hashes) {
            if (hash == null || hash.isBlank()) continue;
            final String h = hash;
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return fetchDetailJson(buildId, h);
                } catch (Exception e) {
                    log.debug("[CHUTCHA] DETAIL fail hash={} {}", h, e.toString());
                    return null;
                }
            }, detailPool));
        }

        // 3) 결과 모아 배치 업데이트
        List<Object[]> updPlate = new ArrayList<>();
        List<Object[]> updCarId = new ArrayList<>();
        for (CompletableFuture<DetailParsed> f : futures) {
            DetailParsed d = f.join();
            if (d == null) continue;
            if (d.numberPlate != null && !d.numberPlate.isBlank()) {
                updPlate.add(new Object[]{ d.numberPlate, d.keys });
            }
            if (d.carId != null && !d.carId.isBlank()) {
                updCarId.add(new Object[]{ d.carId, d.keys });
            }
        }
        if (!updPlate.isEmpty()) {
            jdbc.batchUpdate("UPDATE raw_chutcha SET number_plate=? WHERE share_hash=?", updPlate);
        }
        if (!updCarId.isEmpty()) {
            try {
                jdbc.batchUpdate("UPDATE raw_chutcha SET car_id=? WHERE share_hash=?", updCarId);
            } catch (Exception ignore) { /* 칼럼 없으면 무시 */ }
        }

        return items.size();
    }

    /**
     * 상세 JSON (Next.js 데이터) 직접 호출
     *   GET https://web.chutcha.net/_next/data/{buildId}/bmc/detail/{keys}.json?keys={keys}
     */
    private DetailParsed fetchDetailJson(String buildId, String keys) throws Exception {
        String enc = URLEncoder.encode(keys, StandardCharsets.UTF_8);
        String url = String.format("%s/_next/data/%s/bmc/detail/%s.json?keys=%s", HOST, buildId, enc, enc);

        Request req = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .build();

        try (Response resp = http.newCall(req).execute()) {
            int code = resp.code();
            byte[] bytes = resp.body() != null ? resp.body().bytes() : new byte[0];
            if (code != 200) {
                String peek = new String(bytes, 0, Math.min(bytes.length, 180), StandardCharsets.ISO_8859_1)
                        .replaceAll("\\p{Cntrl}", ".");
                throw new IllegalStateException("HTTP " + code + " DETAIL peek=" + peek);
            }

            JsonNode root = mapper.readTree(bytes);
            // Next 13/14: 루트가 {pageProps: {dehydratedState: {queries: [...]}}}
            JsonNode queries = root.at("/pageProps/dehydratedState/queries"); // (또는 /props/pageProps/..., 빌드에 따라)
            if (queries.isMissingNode()) {
                // fallback (빌드 구조가 약간 다를 수 있음)
                queries = root.at("/props/pageProps/dehydratedState/queries");
            }

            String plate = null;
            String carId = null;
            if (queries.isArray()) {
                for (JsonNode q : queries) {
                    JsonNode base = q.at("/state/data/base_info");
                    if (!base.isMissingNode()) {
                        if (plate == null) plate = text(base.get("number_plate"));
                        if (carId == null) carId = text(base.get("car_id"));
                    }
                }
            }
            return new DetailParsed(keys, plate, carId);
        }
    }

    // --------------------- buildId fetch ---------------------
    private String fetchBuildId() throws Exception {
        Request req = new Request.Builder()
                .url(SEARCH_PAGE)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("User-Agent", "Mozilla/5.0")
                .build();

        try (Response resp = http.newCall(req).execute()) {
            if (resp.code() != 200) throw new IllegalStateException("HTTP " + resp.code());
            String html = new String(resp.body().bytes(), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("\"buildId\"\\s*:\\s*\"([^\"]+)\"").matcher(html);
            if (m.find()) return m.group(1);
            throw new IllegalStateException("buildId not found");
        }
    }

    // --------------------- crawl_run helpers ---------------------
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

    // --------------------- utils ---------------------
    private static String optStr(Map<String, ?> m, String key) {
        if (m == null) return null;
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static String text(JsonNode n) {
        return (n == null || n.isMissingNode() || n.isNull()) ? null : n.asText();
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private record PageResult(List<Map<String, Object>> items, String nextCp, String lastLp) {}
    private record DetailParsed(String keys, String numberPlate, String carId) {}
}

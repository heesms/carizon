package com.carizon.crawler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    private static final String LIST_URL = HOST + "/web001/car/getSearchCarList";
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

            String buildId = fetchBuildId();
            log.info("[CHUTCHA] buildId={}", buildId);

            String cp = "";
            String lp = "";
            String ts = String.valueOf(Instant.now().getEpochSecond());

            PageResult pr = fetchPage(cp, lp, ts);
            while (pr != null && !pr.items.isEmpty()) {
                total += persistAndEnrich(buildId, pr.items);
                log.info("[CHUTCHA] 누적 저장 {}건 (np={}, lp={})", total, pr.nextCp, pr.lastLp);

                if (pr.nextCp == null || pr.nextCp.isBlank()
                        || (pr.lastLp != null && pr.nextCp.equals(pr.lastLp))) {
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
        // 필터 파라미터 (빈값 유지)
        body.put("brand_id", ""); body.put("model_id", ""); body.put("sub_model_id", ""); body.put("grade_id", "");
        body.put("carType", ""); body.put("fuel", ""); body.put("fuel_cc_id", ""); body.put("transmission", "");
        body.put("price_min", ""); body.put("price_max", ""); body.put("mile_min", ""); body.put("mile_max", "");
        body.put("location", ""); body.put("color", ""); body.put("option", ""); body.put("cpo", "");
        body.put("cpo_id", ""); body.put("classify", ""); body.put("import", 0); body.put("theme_id", "");
        body.put("year", ""); body.put("saleType", "");
        // 페이징/정렬
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
                throw new IllegalStateException("HTTP " + code + " LIST");
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
            return new PageResult(items, np, newLp);
        }
    }

    // --------------------- DETAIL + SLIM MERGE SAVE ---------------------
    private int persistAndEnrich(String buildId, List<Map<String, Object>> items) throws Exception {
        List<Object[]> batch = new ArrayList<>();

        for (Map<String, Object> car : items) {
            String hash = optStr(car, "detail_link_hash");
            if (hash == null || hash.isBlank()) hash = optStr(car, "detailLinkHash");

            // 목록 JSON
            ObjectNode merged = mapper.valueToTree(car);

            // 상세 JSON에서 필요한 필드만 뽑아 detail에 슬림 구조로 붙인다
            if (hash != null && !hash.isBlank()) {
                try {
                    DetailSlim d = fetchDetailSlim(buildId, hash);
                    if (d != null) {
                        ObjectNode detail = mapper.createObjectNode();
                        if (d.options != null) detail.set("options", d.options);
                        if (d.imgList != null) detail.set("img_list", d.imgList);
                        if (d.baseInfo != null) detail.set("base_info", d.baseInfo);
                        merged.set("detail", detail);
                    }
                } catch (Exception e) {
                    log.warn("[CHUTCHA] DETAIL fetch/parse fail hash={} {}", hash, e.toString());
                }
            }

            String mergedPayload = mapper.writeValueAsString(merged);
            batch.add(new Object[]{ mergedPayload, hash });
        }

        if (!batch.isEmpty()) {
            jdbc.batchUpdate(
                    "INSERT INTO raw_chutcha(payload, share_hash) VALUES (CAST(? AS JSON), ?) " +
                            "ON DUPLICATE KEY UPDATE payload=VALUES(payload)",
                    batch
            );
        }
        return items.size();
    }

    /**
     * 상세 JSON 호출 후,
     * - options (array)
     * - img_list (array)
     * - base_info (선택 필드만)
     * 만을 뽑아 반환.
     */
    private DetailSlim fetchDetailSlim(String buildId, String keys) throws Exception {
        String enc = URLEncoder.encode(keys, StandardCharsets.UTF_8);
        String url = String.format("%s/_next/data/%s/bmc/detail/%s.json?keys=%s",
                HOST, buildId, enc, enc);

        Request req = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .build();

        try (Response resp = http.newCall(req).execute()) {
            int code = resp.code();
            byte[] bytes = resp.body() != null ? resp.body().bytes() : new byte[0];
            if (code != 200) throw new IllegalStateException("HTTP " + code + " DETAIL");

            JsonNode root = mapper.readTree(bytes);

            // 쿼리 배열 위치 탐색 (빌드에 따라 경로 다를 수 있어 두 경로 모두 시도)
            JsonNode queries = root.at("/pageProps/dehydratedState/queries");
            if (queries.isMissingNode() || !queries.isArray()) {
                queries = root.at("/props/pageProps/dehydratedState/queries");
            }
            if (queries.isMissingNode() || !queries.isArray() || queries.size() == 0) {
                return new DetailSlim(null, null, null); // 비어있으면 null들로
            }

            // 첫 번째 성공적으로 data 가진 노드 탐색
            JsonNode dataNode = null;
            for (JsonNode q : queries) {
                JsonNode candidate = q.at("/state/data");
                if (!candidate.isMissingNode()) { dataNode = candidate; break; }
            }
            if (dataNode == null) return new DetailSlim(null, null, null);

            // options / img_list
            ArrayNode options = dataNode.has("options") && dataNode.get("options").isArray()
                    ? (ArrayNode) dataNode.get("options") : null;
            ArrayNode imgList = dataNode.has("img_list") && dataNode.get("img_list").isArray()
                    ? (ArrayNode) dataNode.get("img_list") : null;

            // base_info 슬림 선택
            JsonNode base = dataNode.get("base_info");
            ObjectNode baseSlim = null;
            if (base != null && base.isObject()) {
                baseSlim = mapper.createObjectNode();
                // 필요한 키만 복사
                copyIfPresent(base, baseSlim, "color");
                copyIfPresent(base, baseSlim, "car_id");
                copyIfPresent(base, baseSlim, "car_type");
                copyIfPresent(base, baseSlim, "displacement");
                copyIfPresent(base, baseSlim, "number_plate");
                copyIfPresent(base, baseSlim, "plain_mileage");
                copyIfPresent(base, baseSlim, "first_reg_year");
                copyIfPresent(base, baseSlim, "fuel_name");
                copyIfPresent(base, baseSlim, "brand_name");
                copyIfPresent(base, baseSlim, "model_name");
                copyIfPresent(base, baseSlim, "sub_model_name");
                copyIfPresent(base, baseSlim, "grade_name");
                copyIfPresent(base, baseSlim, "sub_grade_name");
                copyIfPresent(base, baseSlim, "transmission_name");
                copyIfPresent(base, baseSlim, "shop_info_arr"); // object
            }

            return new DetailSlim(options, imgList, baseSlim);
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

    private static void copyIfPresent(JsonNode from, ObjectNode to, String field) {
        JsonNode n = from.get(field);
        if (n != null && !n.isMissingNode() && !n.isNull()) {
            to.set(field, n);
        }
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    // --------------------- record types ---------------------
    private record PageResult(List<Map<String, Object>> items, String nextCp, String lastLp) {}
    private record DetailSlim(ArrayNode options, ArrayNode imgList, ObjectNode baseInfo) {}
}

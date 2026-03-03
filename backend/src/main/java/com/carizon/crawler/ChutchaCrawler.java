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
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    public int runOnceFull() {
        final Instant started = Instant.now();
        final String runId = recordStart("CHUTCHA", started);

        int total = 0;
        try {
            jdbc.update("TRUNCATE TABLE raw_chutcha");
            log.info("[CHUTCHA] TRUNCATE raw_chutcha done");

            String buildId = fetchBuildId();
            log.info("[CHUTCHA] buildId={}", buildId);

            String cp = "";
            String lp = "";
            String ts = String.valueOf(Instant.now().getEpochSecond());

            PageResult pr = fetchPage(cp, lp, ts);
            while (pr != null && !pr.items.isEmpty()) {
                total += persistAndEnrich(buildId, pr.items);
                log.info("[CHUTCHA] saved total {} (np={}, lp={})", total, pr.nextCp, pr.lastLp);

                if (pr.nextCp == null || pr.nextCp.isBlank()
                        || (pr.lastLp != null && pr.nextCp.equals(pr.lastLp))) {
                    log.info("[CHUTCHA] page end condition reached cp=np={}, lp={}", pr.nextCp, pr.lastLp);
                    break;
                }

                cp = pr.nextCp;
                lp = pr.lastLp;

                Thread.sleep(PAGE_DELAY_MS);
                pr = fetchPage(cp, lp, ts);
            }

            recordSuccess(runId, total);
            log.info("[CHUTCHA] done total={}", total);
        } catch (Exception e) {
            recordFail(runId, total, e.toString());
            log.error("[CHUTCHA] runOnceFull failed", e);
        }

        return total;
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

    // --------------------- DETAIL + SLIM MERGE SAVE (상세 병렬 조회) ---------------------
    private int persistAndEnrich(String buildId, List<Map<String, Object>> items) throws Exception {
        // 상세 조회를 병렬로 실행 (detailPool)
        @SuppressWarnings("unchecked")
        List<CompletableFuture<DetailResult>> futures = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            final int idx = i;
            Map<String, Object> car = items.get(i);
            String hash = optStr(car, "detail_link_hash");
            if (hash == null || hash.isBlank()) hash = optStr(car, "detailLinkHash");
            final String finalHash = hash;

            if (finalHash != null && !finalHash.isBlank()) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        DetailSlim d = fetchDetailSlim(buildId, finalHash);
                        return new DetailResult(idx, d);
                    } catch (Exception e) {
                        log.warn("[CHUTCHA] DETAIL fetch/parse fail hash={} {}", finalHash, e.toString());
                        return new DetailResult(idx, null);
                    }
                }, detailPool));
            } else {
                futures.add(CompletableFuture.completedFuture(new DetailResult(idx, null)));
            }
        }

        DetailSlim[] detailsByIndex = new DetailSlim[items.size()];
        for (CompletableFuture<DetailResult> f : futures) {
            try {
                DetailResult r = f.get();
                detailsByIndex[r.index()] = r.detail();
            } catch (InterruptedException ie) {
                // HTTP 클라이언트 단절/스레드 인터럽트가 와도 크롤링 배치는 가능한 범위에서 진행
                log.warn("[CHUTCHA] detail future interrupted, fallback to partial save");
                Thread.interrupted(); // interrupt status clear
            } catch (ExecutionException ee) {
                log.warn("[CHUTCHA] detail future execution failed: {}", ee.getCause() != null ? ee.getCause().toString() : ee.toString());
            }
        }

        List<Object[]> batch = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> car = items.get(i);
            String hash = optStr(car, "detail_link_hash");
            if (hash == null || hash.isBlank()) hash = optStr(car, "detailLinkHash");

            ObjectNode merged = mapper.valueToTree(car);
            DetailSlim d = detailsByIndex[i];
            if (d != null) {
                ObjectNode detail = mapper.createObjectNode();
                if (d.options != null) detail.set("options", d.options);
                if (d.imgList != null) detail.set("img_list", d.imgList);
                if (d.baseInfo != null) detail.set("base_info", d.baseInfo);
                merged.set("detail", detail);
            }

            String mergedPayload = mapper.writeValueAsString(merged);
            Map<String, Object> mergedMap = mapper.convertValue(merged, new TypeReference<Map<String, Object>>() {});
            String carImageUrl = buildChutchaImageUrl(mergedMap);
            String optionArray = buildChutchaOptionArray(mergedMap);
            Timestamp adDate = resolveChutchaAdDate(mergedMap);
            batch.add(new Object[]{ mergedPayload, hash, carImageUrl, optionArray, adDate, Timestamp.from(Instant.now()) });
        }

        if (!batch.isEmpty()) {
            jdbc.batchUpdate(
                    "INSERT INTO raw_chutcha(payload, share_hash, car_image_url, option_array, ad_date, fetched_at) VALUES (CAST(? AS JSON), ?, ?, ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE payload=VALUES(payload), car_image_url=VALUES(car_image_url), option_array=VALUES(option_array), ad_date=VALUES(ad_date), fetched_at=VALUES(fetched_at)",
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

    /** during_date_str(예: "3일전", "오늘")를 실제 ad_date(00:00:00)로 변환 */
    private Timestamp resolveChutchaAdDate(Map<String, Object> payload) {
        String raw = optStr(payload, "during_date_str");
        if (raw == null || raw.isBlank()) raw = optStr(payload, "duringDateStr");
        if (raw == null || raw.isBlank()) return null;

        String compact = raw.replaceAll("\\s+", "");
        LocalDate base = LocalDate.now();
        LocalDateTime adDateTime;

        if ("오늘".equals(compact) || "금일".equals(compact)) {
            adDateTime = LocalDateTime.of(base, java.time.LocalTime.MIDNIGHT);
        } else if ("어제".equals(compact)) {
            adDateTime = LocalDateTime.of(base.minusDays(1), java.time.LocalTime.MIDNIGHT);
        } else if (compact.matches("^\\d+시간전$")) {
            long hours = Long.parseLong(compact.replace("시간전", ""));
            // 요구사항: 오늘 00시를 기준으로 N시간 전 계산
            adDateTime = LocalDateTime.of(base, java.time.LocalTime.MIDNIGHT).minusHours(hours);
        } else {
            Matcher m = Pattern.compile("(\\d+)일전").matcher(compact);
            if (!m.find()) return null;
            long days = Long.parseLong(m.group(1));
            adDateTime = LocalDateTime.of(base.minusDays(days), java.time.LocalTime.MIDNIGHT);
        }
        return Timestamp.valueOf(adDateTime);
    }

    private static final String CHUTCHA_IMG_BASE = "https://img.chutcha.kr";

    /**
     * CHUTCHA car_image_url 생성
     * 1) payload.detail.img_list[0].img_path 우선 사용 (상세 첫 번째 이미지)
     * 2) 없으면 list_img_path 사용 (목록 썸네일)
     * URL 형식: https://img.chutcha.kr/files/car_resist/202512/04/xxx.jpg (앞 / 제거 후 베이스 붙임)
     */
    private String buildChutchaImageUrl(Map<String, Object> payload) {
        try {
            String path = getFirstImgPathFromDetail(payload);
            if (path == null) path = optStr(payload, "list_img_path");
            if (path == null || path.isBlank()) return null;

            if (path.startsWith("/")) path = path.substring(1);
            return CHUTCHA_IMG_BASE + "/" + path;
        } catch (Exception e) {
            log.warn("[CHUTCHA] car_image_url build failed: {}", e.getMessage());
            return null;
        }
    }

    /** payload.detail.img_list[0].img_path 추출 */
    @SuppressWarnings("unchecked")
    private String getFirstImgPathFromDetail(Map<String, Object> payload) {
        Object detailObj = payload != null ? payload.get("detail") : null;
        if (!(detailObj instanceof Map)) return null;
        Map<String, Object> detail = (Map<String, Object>) detailObj;
        Object imgListObj = detail.get("img_list");
        if (!(imgListObj instanceof List) || ((List<?>) imgListObj).isEmpty()) return null;
        Object first = ((List<?>) imgListObj).get(0);
        if (!(first instanceof Map)) return null;
        return optStr((Map<String, ?>) first, "img_path");
    }

    /** payload.detail.options에서 val=Y 항목의 kor_name을 "|" join */
    @SuppressWarnings("unchecked")
    private String buildChutchaOptionArray(Map<String, Object> payload) {
        try {
            Object detailObj = payload != null ? payload.get("detail") : null;
            if (!(detailObj instanceof Map)) return null;
            Map<String, Object> detail = (Map<String, Object>) detailObj;

            Object optionsObj = detail.get("options");
            if (!(optionsObj instanceof List<?> options)) return null;

            LinkedHashSet<String> names = new LinkedHashSet<>();
            for (Object item : options) {
                if (!(item instanceof Map<?, ?> option)) continue;

                Object val = option.get("val");
                if (!isPositiveOptionValue(val)) continue;

                String korName = null;
                Object korNameObj = option.get("kor_name");
                if (korNameObj != null) korName = String.valueOf(korNameObj).trim();
                if (korName == null || korName.isBlank()) {
                    Object korNameCamel = option.get("korName");
                    if (korNameCamel != null) korName = String.valueOf(korNameCamel).trim();
                }
                if (korName != null && !korName.isBlank()) names.add(korName);
            }

            if (names.isEmpty()) return null;
            return String.join("|", names);
        } catch (Exception e) {
            log.warn("[CHUTCHA] option_array build failed: {}", e.getMessage());
            return null;
        }
    }

    private boolean isPositiveOptionValue(Object val) {
        if (val == null) return false;
        String s = String.valueOf(val).trim();
        if (s.isEmpty()) return false;
        return "Y".equalsIgnoreCase(s) || "1".equals(s) || "TRUE".equalsIgnoreCase(s);
    }

    // --------------------- record types ---------------------
    private record PageResult(List<Map<String, Object>> items, String nextCp, String lastLp) {}
    private record DetailSlim(ArrayNode options, ArrayNode imgList, ObjectNode baseInfo) {}
    private record DetailResult(int index, DetailSlim detail) {}
}

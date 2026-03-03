package com.carizon.crawler;

import com.carizon.batch.CrawlRunRecorder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.net.Proxy;
import java.net.URLEncoder;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class EncarTruckCrawler {

    private final JdbcTemplate jdbc;
    private final CrawlRunRecorder recorder;
    private final ObjectMapper mapper = new ObjectMapper();
    @Value("${crawler.encar.headers.origin:https://car.encar.com}")
    private String encarOrigin;
    @Value("${crawler.encar.headers.list-referer:https://car.encar.com/}")
    private String encarListReferer;
    @Value("${crawler.encar.headers.readside-referer:https://car.encar.com/}")
    private String encarReadsideReferer;
    @Value("${crawler.encar.headers.cookie:}")
    private String encarCookie;
    @Value("${crawler.encar.truck.list-url-template:https://api.encar.com/search/truck/list/mobile?count=true&q=Hidden.N.&sr=%7CMobileModifiedDate%7C{offset}%7C{size}&inav=%7CMetadata%7CSort}")
    private String truckListUrlTemplate;
    @Value("${crawler.encar.truck.fallback-list-url-template:https://api.encar.com/search/car/list/mobile?count=true&q=(And.Hidden.N._.CarType.B.)&sr=%7CMobilePriceAsc%7C{offset}%7C{size}&inav=%7CMetadata%7CSort}")
    private String truckFallbackListUrlTemplate;

    /** IP 변경은 하지 않음(프록시 NO). */
    private final OkHttpClient http = new OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    private static final int PAGE_SIZE = 200;
    /** 오늘 추가한 ENCAR 부가 API(옵션/사고이력) 임시 비활성화 토글 */
    private static final boolean ENABLE_ENCAR_EXTRA_APIS = true;
    /** true면 기본 적재 완료 후 추가 API 보강을 별도 단계로 수행 */
    private static final boolean ENRICH_EXTRA_AFTER_BASE = true;
    /** 후처리 보강 배치 크기 */
    private static final int EXTRA_ENRICH_BATCH_SIZE = 600;
    /** 페이지 간 기본 딜레이(ms) */
    private static final long LIST_PAGE_DELAY_MS = 100L;
    /** 상세 API 청크 크기 (vehicleIds 한 번에 요청 개수) */
    private static final int DETAIL_CHUNK = 20;
    /** 상세 요청 병렬 수 (과도하면 429 위험) */
    private static final int DETAIL_PARALLEL = 12;
    /** 부가 API(옵션/사고) 전용 병렬 수: 과도 호출 시 407 방지용 */
    private static final int EXTRA_API_PARALLEL = 6;
    /** 전체 HTTP 호출 최소 간격(ms): 과도한 burst 방지 */
    private static final long MIN_HTTP_INTERVAL_MS = 0L;
    /** 부가 API 호출 최소 간격(ms): 리스트/상세 속도 영향 없이 extra만 제어 */
    private static final long EXTRA_API_INTERVAL_MS = 0L;

    private final ExecutorService detailPool = Executors.newFixedThreadPool(DETAIL_PARALLEL);
    private final ExecutorService extraApiPool = Executors.newFixedThreadPool(EXTRA_API_PARALLEL);

    /** 브라우저 UA 후보들(라운드로빈). */
    private static final List<String> USER_AGENTS = List.of(
            // Windows Chrome / Edge
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36 Edg/141.0.0.0",
            // macOS Chrome
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_7_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36",
            // Android Chrome
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 13; SM-G981B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36",
            // iOS Chrome
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/141.0.0.0 Mobile/15E148 Safari/604.1",
            "Mozilla/5.0 (iPad; CPU OS 18_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/141.0.0.0 Mobile/15E148 Safari/604.1"
    );
    private final AtomicInteger uaIdx = new AtomicInteger(0);
    private volatile Map<String, String> standardOptionCodeNameCache;
    private final Object standardOptionCodeNameLock = new Object();
    private final Object httpThrottleLock = new Object();
    private final Object extraApiThrottleLock = new Object();
    private volatile String selectedTruckListUrlTemplate;
    private long lastHttpRequestAt = 0L;
    private long lastExtraApiRequestAt = 0L;
    private String nextUA() {
        int i = Math.abs(uaIdx.getAndIncrement());
        return USER_AGENTS.get(i % USER_AGENTS.size());
    }

    private boolean isIosUserAgent(String ua) {
        return ua != null && (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iOS"));
    }

    private boolean isChromiumUserAgent(String ua) {
        if (ua == null) return false;
        return ua.contains("Chrome/") || ua.contains("CriOS/") || ua.contains("Edg/") || ua.contains("EdgA/");
    }

    private String extractChromiumMajorVersion(String ua) {
        if (ua == null) return "141";
        List<String> tokens = List.of("EdgA/", "Edg/", "CriOS/", "Chrome/");
        for (String token : tokens) {
            int idx = ua.indexOf(token);
            if (idx < 0) continue;
            int start = idx + token.length();
            int end = start;
            while (end < ua.length() && Character.isDigit(ua.charAt(end))) end++;
            if (end > start) return ua.substring(start, end);
        }
        return "141";
    }

    private String resolveChromiumBrand(String ua) {
        if (ua != null && (ua.contains("Edg/") || ua.contains("EdgA/"))) {
            return "Microsoft Edge";
        }
        return "Google Chrome";
    }

    private String resolveRefererByUrl(String url) {
        if (url != null && url.contains("/v1/readside/")) {
            return encarReadsideReferer;
        }
        return encarListReferer;
    }

    /** 브라우저스러운 헤더 셋(Origin/Referer 중요). */
    private Request buildBrowserLikeGet(String url, String ua) {
        boolean mobile = ua.contains("Mobile") || ua.contains("Android") || ua.contains("iPhone");
        String platform = ua.contains("Android") ? "\"Android\"" : (ua.contains("iPhone") ? "\"iOS\"" : "\"Windows\"");
        Request.Builder b = new Request.Builder()
                .url(url)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .header("Origin", encarOrigin)
                .header("Referer", resolveRefererByUrl(url))
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-site")
                .header("User-Agent", ua);
        if (isChromiumUserAgent(ua) && !isIosUserAgent(ua)) {
            String major = extractChromiumMajorVersion(ua);
            String brand = resolveChromiumBrand(ua);
            b.header("Sec-CH-UA", "\"Chromium\";v=\"" + major + "\", \"Not=A?Brand\";v=\"24\", \"" + brand + "\";v=\"" + major + "\"")
                    .header("Sec-CH-UA-Mobile", mobile ? "?1" : "?0")
                    .header("Sec-CH-UA-Platform", platform);
        }
        if (encarCookie != null && !encarCookie.isBlank()) {
            b.header("Cookie", encarCookie);
        }
        return b.build();
    }

    public int runOnce() {
        Instant started = Instant.now();
        String runId = recorder.recordStart("ENCAR_TRUCK", started);

        int totalFetched = 0;
        String cursor = "";
        int offset = 0;
        int tryCount = 0;

        try {
            jdbc.update("TRUNCATE TABLE raw_encar_truck");
            log.info("[ENCAR_TRUCK] TRUNCATE raw_encar_truck done");

            while (true) {
                try {
                    Map<String, Object> obj = fetchTruckListResponse(offset, cursor);

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
                        log.info("[ENCAR_TRUCK] SearchResults empty, exit");
                        break;
                    }

                    log.info("[ENCAR_TRUCK] list batch={} total={} nextCursor={}",
                            list.size(), totalFetched + list.size(), nextCursor.isBlank() ? "-" : nextCursor);

                    int inserted = handleDetails(list);
                    totalFetched += inserted;

                    // 종료 조건
                    if (list.size() < PAGE_SIZE) {
                        log.warn("[ENCAR_TRUCK] last page (list < PAGE_SIZE), exit");
                        break;
                    }

                    if (!nextCursor.isBlank()) {
                        cursor = nextCursor;
                    }
                    offset += PAGE_SIZE;

                    Thread.sleep(LIST_PAGE_DELAY_MS);
                    tryCount = 0;
                } catch (Exception e) {
                    log.warn("[ENCAR_TRUCK] list error: {}", e.toString());
                    if (++tryCount > 5) {
                        log.warn("[ENCAR_TRUCK] retry limit exceeded, exit");
                        break;
                    }
                    if (isProxyAuthError(e)) {
                        // 407은 보통 일시 차단/프록시 경유 구간에서 발생하므로 충분히 쉬고 재시도
                        long pauseMs = 2000L * tryCount;
                        log.warn("[ENCAR_TRUCK] proxy-auth(407) detected, pause {} ms then retry", pauseMs);
                        Thread.sleep(pauseMs);
                    } else {
                        Thread.sleep(1000L * tryCount);
                    }
                }
            }

            int dedupeUpdated = markUseYnByVehicleNo();
            log.info("[ENCAR_TRUCK] use_yn dedupe done: updatedRows={}", dedupeUpdated);

            if (ENABLE_ENCAR_EXTRA_APIS && ENRICH_EXTRA_AFTER_BASE) {
                try {
                    int enriched = enrichExtrasAfterBase();
                    log.info("[ENCAR_TRUCK] extra enrichment done: updatedRows={}", enriched);
                } catch (Exception enrichEx) {
                    log.warn("[ENCAR_TRUCK] extra enrichment failed but base crawl already completed: {}", enrichEx.toString());
                }
            }

            recorder.recordEnd(runId, totalFetched, Instant.now());
        } catch (Exception e) {
            recorder.recordFail(runId, totalFetched, Instant.now(), e.toString());
            log.error("[ENCAR_TRUCK] runOnce failed", e);
        }

        return totalFetched;
    }

    /**
     * DB 적재 없이 트럭 목록 페이징만 순회하여 건수를 점검한다.
     * @param maxPages null/0 이하면 마지막 페이지까지 전체 순회
     */
    public Map<String, Object> runPagingCountOnly(Integer maxPages) {
        int totalFetched = 0;
        String cursor = "";
        int offset = 0;
        int tryCount = 0;
        int pageNo = 0;
        Integer apiCount = null;
        Instant started = Instant.now();

        while (true) {
            try {
                Map<String, Object> obj = fetchTruckListResponse(offset, cursor);
                if (apiCount == null) {
                    apiCount = toInteger(obj.get("Count"));
                }

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
                    log.info("[ENCAR_TRUCK][PAGING-TEST] SearchResults empty, exit");
                    break;
                }

                pageNo++;
                totalFetched += list.size();
                log.info("[ENCAR_TRUCK][PAGING-TEST] page={} batch={} total={} nextCursor={}",
                        pageNo, list.size(), totalFetched, nextCursor.isBlank() ? "-" : nextCursor);

                if (maxPages != null && maxPages > 0 && pageNo >= maxPages) {
                    log.info("[ENCAR_TRUCK][PAGING-TEST] maxPages reached: {}", maxPages);
                    break;
                }

                if (list.size() < PAGE_SIZE) {
                    log.info("[ENCAR_TRUCK][PAGING-TEST] last page detected, exit");
                    break;
                }

                if (!nextCursor.isBlank()) {
                    cursor = nextCursor;
                }
                offset += PAGE_SIZE;

                Thread.sleep(LIST_PAGE_DELAY_MS);
                tryCount = 0;
            } catch (Exception e) {
                log.warn("[ENCAR_TRUCK][PAGING-TEST] list error: {}", e.toString());
                if (++tryCount > 5) {
                    log.warn("[ENCAR_TRUCK][PAGING-TEST] retry limit exceeded, exit");
                    break;
                }
                try {
                    Thread.sleep(1000L * tryCount);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        long elapsedSec = Math.max(0L, java.time.Duration.between(started, Instant.now()).toSeconds());
        log.info("[ENCAR_TRUCK][PAGING-TEST] done pages={} fetchedByPaging={} apiCount={} elapsedSec={}",
                pageNo, totalFetched, apiCount, elapsedSec);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "ENCAR_TRUCK");
        result.put("pages", pageNo);
        result.put("fetchedByPaging", totalFetched);
        result.put("apiCount", apiCount);
        result.put("elapsedSec", elapsedSec);
        result.put("maxPages", maxPages);
        return result;
    }

    /** 크롤링 없이 raw_encar_truck의 use_yn만 재계산 */
    public Map<String, Object> refreshUseYnOnly() {
        int updatedRows = markUseYnByVehicleNo();
        Integer yCount = jdbc.queryForObject("SELECT COUNT(*) FROM raw_encar_truck WHERE use_yn = 'Y'", Integer.class);
        Integer nCount = jdbc.queryForObject("SELECT COUNT(*) FROM raw_encar_truck WHERE use_yn = 'N'", Integer.class);
        Integer totalCount = jdbc.queryForObject("SELECT COUNT(*) FROM raw_encar_truck", Integer.class);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "ENCAR_TRUCK");
        result.put("updatedRows", updatedRows);
        result.put("useYnYCount", yCount != null ? yCount : 0);
        result.put("useYnNCount", nCount != null ? nCount : 0);
        result.put("totalCount", totalCount != null ? totalCount : 0);
        return result;
    }

    private Map<String, Object> fetchTruckListResponse(int offset, String cursor) throws Exception {
        for (String template : truckListUrlTemplateCandidates()) {
            String resolvedListUrl = template
                    .replace("{offset}", String.valueOf(offset))
                    .replace("{size}", String.valueOf(PAGE_SIZE));
            StringBuilder url = new StringBuilder(resolvedListUrl);
            if (cursor != null && !cursor.isBlank()) {
                url.append("&cursor=").append(URLEncoder.encode(cursor, StandardCharsets.UTF_8));
            }

            log.debug("[ENCAR_TRUCK] list request: {}", url);
            try {
                Object any = getJsonAny(url.toString());
                if (!(any instanceof Map<?, ?> map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) map;
                if (!template.equals(selectedTruckListUrlTemplate)) {
                    selectedTruckListUrlTemplate = template;
                    log.info("[ENCAR_TRUCK] selected list template={}", template);
                }
                return casted;
            } catch (Exception e) {
                if (isNotFoundError(e)) {
                    log.warn("[ENCAR_TRUCK] list endpoint 404, fallback template={}", template);
                    continue;
                }
                throw e;
            }
        }
        throw new IllegalStateException("No available ENCAR_TRUCK list endpoint");
    }

    private List<String> truckListUrlTemplateCandidates() {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (selectedTruckListUrlTemplate != null && !selectedTruckListUrlTemplate.isBlank()) {
            candidates.add(selectedTruckListUrlTemplate);
        }
        if (truckListUrlTemplate != null && !truckListUrlTemplate.isBlank()) {
            candidates.add(truckListUrlTemplate);
        }
        if (truckFallbackListUrlTemplate != null && !truckFallbackListUrlTemplate.isBlank()) {
            candidates.add(truckFallbackListUrlTemplate);
        }
        // 환경별 CarType 차이를 대비한 2차 폴백(C)
        candidates.add("https://api.encar.com/search/car/list/mobile?count=true&q=(And.Hidden.N._.CarType.C.)&sr=%7CMobilePriceAsc%7C{offset}%7C{size}&inav=%7CMetadata%7CSort");
        return new ArrayList<>(candidates);
    }

    /** 상세 API를 청크 단위로 병렬 호출 후 한 번에 INSERT */
    private int handleDetails(List<Map<String, Object>> list) throws Exception {
        boolean enrichInline = ENABLE_ENCAR_EXTRA_APIS && !ENRICH_EXTRA_AFTER_BASE;

        List<List<String>> idChunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += DETAIL_CHUNK) {
            List<Map<String, Object>> slice = list.subList(i, Math.min(i + DETAIL_CHUNK, list.size()));
            List<String> ids = slice.stream()
                    .map(item -> item.get("Id"))
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .toList();
            if (!ids.isEmpty()) idChunks.add(ids);
        }
        if (idChunks.isEmpty()) return 0;

        List<CompletableFuture<List<Map<String, Object>>>> futures = idChunks.stream()
                .map(ids -> CompletableFuture.supplyAsync(() -> fetchDetailChunk(ids), detailPool))
                .toList();

        List<Map<String, Object>> allVehicles = new ArrayList<>();
        for (CompletableFuture<List<Map<String, Object>>> f : futures) {
            try {
                List<Map<String, Object>> vehicles = f.get();
                if (vehicles != null) allVehicles.addAll(vehicles);
            } catch (Exception e) {
                log.warn("[ENCAR_TRUCK] detail chunk collect error: {}", e.toString());
            }
        }
        int requestedIds = idChunks.stream().mapToInt(List::size).sum();
        log.info("[ENCAR_TRUCK] detail fetched vehicles={} requestedIds={}", allVehicles.size(), requestedIds);
        if (allVehicles.isEmpty()) return 0;
        for (Map<String, Object> vehicle : allVehicles) {
            applyTruckDefaults(vehicle);
        }

        Map<String, CompletableFuture<EncarExtraData>> extraFutureByVehicleId = new HashMap<>();
        if (enrichInline) {
            for (Map<String, Object> vehicle : allVehicles) {
                String vehicleId = extractVehicleId(vehicle);
                if (vehicleId == null || vehicleId.isBlank()) continue;
                String vehicleNo = extractVehicleNo(vehicle);
                extraFutureByVehicleId.computeIfAbsent(
                        vehicleId,
                        id -> CompletableFuture.supplyAsync(() -> fetchEncarExtraData(id, vehicleNo, vehicle), extraApiPool)
                );
            }
            log.info("[ENCAR_TRUCK] extra api target(all trucks)={}", extraFutureByVehicleId.size());
        }

        String sql = "INSERT INTO raw_encar_truck(payload, car_image_url, price_new, option_array, sel_option_array, seat_count, " +
                "my_accident_cnt, my_accident_cost, other_accident_cnt, other_accident_cost, owner_change_cnt, car_no_change_cnt, total_loss_cnt, flood_total_loss_cnt, robber_cnt, fetched_at) " +
                "VALUES (CAST(? AS JSON), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL) AS new_row " +
                "ON DUPLICATE KEY UPDATE " +
                "payload=new_row.payload, car_image_url=new_row.car_image_url, price_new=new_row.price_new, " +
                "option_array=new_row.option_array, sel_option_array=new_row.sel_option_array, seat_count=new_row.seat_count, " +
                "my_accident_cnt=new_row.my_accident_cnt, my_accident_cost=new_row.my_accident_cost, other_accident_cnt=new_row.other_accident_cnt, " +
                "other_accident_cost=new_row.other_accident_cost, owner_change_cnt=new_row.owner_change_cnt, car_no_change_cnt=new_row.car_no_change_cnt, " +
                "total_loss_cnt=new_row.total_loss_cnt, flood_total_loss_cnt=new_row.flood_total_loss_cnt, robber_cnt=new_row.robber_cnt, " +
                "fetched_at=NULL";

        int inserted = 0;
        int skipped = 0;
        for (Map<String, Object> vehicle : allVehicles) {
            try {
                String payloadJson = mapper.writeValueAsString(vehicle);
                String carImageUrl = buildEncarImageUrl(vehicle);
                Integer priceNew = extractOriginPrice(vehicle);
                String vehicleId = extractVehicleId(vehicle);

                EncarExtraData resolvedExtra = null;
                if (enrichInline) {
                    if (vehicleId != null && !vehicleId.isBlank()) {
                        CompletableFuture<EncarExtraData> extraFuture = extraFutureByVehicleId.get(vehicleId);
                        if (extraFuture != null) {
                            try {
                                resolvedExtra = extraFuture.get();
                            } catch (Exception e) {
                                log.debug("[ENCAR_TRUCK] extra collect failed vehicleId={} err={}", vehicleId, e.toString());
                            }
                        }
                    }
                }
                final EncarExtraData extra = resolvedExtra;

                jdbc.update(sql, (PreparedStatement ps) -> {
                    ps.setString(1, payloadJson);
                    ps.setString(2, carImageUrl != null ? carImageUrl : "");
                    if (priceNew != null) ps.setInt(3, priceNew);
                    else ps.setNull(3, Types.INTEGER);

                    setNullableVarchar(ps, 4, extra != null ? extra.optionArray() : null);
                    setNullableVarchar(ps, 5, extra != null ? extra.selOptionArray() : null);
                    setNullableInt(ps, 6, extra != null ? extra.seatCount() : null);
                    setNullableInt(ps, 7, extra != null ? extra.myAccidentCnt() : null);
                    setNullableLong(ps, 8, extra != null ? extra.myAccidentCost() : null);
                    setNullableInt(ps, 9, extra != null ? extra.otherAccidentCnt() : null);
                    setNullableLong(ps, 10, extra != null ? extra.otherAccidentCost() : null);
                    setNullableInt(ps, 11, extra != null ? extra.ownerChangeCnt() : null);
                    setNullableInt(ps, 12, extra != null ? extra.carNoChangeCnt() : null);
                    setNullableInt(ps, 13, extra != null ? extra.totalLossCnt() : null);
                    setNullableInt(ps, 14, extra != null ? extra.floodTotalLossCnt() : null);
                    setNullableInt(ps, 15, extra != null ? extra.robberCnt() : null);
                });
                inserted++;
            } catch (Exception e) {
                skipped++;
                log.debug("[ENCAR_TRUCK] detail row skip: {}", e.toString());
            }
        }
        log.info("[ENCAR_TRUCK] detail chunk done: inserted={} rows, skipped={}", inserted, skipped);
        return inserted;
    }

    private int enrichExtrasAfterBase() {
        long cursor = 0L;
        int totalUpdated = 0;
        int totalTargets = 0;

        final String selectSql = """
                SELECT id, vehicle_id, vehicle_no, payload
                FROM raw_encar_truck
                WHERE id > ?
                  AND use_yn = 'Y'
                  AND fetched_at IS NULL
                ORDER BY id ASC
                LIMIT ?
                """;

        final String updateSql = """
                UPDATE raw_encar_truck
                SET option_array = ?, sel_option_array = ?, seat_count = ?,
                    my_accident_cnt = ?, my_accident_cost = ?, other_accident_cnt = ?, other_accident_cost = ?,
                    owner_change_cnt = ?, car_no_change_cnt = ?, total_loss_cnt = ?, flood_total_loss_cnt = ?, robber_cnt = ?,
                    fetched_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;

        while (true) {
            List<ExtraEnrichTarget> targets = jdbc.query(selectSql, (rs, rowNum) -> {
                long id = rs.getLong("id");
                String vehicleId = rs.getString("vehicle_id");
                String vehicleNo = rs.getString("vehicle_no");
                String payloadJson = rs.getString("payload");

                Map<String, Object> payloadMap = Map.of();
                if (payloadJson != null && !payloadJson.isBlank()) {
                    try {
                        payloadMap = mapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() {});
                    } catch (Exception e) {
                        log.debug("[ENCAR_TRUCK] payload parse failed id={} err={}", id, e.toString());
                    }
                }
                return new ExtraEnrichTarget(id, vehicleId, vehicleNo, payloadMap);
            }, cursor, EXTRA_ENRICH_BATCH_SIZE);

            if (targets.isEmpty()) break;
            cursor = targets.get(targets.size() - 1).id();
            totalTargets += targets.size();

            List<CompletableFuture<ExtraEnrichResult>> futures = targets.stream()
                    .map(t -> CompletableFuture.supplyAsync(() -> {
                        EncarExtraData extra = fetchEncarExtraData(t.vehicleId(), t.vehicleNo(), t.payload());
                        return new ExtraEnrichResult(t.id(), extra);
                    }, extraApiPool))
                    .toList();

            List<ExtraEnrichResult> results = new ArrayList<>();
            for (CompletableFuture<ExtraEnrichResult> f : futures) {
                try {
                    results.add(f.get());
                } catch (Exception e) {
                    log.debug("[ENCAR_TRUCK] extra enrichment future failed: {}", e.toString());
                }
            }

            if (results.isEmpty()) {
                log.info("[ENCAR_TRUCK] extra enrichment progress: targets={} results=0 updated=0 lastId={}", targets.size(), cursor);
                continue;
            }

            int[][] updatedBatches = jdbc.batchUpdate(updateSql, results, results.size(), (ps, r) -> {
                EncarExtraData extra = r.extra();
                setNullableVarchar(ps, 1, extra != null ? extra.optionArray() : null);
                setNullableVarchar(ps, 2, extra != null ? extra.selOptionArray() : null);
                setNullableInt(ps, 3, extra != null ? extra.seatCount() : null);
                setNullableInt(ps, 4, extra != null ? extra.myAccidentCnt() : null);
                setNullableLong(ps, 5, extra != null ? extra.myAccidentCost() : null);
                setNullableInt(ps, 6, extra != null ? extra.otherAccidentCnt() : null);
                setNullableLong(ps, 7, extra != null ? extra.otherAccidentCost() : null);
                setNullableInt(ps, 8, extra != null ? extra.ownerChangeCnt() : null);
                setNullableInt(ps, 9, extra != null ? extra.carNoChangeCnt() : null);
                setNullableInt(ps, 10, extra != null ? extra.totalLossCnt() : null);
                setNullableInt(ps, 11, extra != null ? extra.floodTotalLossCnt() : null);
                setNullableInt(ps, 12, extra != null ? extra.robberCnt() : null);
                ps.setLong(13, r.id());
            });
            int updatedRows = 0;
            for (int[] batchResult : updatedBatches) {
                for (int c : batchResult) {
                    if (c > 0) updatedRows += c;
                    else if (c == java.sql.Statement.SUCCESS_NO_INFO) updatedRows += 1;
                }
            }
            totalUpdated += updatedRows;
            log.info("[ENCAR_TRUCK] extra enrichment progress: targets={} results={} updated={} lastId={}",
                    targets.size(), results.size(), updatedRows, cursor);
        }

        log.info("[ENCAR_TRUCK] extra enrichment summary: targets={} updated={}", totalTargets, totalUpdated);
        return totalUpdated;
    }

    /**
     * DB 컬럼 기준 기본값 정규화.
     * - sell_type NULL/'null'/'' -> NORMAL
     * - body_type NULL/'null'/'' -> 화물
     * - model_group_code NULL/'null'/'' -> model_code
     * - model_group_name NULL/'null'/'' -> model_name
     *
     * payload도 같이 보정해서(raw 컬럼이 payload 기반 생성일 때 대비) 추출값을 안정화한다.
     */
    private int normalizeTruckDefaultsInDb() {
        int affected = 0;

        // 1) payload 보정 (생성 컬럼 경로 대비)
        affected += jdbc.update("""
                UPDATE raw_encar_truck
                SET payload = JSON_SET(
                    COALESCE(payload, JSON_OBJECT()),
                    '$.sellType', 'NORMAL',
                    '$.sell_type', 'NORMAL',
                    '$.advertisement.sellType', 'NORMAL',
                    '$.advertisement.sell_type', 'NORMAL'
                )
                WHERE sell_type IS NULL
                   OR TRIM(sell_type) = ''
                   OR LOWER(TRIM(sell_type)) = 'null'
                """);

        affected += jdbc.update("""
                UPDATE raw_encar_truck
                SET payload = JSON_SET(
                    COALESCE(payload, JSON_OBJECT()),
                    '$.bodyType', '화물',
                    '$.body_type', '화물',
                    '$.category.bodyType', '화물',
                    '$.category.body_type', '화물'
                )
                WHERE body_type IS NULL
                   OR TRIM(body_type) = ''
                   OR LOWER(TRIM(body_type)) = 'null'
                """);

        affected += jdbc.update("""
                UPDATE raw_encar_truck
                SET payload = JSON_SET(
                    COALESCE(payload, JSON_OBJECT()),
                    '$.modelGroupCode', model_code,
                    '$.model_group_code', model_code,
                    '$.modelGroup.code', model_code,
                    '$.category.modelGroupCode', model_code,
                    '$.category.modelGroup.code', model_code
                )
                WHERE (model_group_code IS NULL OR TRIM(model_group_code) = '' OR LOWER(TRIM(model_group_code)) = 'null')
                  AND model_code IS NOT NULL
                  AND TRIM(model_code) <> ''
                  AND LOWER(TRIM(model_code)) <> 'null'
                """);

        affected += jdbc.update("""
                UPDATE raw_encar_truck
                SET payload = JSON_SET(
                    COALESCE(payload, JSON_OBJECT()),
                    '$.modelGroupName', model_name,
                    '$.model_group_name', model_name,
                    '$.modelGroup.name', model_name,
                    '$.category.modelGroupName', model_name,
                    '$.category.modelGroup.name', model_name
                )
                WHERE (model_group_name IS NULL OR TRIM(model_group_name) = '' OR LOWER(TRIM(model_group_name)) = 'null')
                  AND model_name IS NOT NULL
                  AND TRIM(model_name) <> ''
                  AND LOWER(TRIM(model_name)) <> 'null'
                """);

        // 2) 컬럼 직접 보정 (raw 컬럼이 일반 컬럼인 경우 즉시 반영)
        affected += jdbc.update("""
                UPDATE raw_encar_truck
                SET sell_type = 'NORMAL'
                WHERE sell_type IS NULL
                   OR TRIM(sell_type) = ''
                   OR LOWER(TRIM(sell_type)) = 'null'
                """);

        affected += jdbc.update("""
                UPDATE raw_encar_truck
                SET body_type = '화물'
                WHERE body_type IS NULL
                   OR TRIM(body_type) = ''
                   OR LOWER(TRIM(body_type)) = 'null'
                """);

        affected += jdbc.update("""
                UPDATE raw_encar_truck
                SET model_group_code = model_code
                WHERE (model_group_code IS NULL OR TRIM(model_group_code) = '' OR LOWER(TRIM(model_group_code)) = 'null')
                  AND model_code IS NOT NULL
                  AND TRIM(model_code) <> ''
                  AND LOWER(TRIM(model_code)) <> 'null'
                """);

        affected += jdbc.update("""
                UPDATE raw_encar_truck
                SET model_group_name = model_name
                WHERE (model_group_name IS NULL OR TRIM(model_group_name) = '' OR LOWER(TRIM(model_group_name)) = 'null')
                  AND model_name IS NOT NULL
                  AND TRIM(model_name) <> ''
                  AND LOWER(TRIM(model_name)) <> 'null'
                """);

        return affected;
    }

    /**
     * vehicle_no 기준으로 대표 1건만 use_yn='Y'로 남기고 나머지는 'N' 처리.
     * 우선순위:
     * 1) adv_status='ADVERTISE' AND sell_type='NORMAL' AND price<>0
     * 2) sell_type='NORMAL'
     * 3) price<>0
     * 4) id DESC
     */
    private int markUseYnByVehicleNo() {
        jdbc.update("UPDATE raw_encar_truck SET use_yn = 'Y'");
        return jdbc.update("""
                UPDATE raw_encar_truck r
                JOIN (
                    SELECT id,
                           ROW_NUMBER() OVER (
                               PARTITION BY vehicle_no
                               ORDER BY
                                 CASE
                                   WHEN COALESCE(adv_status, '') = 'ADVERTISE'
                                    AND COALESCE(sell_type, '') = 'NORMAL'
                                    AND COALESCE(price, 0) <> 0
                                   THEN 1 ELSE 0
                                 END DESC,
                                 CASE WHEN COALESCE(sell_type, '') = 'NORMAL' THEN 1 ELSE 0 END DESC,
                                 CASE WHEN COALESCE(price, 0) <> 0 THEN 1 ELSE 0 END DESC,
                                 id DESC
                           ) AS rn
                    FROM raw_encar_truck
                    WHERE vehicle_no IS NOT NULL
                      AND TRIM(vehicle_no) <> ''
                ) x ON x.id = r.id
                SET r.use_yn = CASE WHEN x.rn = 1 THEN 'Y' ELSE 'N' END
                """);
    }

    /** 한 청크(최대 DETAIL_CHUNK개 ID)에 대한 상세 API 호출. 실패 시 빈 리스트. */
    private List<Map<String, Object>> fetchDetailChunk(List<String> ids) {
        String detailUrl = "https://api.encar.com/v1/readside/vehicles/view?vehicleIds=" + String.join(",", ids);
        try {
            Object any = getJsonAny(detailUrl);
            if (any instanceof List) {
                return (List<Map<String, Object>>) any;
            }
            if (any instanceof Map) {
                Map<String, Object> obj = (Map<String, Object>) any;
                Object v = obj.getOrDefault("Vehicles", obj.get("vehicles"));
                return (v instanceof List) ? (List<Map<String, Object>>) v : List.of();
            }
        } catch (Exception ex) {
            log.warn("[ENCAR_TRUCK] detail error ids={} err={}", ids, ex.toString());
        }
        return List.of();
    }

    private String extractVehicleId(Map<String, Object> vehicle) {
        if (vehicle == null) return null;
        Object id = vehicle.get("id");
        if (id == null) id = vehicle.get("Id");
        if (id == null) id = vehicle.get("vehicleId");
        if (id == null) id = vehicle.get("vehicle_id");
        if (id == null) return null;
        String vehicleId = String.valueOf(id).trim();
        return vehicleId.isBlank() ? null : vehicleId;
    }

    private String extractVehicleNo(Map<String, Object> vehicle) {
        if (vehicle == null) return null;
        Object vehicleNo = vehicle.get("vehicleNo");
        if (vehicleNo == null) vehicleNo = vehicle.get("vehicle_no");
        if (vehicleNo == null) vehicleNo = vehicle.get("VehicleNo");
        if (vehicleNo == null) return null;
        String value = String.valueOf(vehicleNo).trim();
        return value.isBlank() ? null : value;
    }

    private void applyTruckDefaults(Map<String, Object> vehicle) {
        if (vehicle == null) return;

        // 트럭은 무조건 NORMAL 취급
        vehicle.put("sellType", "NORMAL");
        vehicle.put("sell_type", "NORMAL");
        vehicle.put("SellType", "NORMAL");
        vehicle.put("SELL_TYPE", "NORMAL");

        Map<String, Object> advertisement = getOrCreateMap(vehicle, "advertisement", "Advertisement");
        advertisement.put("sellType", "NORMAL");
        advertisement.put("sell_type", "NORMAL");
        advertisement.put("SellType", "NORMAL");
        advertisement.put("SELL_TYPE", "NORMAL");

        Map<String, Object> spec = getOrCreateMap(vehicle, "spec", "Spec");
        Map<String, Object> category = getOrCreateMap(vehicle, "category", "Category");
        Map<String, Object> model = getOrCreateMap(vehicle, "model", "Model");
        Map<String, Object> categoryModel = getOrCreateMap(category, "model", "Model");

        // body_type 생성컬럼 경로: $.spec.bodyName
        String bodyName = trimToNull(firstNonNull(
                spec.get("bodyName"), spec.get("body_name"),
                vehicle.get("bodyType"), vehicle.get("body_type"), vehicle.get("BodyType"), vehicle.get("BODY_TYPE"),
                category.get("bodyName"), category.get("bodyType")
        ));
        if (bodyName == null) bodyName = "화물";
        spec.put("bodyName", bodyName);
        spec.put("body_name", bodyName);

        // model_code 생성컬럼 경로: $.category.modelCd
        String modelCode = trimToNull(firstNonNull(
                category.get("modelCd"), category.get("modelCode"), category.get("model_code"),
                categoryModel.get("code"), categoryModel.get("modelCd"),
                vehicle.get("modelCd"), vehicle.get("modelCode"), vehicle.get("model_code"), vehicle.get("ModelCode"),
                model.get("code"), model.get("modelCd"), model.get("modelCode")
        ));
        if (modelCode != null) {
            category.put("modelCd", modelCode);
            category.put("modelCode", modelCode);
            category.put("model_code", modelCode);
        }

        // model_name 생성컬럼 경로: $.category.modelName
        String modelName = trimToNull(firstNonNull(
                category.get("modelName"), category.get("model_name"),
                categoryModel.get("name"), categoryModel.get("modelName"),
                vehicle.get("modelName"), vehicle.get("model_name"), vehicle.get("ModelName"), vehicle.get("Model"),
                model.get("name"), model.get("modelName")
        ));
        if (modelName != null) {
            category.put("modelName", modelName);
            category.put("model_name", modelName);
        }

        // model_group_code 생성컬럼 경로: $.category.modelGroupCd
        String modelGroupCode = trimToNull(firstNonNull(
                category.get("modelGroupCd"), category.get("modelGroupCode"), category.get("model_group_code")
        ));
        if (modelGroupCode == null) modelGroupCode = modelCode;
        if (modelGroupCode != null) {
            category.put("modelGroupCd", modelGroupCode);
            category.put("modelGroupCode", modelGroupCode);
            category.put("model_group_code", modelGroupCode);
            vehicle.put("modelGroupCode", modelGroupCode);
            vehicle.put("model_group_code", modelGroupCode);
            vehicle.put("ModelGroupCode", modelGroupCode);
        }

        // model_group_name 생성컬럼 경로: $.category.modelGroupName
        String modelGroupName = trimToNull(firstNonNull(
                category.get("modelGroupName"), category.get("model_group_name")
        ));
        if (modelGroupName == null) modelGroupName = modelName;
        if (modelGroupName != null) {
            category.put("modelGroupName", modelGroupName);
            category.put("model_group_name", modelGroupName);
            vehicle.put("modelGroupName", modelGroupName);
            vehicle.put("model_group_name", modelGroupName);
            vehicle.put("ModelGroupName", modelGroupName);
        }
    }

    private Map<String, Object> getOrCreateMap(Map<String, Object> root, String... keys) {
        for (String key : keys) {
            Object v = root.get(key);
            if (v instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    result.put(String.valueOf(e.getKey()), e.getValue());
                }
                root.put(key, result);
                return result;
            }
        }
        Map<String, Object> created = new LinkedHashMap<>();
        root.put(keys[0], created);
        return created;
    }

    private Map<String, String> buildSellTypeByVehicleId(List<Map<String, Object>> list) {
        Map<String, String> result = new HashMap<>();
        if (list == null || list.isEmpty()) return result;
        for (Map<String, Object> item : list) {
            String vehicleId = extractVehicleId(item);
            if (vehicleId == null || vehicleId.isBlank()) continue;
            String sellType = extractSellType(item);
            if (sellType != null && !sellType.isBlank()) {
                result.putIfAbsent(vehicleId, sellType);
            }
        }
        return result;
    }

    private String resolveSellType(String vehicleId, Map<String, Object> detailVehicle, Map<String, String> sellTypeByVehicleId) {
        // 사용자 기준: SELL_TYPE는 vehicles/view 응답이 기준
        String fromView = extractSellType(detailVehicle);
        if (fromView != null && !fromView.isBlank()) return fromView;
        if (vehicleId != null && sellTypeByVehicleId != null) {
            String fromList = sellTypeByVehicleId.get(vehicleId);
            if (fromList != null && !fromList.isBlank()) return fromList;
        }
        return null;
    }

    private String extractSellType(Map<String, Object> vehicle) {
        if (vehicle == null || vehicle.isEmpty()) return null;

        Object direct = firstNonNull(
                vehicle.get("sellType"),
                vehicle.get("sell_type"),
                vehicle.get("SellType"),
                vehicle.get("SELL_TYPE"),
                vehicle.get("advertisementType"),
                vehicle.get("advertisement_type"),
                vehicle.get("AdvertisementType"),
                vehicle.get("ADVERTISEMENT_TYPE"),
                vehicle.get("saleType"),
                vehicle.get("sale_type"),
                vehicle.get("SaleType"),
                vehicle.get("SALE_TYPE"),
                vehicle.get("saleStatus"),
                vehicle.get("sale_status"),
                vehicle.get("SaleStatus"),
                vehicle.get("SALE_STATUS"),
                vehicle.get("status"),
                vehicle.get("STATUS")
        );
        String directValue = trimToNull(direct);
        if (directValue != null) return directValue;

        Object adObj = firstNonNull(vehicle.get("advertisement"), vehicle.get("Advertisement"));
        if (adObj instanceof Map<?, ?> ad) {
            Object nested = firstNonNull(
                    ad.get("sellType"),
                    ad.get("sell_type"),
                    ad.get("SellType"),
                    ad.get("SELL_TYPE"),
                    ad.get("advertisementType"),
                    ad.get("advertisement_type"),
                    ad.get("AdvertisementType"),
                    ad.get("ADVERTISEMENT_TYPE"),
                    ad.get("saleType"),
                    ad.get("sale_type"),
                    ad.get("SaleType"),
                    ad.get("SALE_TYPE"),
                    ad.get("saleStatus"),
                    ad.get("sale_status"),
                    ad.get("SaleStatus"),
                    ad.get("SALE_STATUS"),
                    ad.get("status"),
                    ad.get("STATUS")
            );
            return trimToNull(nested);
        }
        return null;
    }

    private static Object firstNonNull(Object... values) {
        if (values == null) return null;
        for (Object v : values) {
            if (v != null) return v;
        }
        return null;
    }

    private static String trimToNull(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        if (s.isBlank()) return null;
        if ("null".equalsIgnoreCase(s)) return null;
        return s;
    }

    private record EncarExtraData(
            String optionArray,
            String selOptionArray,
            Integer seatCount,
            Integer myAccidentCnt,
            Long myAccidentCost,
            Integer otherAccidentCnt,
            Long otherAccidentCost,
            Integer ownerChangeCnt,
            Integer carNoChangeCnt,
            Integer totalLossCnt,
            Integer floodTotalLossCnt,
            Integer robberCnt
    ) {}

    private record ExtraEnrichTarget(
            long id,
            String vehicleId,
            String vehicleNo,
            Map<String, Object> payload
    ) {}

    private record ExtraEnrichResult(
            long id,
            EncarExtraData extra
    ) {}

    private EncarExtraData fetchEncarExtraData(String vehicleId, String vehicleNo, Map<String, Object> vehicleSummary) {
        try {
            Map<String, Object> baseDetail = extractOptionSpecFromVehicleSummary(vehicleSummary);
            boolean needsOptionSpecFetch = baseDetail.isEmpty() || !hasOptionsContainer(baseDetail) || !hasSeatCountValue(baseDetail);

            // 독립적인 API 호출을 병렬 실행 (extraApiPool 데드락 방지를 위해 commonPool 사용)
            CompletableFuture<Map<String, Object>> detailFuture = needsOptionSpecFetch
                    ? CompletableFuture.supplyAsync(() -> fetchVehicleDetailForOptionAndSpec(vehicleId))
                    : CompletableFuture.completedFuture(Map.of());
            CompletableFuture<Map<String, String>> standardFuture =
                    CompletableFuture.supplyAsync(() -> fetchOptionCodeNameMap(vehicleId, "standard"));
            CompletableFuture<Map<String, String>> choiceFuture =
                    CompletableFuture.supplyAsync(() -> fetchOptionCodeNameMap(vehicleId, "choice"));
            CompletableFuture<Map<String, Object>> openFuture =
                    CompletableFuture.supplyAsync(() -> fetchVehicleOpenRecord(vehicleId, vehicleNo));

            Map<String, Object> detail = needsOptionSpecFetch
                    ? mergeOptionSpec(baseDetail, detailFuture.join())
                    : baseDetail;
            log.debug("[ENCAR_TRUCK][OPTION] vehicleId={} needsOptionSpecFetch={} hasOptions={} hasSeatCount={}",
                    vehicleId, needsOptionSpecFetch, hasOptionsContainer(detail), hasSeatCountValue(detail));
            Map<String, String> standardOptionMap = standardFuture.join();
            Map<String, String> choiceOptionMap = choiceFuture.join();

            List<String> standardCodes = extractOptionCodes(detail, "standard");
            List<String> choiceCodes = extractOptionCodes(detail, "choice");

            String optionArray = mapOptionCodesToNames(standardCodes, standardOptionMap);
            String selOptionArray = mapOptionCodesToNames(choiceCodes, choiceOptionMap);
            if ((selOptionArray == null || selOptionArray.isBlank())
                    && (choiceCodes == null || choiceCodes.isEmpty())
                    && choiceOptionMap != null && !choiceOptionMap.isEmpty()) {
                selOptionArray = joinOptionNames(choiceOptionMap);
                log.debug("[ENCAR_TRUCK][OPTION] vehicleId={} sel_option_array fallback from choice api names count={}",
                        vehicleId, choiceOptionMap.size());
            }
            Integer seatCount = extractSeatCount(detail);
            log.debug("[ENCAR_TRUCK][OPTION] vehicleId={} standardCodes={} choiceCodes={} optionMapped={} selOptionMapped={} seatCount={}",
                    vehicleId,
                    standardCodes != null ? standardCodes.size() : 0,
                    choiceCodes != null ? choiceCodes.size() : 0,
                    optionArray != null && !optionArray.isBlank(),
                    selOptionArray != null && !selOptionArray.isBlank(),
                    seatCount);

            Map<String, Object> open = openFuture.join();
            Integer myAccidentCnt = toInteger(open.get("myAccidentCnt"));
            Long myAccidentCost = toLong(open.get("myAccidentCost"));
            Integer otherAccidentCnt = toInteger(open.get("otherAccidentCnt"));
            Long otherAccidentCost = toLong(open.get("otherAccidentCost"));
            Integer ownerChangeCnt = toInteger(open.get("ownerChangeCnt"));
            Integer carNoChangeCnt = toInteger(open.get("carNoChangeCnt"));
            Integer totalLossCnt = toInteger(open.get("totalLossCnt"));
            Integer floodTotalLossCnt = toInteger(open.get("floodTotalLossCnt"));
            Integer robberCnt = toInteger(open.get("robberCnt"));

            return new EncarExtraData(
                    optionArray,
                    selOptionArray,
                    seatCount,
                    myAccidentCnt,
                    myAccidentCost,
                    otherAccidentCnt,
                    otherAccidentCost,
                    ownerChangeCnt,
                    carNoChangeCnt,
                    totalLossCnt,
                    floodTotalLossCnt,
                    robberCnt
            );
        } catch (Exception e) {
            log.debug("[ENCAR_TRUCK] extra fetch failed vehicleId={} err={}", vehicleId, e.toString());
            return new EncarExtraData(null, null, null, null, null, null, null, null, null, null, null, null);
        }
    }

    private boolean hasOptionsContainer(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) return false;
        return detail.get("options") instanceof Map<?, ?>;
    }

    private boolean hasSeatCountValue(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) return false;
        Object specObj = detail.get("spec");
        if (!(specObj instanceof Map<?, ?> spec)) return false;
        return spec.get("seatCount") != null;
    }

    private Map<String, Object> mergeOptionSpec(Map<String, Object> base, Map<String, Object> fetched) {
        if (base == null || base.isEmpty()) return fetched != null ? fetched : Map.of();
        if (fetched == null || fetched.isEmpty()) return base;

        Map<String, Object> merged = new HashMap<>(base);
        if (!(merged.get("options") instanceof Map<?, ?>) && fetched.get("options") instanceof Map<?, ?>) {
            merged.put("options", fetched.get("options"));
        }
        if (!(merged.get("spec") instanceof Map<?, ?>) && fetched.get("spec") instanceof Map<?, ?>) {
            merged.put("spec", fetched.get("spec"));
        } else if (merged.get("spec") instanceof Map<?, ?> baseSpec && fetched.get("spec") instanceof Map<?, ?> fetchSpec) {
            // summary spec에 seatCount가 없을 때 fetched spec으로 보강
            if (baseSpec.get("seatCount") == null && fetchSpec.get("seatCount") != null) {
                Map<String, Object> specMerged = new HashMap<>();
                for (Map.Entry<?, ?> e : baseSpec.entrySet()) specMerged.put(String.valueOf(e.getKey()), e.getValue());
                specMerged.put("seatCount", fetchSpec.get("seatCount"));
                merged.put("spec", specMerged);
            }
        }
        return merged;
    }

    private Map<String, Object> extractOptionSpecFromVehicleSummary(Map<String, Object> vehicleSummary) {
        if (vehicleSummary == null || vehicleSummary.isEmpty()) return Map.of();
        boolean hasOptions = vehicleSummary.get("options") instanceof Map<?, ?>;
        boolean hasSpec = vehicleSummary.get("spec") instanceof Map<?, ?>;
        if (!hasOptions && !hasSpec) return Map.of();

        Map<String, Object> detail = new HashMap<>();
        if (hasOptions) detail.put("options", vehicleSummary.get("options"));
        if (hasSpec) detail.put("spec", vehicleSummary.get("spec"));
        return detail;
    }

    private Map<String, Object> fetchVehicleDetailForOptionAndSpec(String vehicleId) {
        String detailUrl = "https://api.encar.com/v1/readside/vehicle/" + vehicleId + "?include=OPTIONS,SPEC";
        try {
            Object any = getJsonAnyOnce(detailUrl);
            if (any instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) map;
                return casted;
            }
        } catch (Exception e) {
            log.debug("[ENCAR_TRUCK] option/spec detail fetch failed vehicleId={} err={}", vehicleId, e.toString());
        }
        return Map.of();
    }

    private Map<String, Object> fetchVehicleOpenRecord(String vehicleId, String vehicleNo) {
        if (vehicleNo == null || vehicleNo.isBlank()) return Map.of();
        String encodedVehicleNo = URLEncoder.encode(vehicleNo, StandardCharsets.UTF_8);
        String openUrl = "https://api.encar.com/v1/readside/record/vehicle/" + vehicleId + "/open?vehicleNo=" + encodedVehicleNo;
        try {
            Object any = getJsonAnyOnce(openUrl);
            if (any instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) map;
                return casted;
            }
        } catch (Exception e) {
            if (!isNotFoundError(e)) {
                log.debug("[ENCAR_TRUCK] open record fetch failed vehicleId={} err={}", vehicleId, e.toString());
            }
        }
        return Map.of();
    }

    private Integer extractSeatCount(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) return null;
        Object specObj = detail.get("spec");
        if (!(specObj instanceof Map<?, ?> spec)) return null;
        return toInteger(spec.get("seatCount"));
    }

    private List<String> extractOptionCodes(Map<String, Object> detail, String fieldName) {
        if (detail == null || detail.isEmpty()) return List.of();
        Object optionsObj = detail.get("options");
        if (!(optionsObj instanceof Map<?, ?> options)) return List.of();
        Object codes = options.get(fieldName);
        if (!(codes instanceof List<?> codeList)) return List.of();

        List<String> result = new ArrayList<>();
        for (Object item : codeList) {
            if (item == null) continue;
            String code = String.valueOf(item).trim();
            if (!code.isBlank()) result.add(code);
        }
        return result;
    }

    private String mapOptionCodesToNames(List<String> codes, Map<String, String> codeNameMap) {
        if (codes == null || codes.isEmpty() || codeNameMap == null || codeNameMap.isEmpty()) return null;
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String code : codes) {
            if (code == null || code.isBlank()) continue;
            String name = codeNameMap.get(code);
            if (name != null && !name.isBlank()) names.add(name);
        }
        if (names.isEmpty()) return null;
        return String.join("|", names);
    }

    private String joinOptionNames(Map<String, String> codeNameMap) {
        if (codeNameMap == null || codeNameMap.isEmpty()) return null;
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String name : codeNameMap.values()) {
            if (name == null) continue;
            String v = name.trim();
            if (!v.isBlank()) names.add(v);
        }
        if (names.isEmpty()) return null;
        return String.join("|", names);
    }

    private Map<String, String> fetchOptionCodeNameMap(String vehicleId, String optionType) {
        if ("standard".equalsIgnoreCase(optionType)) {
            Map<String, String> cached = standardOptionCodeNameCache;
            if (cached != null) return cached;
            synchronized (standardOptionCodeNameLock) {
                cached = standardOptionCodeNameCache;
                if (cached != null) return cached;
                Map<String, String> loaded = fetchOptionCodeNameMapByUrls(
                        List.of("https://api.encar.com/v1/readside/vehicles/car/options/standard"),
                        optionType,
                        vehicleId
                );
                standardOptionCodeNameCache = loaded;
                return loaded;
            }
        }

        return fetchOptionCodeNameMapByUrls(
                List.of("https://api.encar.com/v1/readside/vehicles/car/" + vehicleId + "/options/" + optionType),
                optionType,
                vehicleId
        );
    }

    private Map<String, String> fetchOptionCodeNameMapByUrls(List<String> urls, String optionType, String vehicleId) {
        for (String url : urls) {
            try {
                log.debug("[ENCAR_TRUCK][OPTION] request type={} vehicleId={} url={}", optionType, vehicleId, url);
                Object any = getJsonAnyOnce(url);
                Map<String, String> codeNameMap = new LinkedHashMap<>();
                collectOptionCodeNames(any, codeNameMap);
                if (!codeNameMap.isEmpty()) {
                    log.debug("[ENCAR_TRUCK][OPTION] response type={} vehicleId={} mappedCodes={}", optionType, vehicleId, codeNameMap.size());
                    return codeNameMap;
                }
                log.debug("[ENCAR_TRUCK][OPTION] empty map type={} vehicleId={} url={}", optionType, vehicleId, url);
            } catch (Exception e) {
                if (isNotFoundError(e)) {
                    log.debug("[ENCAR_TRUCK][OPTION] 404 type={} vehicleId={} url={}", optionType, vehicleId, url);
                    continue;
                }
                log.debug("[ENCAR_TRUCK] {} option map fetch failed vehicleId={} url={} err={}", optionType, vehicleId, url, e.toString());
            }
        }
        return Map.of();
    }

    private boolean isNotFoundError(Exception e) {
        String msg = e != null ? String.valueOf(e.getMessage()) : "";
        return msg.contains("HTTP 404");
    }

    private boolean isProxyAuthError(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            String msg = String.valueOf(cur.getMessage());
            if (msg.contains("HTTP_PROXY_AUTH") || msg.contains(" 407") || msg.contains("HTTP 407")) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private void waitForHttpSlot() throws InterruptedException {
        if (MIN_HTTP_INTERVAL_MS <= 0L) return;
        synchronized (httpThrottleLock) {
            long now = System.currentTimeMillis();
            long waitMs = MIN_HTTP_INTERVAL_MS - (now - lastHttpRequestAt);
            if (waitMs > 0) Thread.sleep(waitMs);
            lastHttpRequestAt = System.currentTimeMillis();
        }
    }

    private void waitForExtraApiSlot() throws InterruptedException {
        if (EXTRA_API_INTERVAL_MS <= 0L) return;
        synchronized (extraApiThrottleLock) {
            long now = System.currentTimeMillis();
            long waitMs = EXTRA_API_INTERVAL_MS - (now - lastExtraApiRequestAt);
            if (waitMs > 0) Thread.sleep(waitMs);
            lastExtraApiRequestAt = System.currentTimeMillis();
        }
    }

    private void collectOptionCodeNames(Object source, Map<String, String> out) {
        if (source == null) return;

        if (source instanceof List<?> list) {
            for (Object item : list) collectOptionCodeNames(item, out);
            return;
        }
        if (!(source instanceof Map<?, ?> map)) return;

        Object optionCd = map.get("optionCd");
        Object optionName = map.get("optionName");
        if (optionCd != null && optionName != null) {
            String code = String.valueOf(optionCd).trim();
            String name = String.valueOf(optionName).trim();
            if (!code.isBlank() && !name.isBlank()) out.putIfAbsent(code, name);
        }
        for (Object value : map.values()) {
            if (value instanceof Map<?, ?> || value instanceof List<?>) {
                collectOptionCodeNames(value, out);
            }
        }
    }

    private void setNullableVarchar(PreparedStatement ps, int index, String value) throws java.sql.SQLException {
        if (value != null && !value.isBlank()) ps.setString(index, value);
        else ps.setNull(index, Types.VARCHAR);
    }

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws java.sql.SQLException {
        if (value != null) ps.setInt(index, value);
        else ps.setNull(index, Types.INTEGER);
    }

    private void setNullableLong(PreparedStatement ps, int index, Long value) throws java.sql.SQLException {
        if (value != null) ps.setLong(index, value);
        else ps.setNull(index, Types.BIGINT);
    }

    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        String s = String.valueOf(value).trim();
        if (s.isBlank() || "null".equalsIgnoreCase(s)) return null;
        try {
            return Integer.parseInt(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        String s = String.valueOf(value).trim();
        if (s.isBlank() || "null".equalsIgnoreCase(s)) return null;
        try {
            return Long.parseLong(s);
        } catch (Exception ignored) {
            return null;
        }
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
            waitForHttpSlot();
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
                    log.warn("[ENCAR_TRUCK] {} block/limit attempt={} ua={} peek={}", code, attempt, ua, peek);
                    last = new IllegalStateException("HTTP " + code);
                    Thread.sleep(BACKOFF_MS * attempt); // 지수 백오프
                    continue; // UA 바꿔 재시도
                }

                // 그 외 에러는 즉시 실패(원하면 재시도 그룹에 넣어도 됨)
                throw new IllegalStateException("HTTP " + code + " type=" + ctype + " peek=" + peek);

            } catch (Exception e) {
                last = e;
                log.warn("[ENCAR_TRUCK] fetch error attempt={} ua={} err={}", attempt, ua, e.toString());
                if (isProxyAuthError(e)) {
                    Thread.sleep(1000L * attempt);
                } else {
                    Thread.sleep(BACKOFF_MS * attempt);
                }
            }
        }
        throw (last != null ? last : new IllegalStateException("fetch failed"));
    }

    /**
     * 옵션/부가 API용 단건 호출.
     * 404 같은 고정 오류는 재시도하지 않고 즉시 반환해 로그 폭증을 막는다.
     */
    private Object getJsonAnyOnce(String url) throws Exception {
        String ua = nextUA();
        Request req = buildBrowserLikeGet(url, ua);
        waitForExtraApiSlot();
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
                    return root;
                }
            }

            String peek = new String(body, 0, Math.min(body.length, 300), StandardCharsets.UTF_8);
            throw new IllegalStateException("HTTP " + code + " type=" + ctype + " peek=" + peek);
        }
    }

    /** payload의 category.originPrice 추출 (신차가격, 만원 단위) */
    private Integer extractOriginPrice(Map<String, Object> vehicle) {
        try {
            Object cat = vehicle.get("category");
            if (!(cat instanceof Map)) return null;
            Object val = ((Map<?, ?>) cat).get("originPrice");
            if (val == null) return null;
            if (val instanceof Number) return ((Number) val).intValue();
            if (val instanceof String) {
                String s = ((String) val).trim();
                if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
                return Integer.parseInt(s);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * ENCAR car_image_url 생성
     * 규칙: payload의 photos 오브젝트에서 code=001인 path 사용
     * URL 형식: https://ci.encar.com/carpicture/carpicture08/pic4128/41289848_001.jpg?impolicy=heightRate&rh=384&cw=640&ch=384&cg=Center&wtmk=https://ci.encar.com/wt_mark/w_mark_04.png&t=20260106171453
     */
    private String buildEncarImageUrl(Map<String, Object> vehicle) {
        try {
            Object photosObj = vehicle.get("photos");
            if (photosObj == null) return null;
            
            if (photosObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> photos = (List<Map<String, Object>>) photosObj;
                for (Map<String, Object> photo : photos) {
                    Object codeObj = photo.get("code");
                    if (codeObj != null && "001".equals(String.valueOf(codeObj))) {
                        Object pathObj = photo.get("path");
                        if (pathObj != null) {
                            String path = String.valueOf(pathObj);
                            if (path.startsWith("/")) {
                                path = path.substring(1); // 앞의 / 제거
                            }
                            return "https://ci.encar.com/carpicture/" + path + 
                                    "?impolicy=heightRate&rh=384&cw=640&ch=384&cg=Center&wtmk=https://ci.encar.com/wt_mark/w_mark_04.png&t=" + 
                                    System.currentTimeMillis();
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("[ENCAR_TRUCK] car_image_url build fail: {}", e.getMessage());
            return null;
        }
    }
}

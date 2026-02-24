package com.carizon.crawler;

import com.carizon.batch.CrawlRunRecorder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * TCAR(롯데렌터카) 목록 크롤러
 *  - GET: https://mycarsave.lotterentacar.net/cr/search/ajax/list
 *  - QueryString으로 페이징/필터 전달 (perPageNum=15, page=1..N)
 *  - 응답: { result: { data: [ ... ], carTotalCount, recordsFiltered, ... } }
 *  - 저장 테이블: raw_tcar(payload JSON, body_type, car_image_url2)
 *  - carType 파라미터별 9회 호출하여 body_type 매핑
 */
@Slf4j
@Component
public class TcarCrawler {

    private static final String LIST_URL = "https://mycarsave.lotterentacar.net/cr/search/ajax/list";

    /** carType 코드 → body_type 한글 매핑 */
    private static final LinkedHashMap<String, String> CAR_TYPE_MAP = new LinkedHashMap<>();
    static {
        CAR_TYPE_MAP.put("002001", "경차");
        CAR_TYPE_MAP.put("002002", "소형");
        CAR_TYPE_MAP.put("002003", "준중형");
        CAR_TYPE_MAP.put("002004", "중형");
        CAR_TYPE_MAP.put("002005", "대형");
        CAR_TYPE_MAP.put("002007", "RV");
        CAR_TYPE_MAP.put("002008", "SUV");
        CAR_TYPE_MAP.put("002009", "승합");
        CAR_TYPE_MAP.put("002010", "화물");
    }

    private final OkHttpClient http = new OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofSeconds(30))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final JdbcTemplate jdbc;
    private final CrawlRunRecorder recorder;

    public TcarCrawler(JdbcTemplate jdbc, CrawlRunRecorder recorder) {
        this.jdbc = jdbc;
        this.recorder = recorder;
    }

    /** 전체 풀 스캔 1회 실행 — carType 9종을 순회 */
    @SuppressWarnings("unchecked")
    public void runOnceFull() {
        Instant started = Instant.now();
        String runId = recorder.recordStart("TCAR", started);

        int perPage = 100;
        int fetchedTotal = 0;

        try {
            // ★ 시작 시 한 번만 전체 초기화
            try {
                log.warn("[TCAR] TRUNCATE raw_tcar start");
                jdbc.execute("TRUNCATE TABLE raw_tcar");
                log.warn("[TCAR] TRUNCATE raw_tcar done");
            } catch (Exception e) {
                log.error("[TCAR] TRUNCATE failed: {}", e.toString(), e);
                return;
            }

            log.info("[TCAR] start: perPage={}, carTypes={}", perPage, CAR_TYPE_MAP.size());

            // carType별로 순회
            for (Map.Entry<String, String> entry : CAR_TYPE_MAP.entrySet()) {
                String carTypeCode = entry.getKey();
                String bodyType = entry.getValue();

                log.info("[TCAR] crawling carType={} ({})", carTypeCode, bodyType);
                int typeTotal = 0;
                int page = 1;

                while (true) {
                    HttpUrl url = buildUrl(page, perPage, carTypeCode);
                    Request req = new Request.Builder()
                            .url(url)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36")
                            .header("Accept", "application/json")
                            .get()
                            .build();

                    log.info("[TCAR] request carType={} page={}", carTypeCode, page);

                    try (Response resp = http.newCall(req).execute()) {
                        if (!resp.isSuccessful()) {
                            log.warn("[TCAR] non-200 carType={} page={} status={}", carTypeCode, page, resp.code());
                            break;
                        }
                        String body = resp.body() != null ? resp.body().string() : "";
                        if (body.isBlank()) {
                            log.info("[TCAR] empty response carType={} page={} → stop", carTypeCode, page);
                            break;
                        }

                        Map<String, Object> root = mapper.readValue(
                                body, new TypeReference<Map<String, Object>>() {});
                        Map<String, Object> result = (Map<String, Object>) root.getOrDefault("result", Collections.emptyMap());
                        List<Map<String, Object>> list = (List<Map<String, Object>>) result.get("data");

                        int batchCount = (list == null) ? 0 : list.size();
                        if (batchCount == 0) {
                            log.info("[TCAR] no more data carType={} page={} → stop", carTypeCode, page);
                            break;
                        }

                        // payload + car_image_url2 + body_type 저장
                        String sql = "INSERT INTO raw_tcar(payload, car_image_url2, body_type) VALUES (CAST(? AS JSON), ?, ?)";
                        List<Object[]> params = new ArrayList<>(batchCount);
                        for (Map<String, Object> item : list) {
                            String payloadJson = mapper.writeValueAsString(item);
                            String carImageUrl = buildTcarImageUrl(item);
                            params.add(new Object[]{ payloadJson, carImageUrl, bodyType });
                        }
                        int[] res = jdbc.batchUpdate(sql, params);
                        typeTotal += res.length;
                        fetchedTotal += res.length;

                        log.info("[TCAR] carType={} page={} saved {} (typeTotal={}, grandTotal={})",
                                carTypeCode, page, res.length, typeTotal, fetchedTotal);

                        if (batchCount < perPage) {
                            log.info("[TCAR] last page carType={} (list < perPage) → stop (page={}, items={})",
                                    carTypeCode, page, batchCount);
                            break;
                        }

                        page++;
                        Thread.sleep(600);
                    } catch (Exception e) {
                        log.error("[TCAR] exception carType={} page={} → stop: {}", carTypeCode, page, e.toString(), e);
                        break;
                    }
                }

                log.info("[TCAR] carType={} ({}) done, typeTotal={}", carTypeCode, bodyType, typeTotal);
            }

            recorder.recordEnd(runId, fetchedTotal, Instant.now());
        } catch (Exception e) {
            recorder.recordFail(runId, fetchedTotal, Instant.now(), e.toString());
        }

        log.info("[TCAR] done totalItems={} elapsed={}s",
                fetchedTotal, Duration.between(started, Instant.now()).toSeconds());
    }

    /** carType 파라미터를 포함한 URL 빌드 */
    private HttpUrl buildUrl(int page, int perPage, String carTypeCode) {
        HttpUrl.Builder b = Objects.requireNonNull(HttpUrl.parse(LIST_URL)).newBuilder();

        b.addQueryParameter("country", "");
        b.addQueryParameter("perPageNum", String.valueOf(perPage));
        b.addQueryParameter("page", String.valueOf(page));
        b.addQueryParameter("orderType", "");
        // carType: URL 인코딩된 JSON 배열 ["002001"] 형태
        b.addQueryParameter("carType", "[\"" + carTypeCode + "\"]");
        b.addQueryParameter("carShapeType", "");
        b.addQueryParameter("categoryGroup", "[]");

        b.addQueryParameter("minYear", "");
        b.addQueryParameter("maxYear", "");
        b.addQueryParameter("minMileage", "");
        b.addQueryParameter("maxMileage", "");
        b.addQueryParameter("minPrice", "");
        b.addQueryParameter("maxPrice", "");
        b.addQueryParameter("minRentPrice", "");
        b.addQueryParameter("maxRentPrice", "");
        b.addQueryParameter("minSalePrice", "");
        b.addQueryParameter("maxSalePrice", "");
        b.addQueryParameter("minBuyPrice", "");
        b.addQueryParameter("maxBuyPrice", "");
        b.addQueryParameter("brandCert", "");
        b.addQueryParameter("colour", "");
        b.addQueryParameter("inColor", "");
        b.addQueryParameter("fuel", "");
        b.addQueryParameter("checkedCenterCodes", "");
        b.addQueryParameter("option", "");
        b.addQueryParameter("blotte", "");
        b.addQueryParameter("themeId", "");
        b.addQueryParameter("themeType", "");
        b.addQueryParameter("keyword", "");
        b.addQueryParameter("cert", "");
        b.addQueryParameter("manage", "");
        b.addQueryParameter("promotion", "");
        b.addQueryParameter("reqDirect", "");
        b.addQueryParameter("consult", "");
        b.addQueryParameter("rentBuy", "");
        b.addQueryParameter("tagList", "");
        b.addQueryParameter("saleTyAll", "");
        b.addQueryParameter("saleTyRent", "");
        b.addQueryParameter("saleTySale", "");
        b.addQueryParameter("isDiscount", "0");

        return b.build();
    }

    /**
     * TCAR car_image_url 생성
     * 규칙: carThumbnail 사용
     * URL 형식: https://img-mycarsave.lotterentacar.net/uploadFile/2025/09/19/LFILE_000070902320250919081733.png
     */
    private String buildTcarImageUrl(Map<String, Object> item) {
        try {
            Object carThumbnailObj = item.get("carThumbnail");
            if (carThumbnailObj == null) return null;

            String carThumbnail = String.valueOf(carThumbnailObj);
            if (carThumbnail.isBlank()) return null;

            // 경로가 /로 시작하면 제거
            if (carThumbnail.startsWith("/")) {
                carThumbnail = carThumbnail.substring(1);
            }

            return "https://img-mycarsave.lotterentacar.net/uploadFile/" + carThumbnail;
        } catch (Exception e) {
            log.warn("[TCAR] car_image_url build failed: {}", e.getMessage());
            return null;
        }
    }
}

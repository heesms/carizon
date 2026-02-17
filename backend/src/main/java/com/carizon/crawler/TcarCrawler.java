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
 *  - 저장 테이블: raw_tcar(payload JSON)
 */
@Slf4j
@Component
public class TcarCrawler {

    private static final String LIST_URL = "https://mycarsave.lotterentacar.net/cr/search/ajax/list";

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

    /** 전체 풀 스캔 1회 실행 */
    @SuppressWarnings("unchecked")
    public void runOnceFull() {
        Instant started = Instant.now();
        String runId = recorder.recordStart("TCAR", started);

        int page = 1;
        int perPage = 100;                 // 서버가 15만 허용하면 15로 낮춰
        int fetchedTotal = 0;

        try {
            // ★ 시작 시 한 번만 전체 초기화
            try {
                log.warn("[TCAR] TRUNCATE raw_tcar start");
                jdbc.execute("TRUNCATE TABLE raw_tcar");
                log.warn("[TCAR] TRUNCATE raw_tcar done");
            } catch (Exception e) {
                log.error("[TCAR] TRUNCATE failed: {}", e.toString(), e);
                return; // 초기화 안 되면 적재하지 않음
            }

            log.info("[TCAR] start: perPage={}", perPage);

            while (true) {
                HttpUrl url = buildUrl(page, perPage);
                Request req = new Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36")
                        .header("Accept", "application/json")
                        .get()
                        .build();

                log.info("[TCAR] request page={} url={}", page, url);

                try (Response resp = http.newCall(req).execute()) {
                    if (!resp.isSuccessful()) {
                        log.warn("[TCAR] non-200 page={} status={}", page, resp.code());
                        break;
                    }
                    String body = resp.body() != null ? resp.body().string() : "";
                    if (body.isBlank()) {
                        log.info("[TCAR] empty response(page={}) → stop", page);
                        break;
                    }

                    Map<String, Object> root = mapper.readValue(
                            body, new TypeReference<Map<String, Object>>() {});
                    Map<String, Object> result = (Map<String, Object>) root.getOrDefault("result", Collections.emptyMap());
                    List<Map<String, Object>> list = (List<Map<String, Object>>) result.get("data");

                    int batchCount = (list == null) ? 0 : list.size();
                    if (batchCount == 0) {
                        log.info("[TCAR] no more data(page={}) → stop", page);
                        break;
                    }

                    // 원본 item 그대로 저장 (raw_tcar.payload) + car_image_url2 생성
                    // car_image_url은 GENERATED COLUMN이므로 car_image_url2 사용
                    String sql = "INSERT INTO raw_tcar(payload, car_image_url2) VALUES (CAST(? AS JSON), ?)";
                    List<Object[]> params = new ArrayList<>(batchCount);
                    for (Map<String, Object> item : list) {
                        String payloadJson = mapper.writeValueAsString(item);
                        String carImageUrl = buildTcarImageUrl(item);
                        params.add(new Object[]{ payloadJson, carImageUrl });
                    }
                    int[] res = jdbc.batchUpdate(sql, params);
                    fetchedTotal += res.length;

                    log.info("[TCAR] page={} saved {} (total={})", page, res.length, fetchedTotal);

                    // 마지막 페이지 추정: 현재 페이지 데이터 수 < perPage
                    if (batchCount < perPage) {
                        log.info("[TCAR] last page (list < perPage) → stop (page={}, items={})", page, batchCount);
                        break;
                    }

                    page++;
                    Thread.sleep(600); // 부하 완화
                } catch (Exception e) {
                    log.error("[TCAR] exception page={} → stop: {}", page, e.toString(), e);
                    break;
                }
            }

            recorder.recordEnd(runId, fetchedTotal, Instant.now());
        } catch (Exception e) {
            recorder.recordFail(runId, fetchedTotal, Instant.now(), e.toString());
        }

        log.info("[TCAR] done totalItems={} elapsed={}s",
                fetchedTotal, Duration.between(started, Instant.now()).toSeconds());
    }

    /** 캡처 기준 기본 파라미터로 URL 빌드 */
    private HttpUrl buildUrl(int page, int perPage) {
        HttpUrl.Builder b = Objects.requireNonNull(HttpUrl.parse(LIST_URL)).newBuilder();

        // 캡처에 보였던 파라미터들 — 기본값을 비워 전체 검색
        b.addQueryParameter("country", "");
        b.addQueryParameter("perPageNum", String.valueOf(perPage));
        b.addQueryParameter("page", String.valueOf(page));
        b.addQueryParameter("orderType", "");
        b.addQueryParameter("carType", "");
        b.addQueryParameter("carShapeType", "");
        // categoryGroup는 [] 형태 — URL 인코딩된 "[]"
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
        b.addQueryParameter("isDiscount", "0"); // 캡처상 0
        // shuffleKey 등 유동 파라미터는 생략해도 목록 반환됨

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

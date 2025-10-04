package com.carizon.crawler;

import com.carizon.batch.CrawlRunRecorder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
            log.info("[TCAR] 시작: perPage={}", perPage);

            while (true) {
                HttpUrl url = buildUrl(page, perPage);
                Request req = new Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36")
                        .header("Accept", "application/json")
                        .get()
                        .build();

                log.info("[TCAR] 요청 page={} url={}", page, url);

                try (Response resp = http.newCall(req).execute()) {
                    if (!resp.isSuccessful()) {
                        log.warn("[TCAR] non-200 page={} status={}", page, resp.code());
                        break;
                    }
                    String body = resp.body() != null ? resp.body().string() : "";
                    if (body.isBlank()) {
                        log.info("[TCAR] 빈 응답(page={}) → 종료", page);
                        break;
                    }

                    Map<String, Object> root = mapper.readValue(
                            body, new TypeReference<Map<String, Object>>() {});
                    Map<String, Object> result = (Map<String, Object>) root.getOrDefault("result", Collections.emptyMap());
                    List<Map<String, Object>> list = (List<Map<String, Object>>) result.get("data");

                    int batchCount = (list == null) ? 0 : list.size();
                    if (batchCount == 0) {
                        log.info("[TCAR] 더 이상 데이터 없음(page={}) → 종료", page);
                        break;
                    }

                    // 원본 item 그대로 저장 (raw_tcar.payload)
                    String sql = "INSERT INTO raw_tcar(payload) VALUES (CAST(? AS JSON))";
                    List<Object[]> params = new ArrayList<>(batchCount);
                    for (Map<String, Object> item : list) {
                        params.add(new Object[]{ mapper.writeValueAsString(item) });
                    }
                    int[] res = jdbc.batchUpdate(sql, params);
                    fetchedTotal += res.length;

                    log.info("[TCAR] page={} 저장 {}건 (누적={})", page, res.length, fetchedTotal);

                    // 마지막 페이지 추정: 현재 페이지 데이터 수 < perPage
                    if (batchCount < perPage) {
                        log.info("[TCAR] 마지막 페이지 추정(list < perPage) → 종료 (page={}, items={})", page, batchCount);
                        break;
                    }

                    page++;
                    Thread.sleep(600); // 부하 완화
                } catch (Exception e) {
                    log.error("[TCAR] 예외 page={} → 종료: {}", page, e.toString(), e);
                    break;
                }
            }

            recorder.recordEnd(runId, fetchedTotal, Instant.now());
        } catch (Exception e) {
            recorder.recordFail(runId, fetchedTotal, Instant.now(), e.toString());
        }

        log.info("[TCAR] 완료 totalItems={} elapsed={}s",
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
}

package com.carizon.crawler;

import com.carizon.batch.CrawlRunRecorder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Charancha(차란차) 목록 크롤러
 * - 엔드포인트: https://charancha.com/bu/sell/listCtl (POST, JSON)
 * - 페이지네이션: payload.page = 1..N, perPageNum = 15(기본) — 여기서는 100으로 올려서 fewer calls
 * - 저장: raw_charancha(payload JSON)
 */
@Slf4j
@Component
public class CharanchaCrawler {

    private static final String LIST_URL = "https://charancha.com/bu/sell/listCtl";

    private final OkHttpClient http = new OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofSeconds(30))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final JdbcTemplate jdbc;
    private final CrawlRunRecorder recorder;

    public CharanchaCrawler(JdbcTemplate jdbc, CrawlRunRecorder recorder) {
        this.jdbc = jdbc;
        this.recorder = recorder;
    }

    /** 하루 1회 전체 새로 긁기 */
    @SuppressWarnings("unchecked")
    public int runOnceFull() {
        Instant started = Instant.now();
        String runId = recorder.recordStart("CHARANCHA", started);

        int page = 1;
        int perPage = 100;           // 필요시 15로 낮출 수 있음
        int fetchedTotal = 0;

            try {
                // 초기화(원하면 주석 처리)
                try {
                    log.warn("[CHARANCHA] TRUNCATE raw_charancha start");
                    jdbc.execute("TRUNCATE TABLE raw_charancha");
                    log.warn("[CHARANCHA] TRUNCATE raw_charancha done");
                } catch (Exception e) {
                    log.error("[CHARANCHA] TRUNCATE failed: {}", e.toString(), e);
                    return fetchedTotal;
                }

            while (true) {
                Map<String, Object> payload = buildPayload(page, perPage);
                String json = mapper.writeValueAsString(payload);
                Request req = new Request.Builder()
                        .url(LIST_URL)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36")
                        .header("Accept", "application/json, text/plain, */*")
                        .header("Content-Type", "application/json;charset=UTF-8")
                        .post(RequestBody.create(json, MediaType.parse("application/json; charset=utf-8")))
                        .build();

                log.info("[CHARANCHA] page={} perPage={} request", page, perPage);

                try (Response resp = http.newCall(req).execute()) {
                    if (!resp.isSuccessful()) {
                        log.warn("[CHARANCHA] non-200 page={} status={}", page, resp.code());
                        break;
                    }
                    String body = resp.body() != null ? resp.body().string() : "";
                    if (body.isBlank()) {
                        log.info("[CHARANCHA] empty response(page={}) → stop", page);
                        break;
                    }

                    Map<String, Object> root = mapper.readValue(body, new TypeReference<Map<String, Object>>() {});
                    List<Map<String, Object>> list = (List<Map<String, Object>>) root.get("list");
                    int batchCount = (list == null) ? 0 : list.size();
                    if (batchCount == 0) {
                        log.info("[CHARANCHA] no more data(page={}) → stop", page);
                        break;
                    }

                    // 원본 item 그대로 저장 (raw_charancha.payload JSON) + car_image_url 생성
                    String sql = "INSERT INTO raw_charancha(payload, car_image_url) VALUES (CAST(? AS JSON), ?)";
                    List<Object[]> params = new ArrayList<>(batchCount);
                    for (Map<String, Object> item : list) {
                        String payloadJson = mapper.writeValueAsString(item);
                        String carImageUrl = buildCharanchaImageUrl(item);
                        params.add(new Object[]{ payloadJson, carImageUrl });
                    }
                    int[] res = jdbc.batchUpdate(sql, params);
                    fetchedTotal += res.length;

                    log.info("[CHARANCHA] page={} saved {} (total={})", page, res.length, fetchedTotal);

                    // 마지막 페이지 추정: list 크기가 페이지 사이즈보다 작으면 종료
                    if (batchCount < perPage) {
                        log.info("[CHARANCHA] last page (list < perPage) → stop (page={}, items={})", page, batchCount);
                        break;
                    }

                    page++;
                    Thread.sleep(600); // 서버 부하 완화
                } catch (Exception e) {
                    log.error("[CHARANCHA] exception page={} → stop: {}", page, e.toString(), e);
                    break;
                }
            }

            recorder.recordEnd(runId, fetchedTotal, Instant.now());
        } catch (Exception e) {
            recorder.recordFail(runId, fetchedTotal, Instant.now(), e.toString());
        }

        log.info("[CHARANCHA] done totalItems={} elapsed={}s", fetchedTotal, Duration.between(started, Instant.now()).toSeconds());

        return fetchedTotal;
    }

    /** 요청에 필요한 payload — 네가 준 캡처 그대로 기본값을 유지하고 페이지/사이즈만 바꿔서 보냄 */
    private Map<String, Object> buildPayload(int page, int perPage) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("order", "");
        p.put("perPageNum", perPage);
        p.put("page", String.valueOf(page));         // 서버가 문자열/숫자 모두 허용할 가능성 → 문자열로 보냄
        p.put("releaseDtSearch", "");
        p.put("mileageSearch", "");
        p.put("priceSearch", "");

        // 필터(전부 비워서 전체 검색). 필요시 값 채워서 사용.
        p.put("accidentSearch", "");
        p.put("carNo", "");
        p.put("colorSearch", "");
        p.put("countryCd", "");
        p.put("countryCdSearch", "KO");              // 캡처상 "KO"로 보임. 불필요하면 빈 값으로 두어도 됨.
        p.put("fuelSearch", "");
        p.put("gradeCdList", "");
        p.put("makerCd", "");
        p.put("makerCdSearch", "");
        p.put("menuType", "type1");                  // 캡처 값
        p.put("mileageEnd", "");
        p.put("mileageStart", "");
        p.put("modelCd", "");
        p.put("modelCdSearch", "");
        p.put("modelDetailCd", "");
        p.put("modelDetailCdSearch", "");
        p.put("optionCnt", "");
        p.put("optionSearch", "");
        return p;
    }

    private static final String CARIMG_BASE = "https://charancha.com/uploads/carimg/xxlarge";
    private static final String CARIMG_QUERY = "?w=480&h=360&f=webp";

    /**
     * CHARANCHA 차량 이미지 URL 생성
     * - API 응답에서 이미지 식별자 추출 (carImg, car_img, carImage 등 시도)
     * - URL 형식: https://charancha.com/uploads/carimg/xxlarge/{연도}/{파일명}?w=480&h=360&f=webp
     * - UUID만 오면 연도는 payload.regDt 기준 (예: "2025-10-23 14:31:59" → 2025), 없으면 올해
     */
    private String buildCharanchaImageUrl(Map<String, Object> item) {
        try {
            String carImg = getFirstNonBlank(item, "carImg", "car_img", "carImage", "mainImg", "imgUrl", "thumbnail", "img");
            if (carImg == null || carImg.isBlank()) return null;

            carImg = carImg.trim();
            if (carImg.startsWith("http://") || carImg.startsWith("https://")) return carImg;

            if (carImg.startsWith("/")) carImg = carImg.substring(1);
            if (carImg.isBlank()) return null;

            String path;
            if (carImg.contains("/")) {
                path = carImg;
            } else {
                // UUID만 오면 연도 = payload.regDt 기준, 없으면 올해
                String year = extractYearFromRegDt(item);
                if (!carImg.contains(".")) carImg = carImg + ".jpg";
                path = year + "/" + carImg;
            }

            return CARIMG_BASE + "/" + path + CARIMG_QUERY;
        } catch (Exception e) {
            log.warn("[CHARANCHA] car_image_url build failed: {}", e.getMessage());
            return null;
        }
    }

    /** payload.regDt 에서 연도 추출 (예: "2025-10-23 14:31:59" → "2025"), 실패 시 올해 */
    private String extractYearFromRegDt(Map<String, Object> item) {
        Object regDtObj = item != null ? item.get("regDt") : null;
        if (regDtObj != null) {
            String regDt = String.valueOf(regDtObj).trim();
            if (!regDt.isBlank() && regDt.length() >= 4) {
                // "2025-10-23 ..." 또는 "20251023" 등 앞 4자리가 연도
                String y = regDt.substring(0, 4);
                if (y.matches("\\d{4}")) return y;
            }
        }
        return String.valueOf(java.time.Year.now().getValue());
    }

    private String getFirstNonBlank(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object v = map.get(key);
            if (v == null) continue;
            String s = String.valueOf(v).trim();
            if (!s.isBlank() && !"null".equalsIgnoreCase(s)) return s;
        }
        return null;
    }
}

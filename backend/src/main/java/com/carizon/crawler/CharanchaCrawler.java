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
    public void runOnceFull() {
        Instant started = Instant.now();
        String runId = recorder.recordStart("CHARANCHA", started);

        int page = 1;
        int perPage = 100;           // 필요시 15로 낮출 수 있음
        int fetchedTotal = 0;

        try {
            // 초기화(원하면 주석 처리)
            try {
                log.warn("[CHARANCHA] TRUNCATE raw_charancha 시작");
                jdbc.execute("TRUNCATE TABLE raw_charancha");
                log.warn("[CHARANCHA] TRUNCATE raw_charancha 완료");
            } catch (Exception e) {
                log.error("[CHARANCHA] TRUNCATE 실패: {}", e.toString(), e);
                return;
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

                log.info("[CHARANCHA] page={} perPage={} 요청", page, perPage);

                try (Response resp = http.newCall(req).execute()) {
                    if (!resp.isSuccessful()) {
                        log.warn("[CHARANCHA] non-200 page={} status={}", page, resp.code());
                        break;
                    }
                    String body = resp.body() != null ? resp.body().string() : "";
                    if (body.isBlank()) {
                        log.info("[CHARANCHA] 빈 응답(page={}) → 종료", page);
                        break;
                    }

                    Map<String, Object> root = mapper.readValue(body, new TypeReference<Map<String, Object>>() {});
                    List<Map<String, Object>> list = (List<Map<String, Object>>) root.get("list");
                    int batchCount = (list == null) ? 0 : list.size();
                    if (batchCount == 0) {
                        log.info("[CHARANCHA] 더 이상 데이터 없음(page={}) → 종료", page);
                        break;
                    }

                    // 원본 item 그대로 저장 (raw_charancha.payload JSON)
                    String sql = "INSERT INTO raw_charancha(payload) VALUES (CAST(? AS JSON))";
                    List<Object[]> params = new ArrayList<>(batchCount);
                    for (Map<String, Object> item : list) {
                        params.add(new Object[]{ mapper.writeValueAsString(item) });
                    }
                    int[] res = jdbc.batchUpdate(sql, params);
                    fetchedTotal += res.length;

                    log.info("[CHARANCHA] page={} 저장 {}건 (누적={})", page, res.length, fetchedTotal);

                    // 마지막 페이지 추정: list 크기가 페이지 사이즈보다 작으면 종료
                    if (batchCount < perPage) {
                        log.info("[CHARANCHA] 마지막 페이지로 추정(list < perPage) → 종료 (page={}, items={})", page, batchCount);
                        break;
                    }

                    page++;
                    Thread.sleep(600); // 서버 부하 완화
                } catch (Exception e) {
                    log.error("[CHARANCHA] 예외 page={} → 종료: {}", page, e.toString(), e);
                    break;
                }
            }

            recorder.recordEnd(runId, fetchedTotal, Instant.now());
        } catch (Exception e) {
            recorder.recordFail(runId, fetchedTotal, Instant.now(), e.toString());
        }

        log.info("[CHARANCHA] 완료 totalItems={} elapsed={}s", fetchedTotal, Duration.between(started, Instant.now()).toSeconds());
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
}

package com.carizon.crawler;

import com.carizon.batch.CrawlRunRecorder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Component
public class ChachachaCrawler {

    private static final String BASE = "https://m.kbchachacha.com/public/web/search/infinitySearch.json";
    private static final String INCLUDE_FIELDS =
            "carSeq,carNo,firstAdDay,adDay,regiSiteGbn,shopNo,danjiNo,fileNameArray,ownerYn," +
                    "makerName,className,carName,modelName,gradeName,regiDay,yymm,km,cityCodeName2," +
                    "sellAmtGbn,sellAmt,sellAmtPrev,carMasterSpecialYn,monthLeaseAmt,directYn,carAccidentNo," +
                    "warrantyYn,kbLeaseYn,orderDate,certifiedShopYn,kbCertifiedYn,hasOverThreeFileNames,diagYn," +
                    "diagGbn,lineAdYn,carAccidentNo,colorCodeName,gasName,homeserviceYn2,labsDanjiNo2,premiumYn," +
                    "t34SellGbn,t34MonthAmt,t34DiscountAmt,adState,paymentPremiumYn,contractingYn," +
                    "makerCode,classCode,carCode,modelCode,gradeCode,useCodeName,autoGbnName,numCc,fileNameArray";

    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final JdbcTemplate jdbc;

    private final CrawlRunRecorder recorder;   // ✅ 주입

    public ChachachaCrawler(JdbcTemplate jdbc, CrawlRunRecorder recorder) { this.jdbc = jdbc;     this.recorder = recorder;
    }

    @SuppressWarnings("unchecked")
    public void runOnce() {
        Instant started = Instant.now();
        String runId = recorder.recordStart("CHACHACHA", started);  // ✅ 시작 기록

        List<Object> searchAfter = null;
        int pageSize = 1000;             // 천 개씩 처리
        int fetchedTotal = 0;
        int page = 0;


        try {
            // ★ 시작 시 한 번만 전체 초기화
        try {
            log.warn("[CRAWL] TRUNCATE raw_chachacha 시작");
            jdbc.execute("TRUNCATE TABLE raw_chachacha");
            log.warn("[CRAWL] TRUNCATE raw_chachacha 완료");
        } catch (Exception e) {
            log.error("[CRAWL] TRUNCATE 실패: {}", e.toString(), e);
            return; // 초기화 안 되면 적재하지 않음 (원하면 계속 진행하도록 바꿔도 됨)
        }

        log.info("[CRAWL] KB차차차 시작 pageSize={}", pageSize);

        while (true) {
            page++;
            try {
                // URL 구성
                StringBuilder url = new StringBuilder(BASE)
                        .append("?sort=-orderDate&page=1")
                        .append("&pageSize=").append(pageSize)
                        .append("&includeFields=").append(INCLUDE_FIELDS)
                        .append("&displaySoldoutYn=Y")
                        .append("&v=").append(System.currentTimeMillis());
                if (searchAfter != null) {
                    for (Object v : searchAfter) url.append("&searchAfter=").append(v);
                }
                String finalUrl = url.toString();
                log.debug("[CRAWL] request page={} url={}", page, finalUrl);

                // HTTP
                Request req = new Request.Builder()
                        .url(finalUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36")
                        .header("Accept", "application/json, text/plain, */*")
                        .build();

                try (Response resp = http.newCall(req).execute()) {
                    int code = resp.code();
                    if (!resp.isSuccessful()) {
                        log.warn("[CRAWL] non-200 page={} status={}", page, code);
                        break; // 필요시 재시도 로직 추가
                    }

                    byte[] bytes = Objects.requireNonNull(resp.body()).bytes();
                    Map<String, Object> data = mapper.readValue(bytes, new TypeReference<>() {});
                    Map<String, Object> result = (Map<String, Object>) data.getOrDefault("result", Map.of());
                    List<Map<String, Object>> list = (List<Map<String, Object>>) result.getOrDefault("hits", List.of());

                    log.info("hits={}", list);

                    int batchCount = list.size();
                    log.info("[CRAWL] page={} status={} items={}", page, code, batchCount);

                    if (batchCount == 0) {
                        log.info("[CRAWL] 빈 결과 → 종료 (page={})", page);
                        break;
                    }

                    // 원본 JSON 저장 + car_image_url 생성
                    String sql = "INSERT INTO raw_chachacha(payload, car_image_url) VALUES (CAST(? AS JSON), ?)";
                    List<Object[]> params = new ArrayList<>(batchCount);
                    for (Map<String, Object> item : list) {
                        String payloadJson = mapper.writeValueAsString(item);
                        String carImageUrl = buildChachachaImageUrl(item);
                        params.add(new Object[]{ payloadJson, carImageUrl });
                    }
                    int[] res = jdbc.batchUpdate(sql, params);
                    log.debug("[CRAWL] page={} dbInserted={}", page, res.length);

                    fetchedTotal += batchCount;

                    Object nextSa = result.get("searchAfter");
                    if (!(nextSa instanceof List<?> nextList) || nextList.isEmpty()) {
                        log.info("[CRAWL] 다음 searchAfter 없음 → 종료 (page={})", page);
                        break;
                    }
                    searchAfter = (List<Object>) nextList;
                    log.debug("[CRAWL] next searchAfter={}", searchAfter);

                    if (batchCount < pageSize) {
                        log.info("[CRAWL] 마지막 페이지로 추정(list < pageSize) → 종료 (page={}, items={})", page, batchCount);
                        break;
                    }

                    Thread.sleep(600); // 서버 부하 완화
                }
            } catch (Exception e) {
                log.error("[CRAWL] 예외 발생 page={} → 종료: {}", page, e.toString(), e);
                break;
            }
        }
            recorder.recordEnd(runId, fetchedTotal, Instant.now());   // ✅ 성공 기록
        }catch (Exception e) {
            recorder.recordFail(runId, fetchedTotal, Instant.now(), e.toString()); // ✅ 실패 기록
        }

        log.info("[CRAWL] 완료 totalItems={} elapsed={}s", fetchedTotal, Duration.between(started, Instant.now()).toSeconds());
    }

    /**
     * CHACHACHA car_image_url 생성
     * 규칙: fileNameArray 첫번째 파일명 사용
     * URL 형식: https://img.kbchachacha.com/IMG/carimg/l/img08/img2758/27587540_7561184939553420.jpeg?width=720
     * - img08: car_seq의 4번째 자리수 (0이면 img10)
     * - img2758: car_seq의 1~4번째 자리수
     */
    private String buildChachachaImageUrl(Map<String, Object> item) {
        try {
            Object carSeqObj = item.get("carSeq");
            if (carSeqObj == null) return null;
            
            String carSeq = String.valueOf(carSeqObj);
            if (carSeq.length() < 4) return null;
            
            // fileNameArray에서 첫번째 파일명 가져오기
            Object fileNameArrayObj = item.get("fileNameArray");
            String fileName = null;
            if (fileNameArrayObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> fileNameList = (List<Object>) fileNameArrayObj;
                if (!fileNameList.isEmpty()) {
                    fileName = String.valueOf(fileNameList.get(0));
                }
            }
            if (fileName == null || fileName.isBlank()) return null;
            
            // car_seq에서 경로 추출
            // 4번째 자리수 (인덱스 3)
            int fourthDigit = Character.getNumericValue(carSeq.charAt(3));
            String imgFolder = (fourthDigit == 0) ? "img10" : "img" + String.format("%02d", fourthDigit);
            
            // 1~4번째 자리수 (인덱스 0~3)
            String imgPath = carSeq.substring(0, Math.min(4, carSeq.length()));
            
            return String.format("https://img.kbchachacha.com/IMG/carimg/l/%s/img%s/%s?width=720",
                    imgFolder, imgPath, fileName);
        } catch (Exception e) {
            log.warn("[CHACHACHA] car_image_url 생성 실패: {}", e.getMessage());
            return null;
        }
    }
}

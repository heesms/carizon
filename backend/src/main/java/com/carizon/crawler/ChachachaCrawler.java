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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.*;

@Slf4j
@Component
public class ChachachaCrawler {
    private static final Set<String> DATE_FIELDS = Set.of("firstAdDay", "adDay", "orderDate", "regiDay");
    private static final String INSERT_SQL = "INSERT INTO raw_chachacha(payload, car_image_url, option_array) VALUES (CAST(? AS JSON), ?, ?)";
    private static final DateTimeFormatter STRATEGY_DATETIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter STRATEGY_DATETIME_NO_SEC = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter STRATEGY_DATETIME_FLEX = DateTimeFormatter.ofPattern("uuuu-M-d H:m:s").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter STRATEGY_DATETIME_NO_SEC_FLEX = DateTimeFormatter.ofPattern("uuuu-M-d H:m").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter STRATEGY_DATE_ONLY = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter STRATEGY_DATE_ONLY_FLEX = DateTimeFormatter.ofPattern("uuuu-M-d").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter STRATEGY_DATE_COMPACT = DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter STRATEGY_YEAR_MONTH = DateTimeFormatter.ofPattern("uuuuMM").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter STRATEGY_YEAR_MONTH_DASH = DateTimeFormatter.ofPattern("uuuu-MM").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter STRATEGY_DATETIME_COMPACT = DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter STRATEGY_DATETIME_OUTPUT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");
    private static final DateTimeFormatter STRATEGY_DATE_OUTPUT = DateTimeFormatter.ofPattern("uuuu-MM-dd");

    private static final String BASE = "https://m.kbchachacha.com/public/web/search/infinitySearch.json";
    private static final String INCLUDE_FIELDS =
            "carSeq,carNo,firstAdDay,adDay,regiSiteGbn,shopNo,danjiNo,fileNameArray,ownerYn," +
                    "makerName,className,carName,modelName,gradeName,regiDay,yymm,km,cityCodeName2," +
                    "sellAmtGbn,sellAmt,sellAmtPrev,carMasterSpecialYn,monthLeaseAmt,directYn,carAccidentNo," +
                    "warrantyYn,kbLeaseYn,orderDate,certifiedShopYn,kbCertifiedYn,hasOverThreeFileNames,diagYn," +
                    "diagGbn,lineAdYn,carAccidentNo,colorCodeName,gasName,homeserviceYn2,labsDanjiNo2,premiumYn," +
                    "t34SellGbn,t34MonthAmt,t34DiscountAmt,adState,paymentPremiumYn,contractingYn," +
                    "makerCode,classCode,carCode,modelCode,gradeCode,useCodeName,autoGbnName,numCc,fileNameArray,optionNameArray";

    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final JdbcTemplate jdbc;

    private final CrawlRunRecorder recorder;   // ✅ 주입

    public ChachachaCrawler(JdbcTemplate jdbc, CrawlRunRecorder recorder) { this.jdbc = jdbc;     this.recorder = recorder;
    }

    @SuppressWarnings("unchecked")
    public int runOnce() {
        Instant started = Instant.now();
        String runId = recorder.recordStart("CHACHACHA", started);  // ✅ 시작 기록

        List<Object> searchAfter = null;
        int pageSize = 1000;             // 천 개씩 처리
        int fetchedTotal = 0;
        int page = 0;


        try {
            // ★ 시작 시 한 번만 전체 초기화
        try {
            log.warn("[CRAWL] TRUNCATE raw_chachacha start");
            jdbc.execute("TRUNCATE TABLE raw_chachacha");
            log.warn("[CRAWL] TRUNCATE raw_chachacha done");
        } catch (Exception e) {
            log.error("[CRAWL] TRUNCATE failed: {}", e.toString(), e);
            return fetchedTotal; // 초기화 안 되면 적재하지 않음 (원하면 계속 진행하도록 바꿔도 됨)
        }

        log.info("[CRAWL] KB Chachacha start pageSize={}", pageSize);

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
                        log.info("[CRAWL] empty result → stop (page={})", page);
                        break;
                    }

                    // 원본 JSON 저장 + car_image_url 생성 + 옵션 문자열 저장
                    List<RawChachachaInsertRow> rows = new ArrayList<>(batchCount);
                    for (Map<String, Object> item : list) {
                        sanitizeChachachaDates(item);
                        String payloadJson = mapper.writeValueAsString(item);
                        String carImageUrl = buildChachachaImageUrl(item);
                        String optionArray = buildChachachaOptionArray(item);
                        rows.add(new RawChachachaInsertRow(payloadJson, carImageUrl, optionArray));
                    }
                    int inserted = insertRowsWithSkip(rows, page);
                    log.debug("[CRAWL] page={} dbInserted={}", page, inserted);

                    fetchedTotal += inserted;

                    Object nextSa = result.get("searchAfter");
                    if (!(nextSa instanceof List<?> nextList) || nextList.isEmpty()) {
                        log.info("[CRAWL] no next searchAfter → stop (page={})", page);
                        break;
                    }
                    searchAfter = (List<Object>) nextList;
                    log.debug("[CRAWL] next searchAfter={}", searchAfter);

                    if (batchCount < pageSize) {
                        log.info("[CRAWL] last page (list < pageSize) → stop (page={}, items={})", page, batchCount);
                        break;
                    }

                    Thread.sleep(150); // 서버 부하 완화
                }
            } catch (Exception e) {
                log.error("[CRAWL] exception page={} → stop: {}", page, e.toString(), e);
                break;
            }
        }
            recorder.recordEnd(runId, fetchedTotal, Instant.now());   // ✅ 성공 기록
        }catch (Exception e) {
            recorder.recordFail(runId, fetchedTotal, Instant.now(), e.toString()); // ✅ 실패 기록
        }

        log.info("[CRAWL] done totalItems={} elapsed={}s", fetchedTotal, Duration.between(started, Instant.now()).toSeconds());

        return fetchedTotal;
    }

    private int insertRowsWithSkip(List<RawChachachaInsertRow> rows, int page) {
        if (rows == null || rows.isEmpty()) return 0;

        List<Object[]> params = new ArrayList<>(rows.size());
        for (RawChachachaInsertRow row : rows) {
            params.add(new Object[]{ row.payloadJson, row.carImageUrl, row.optionArray });
        }

        try {
            return jdbc.batchUpdate(INSERT_SQL, params).length;
        } catch (Exception e) {
            log.warn("[CRAWL] page={} batch insert failed, fallback row-by-row (skip bad rows): {}", page, e.getMessage());
            int inserted = 0;
            for (int i = 0; i < rows.size(); i++) {
                RawChachachaInsertRow row = rows.get(i);
                try {
                    jdbc.update(INSERT_SQL, row.payloadJson, row.carImageUrl, row.optionArray);
                    inserted++;
                } catch (Exception rowEx) {
                    log.warn("[CRAWL] page={} skip row={} insert failed: {}", page, i, rowEx.getMessage());
                }
            }
            return inserted;
        }
    }

    private static class RawChachachaInsertRow {
        final String payloadJson;
        final String carImageUrl;
        final String optionArray;

        RawChachachaInsertRow(String payloadJson, String carImageUrl, String optionArray) {
            this.payloadJson = payloadJson;
            this.carImageUrl = carImageUrl;
            this.optionArray = optionArray;
        }
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
            log.warn("[CHACHACHA] car_image_url build failed: {}", e.getMessage());
            return null;
        }
    }

    private String buildChachachaOptionArray(Map<String, Object> item) {
        if (item == null) return null;
        Object optionsObj = item.get("optionNameArray");
        if (optionsObj instanceof List<?> list) {
            List<String> tokens = new ArrayList<>();
            for (Object option : list) {
                if (option == null) continue;
                String token = String.valueOf(option).trim();
                if (!token.isEmpty()) tokens.add(token);
            }
            return tokens.isEmpty() ? null : String.join("|", tokens);
        }
        if (optionsObj != null) {
            String raw = String.valueOf(optionsObj).trim();
            return raw.isEmpty() ? null : raw;
        }
        return null;
    }

    private void sanitizeChachachaDates(Map<String, Object> item) {
        if (item == null) return;

        for (String field : DATE_FIELDS) {
            Object raw = item.get(field);
            String value = Objects.toString(raw, null);
            if (value == null) {
                continue;
            }
            value = value.trim();
            if (value.isBlank()) {
                item.put(field, null);
                continue;
            }
            String normalized = normalizeDate(raw.toString());
            if (normalized == null) {
                log.debug("[CHACHACHA] invalid date field {}={} -> null", field, value);
                item.put(field, null);
            } else if (!normalized.equals(value)) {
                item.put(field, normalized);
            }
        }
    }

    private String normalizeDate(String rawValue) {
        if (rawValue == null) return null;

        String value = rawValue.trim()
                .replace("T", " ")
                .replace("/", "-")
                .replace(".0", "");
        if (value.isBlank() || "null".equalsIgnoreCase(value)) {
            return null;
        }

        try {
            try {
                return LocalDateTime.parse(value, STRATEGY_DATETIME).format(STRATEGY_DATETIME_OUTPUT);
            } catch (DateTimeParseException e) {
                // no-op
            }
            try {
                return LocalDateTime.parse(value, STRATEGY_DATETIME_NO_SEC).format(STRATEGY_DATETIME_OUTPUT);
            } catch (DateTimeParseException e) {
                // no-op
            }
            try {
                return LocalDateTime.parse(value, STRATEGY_DATETIME_FLEX).format(STRATEGY_DATETIME_OUTPUT);
            } catch (DateTimeParseException e) {
                // no-op
            }
            try {
                return LocalDateTime.parse(value, STRATEGY_DATETIME_NO_SEC_FLEX).format(STRATEGY_DATETIME_OUTPUT);
            } catch (DateTimeParseException e) {
                // no-op
            }
            try {
                return LocalDate.parse(value, STRATEGY_DATE_ONLY).format(STRATEGY_DATE_OUTPUT);
            } catch (DateTimeParseException e) {
                // no-op
            }
            try {
                return LocalDate.parse(value, STRATEGY_DATE_ONLY_FLEX).format(STRATEGY_DATE_OUTPUT);
            } catch (DateTimeParseException e) {
                // no-op
            }
            try {
                return LocalDate.parse(value, STRATEGY_DATE_COMPACT).format(STRATEGY_DATE_OUTPUT);
            } catch (DateTimeParseException e) {
                // no-op
            }
            try {
                return java.time.YearMonth.parse(value, STRATEGY_YEAR_MONTH_DASH).atDay(1).format(STRATEGY_DATE_OUTPUT);
            } catch (DateTimeParseException e) {
                // no-op
            }
            try {
                return java.time.YearMonth.parse(value, STRATEGY_YEAR_MONTH).atDay(1).format(STRATEGY_DATE_OUTPUT);
            } catch (DateTimeParseException e) {
                // no-op
            }
            try {
                return LocalDateTime.parse(value, STRATEGY_DATETIME_COMPACT).format(STRATEGY_DATETIME_OUTPUT);
            } catch (DateTimeParseException e) {
                // no-op
            }
        } catch (Exception e) {
            return null;
        }

        return null;
    }
}

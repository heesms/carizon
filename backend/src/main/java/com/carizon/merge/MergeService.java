package com.carizon.merge;

import com.carizon.common.service.CarMasterIdSequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 파이프라인
 *  1) RAW_* → platform_car : 배치별 REQUIRES_NEW + 플랫폼별 Named Lock + ODKU
 *  2) platform_car → car_master 링크 : 배치별 REQUIRES_NEW, SKIP LOCKED
 *  3) 가격 스냅샷 / 미노출 SOLD : 단계별 REQUIRES_NEW
 *
 * 필수 인덱스:
 *  - platform_car UNIQUE (platform_name, platform_car_key)
 *  - platform_car PK (platform_car_id), INDEX (car_id), INDEX (last_seen_date)
 *  - car_master UNIQUE (car_no)
 *  - raw_* PK/INDEX (id)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MergeService {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate npJdbc;
    private final PlatformTransactionManager txManager;
    private final CarMasterIdSequenceService carMasterIdSequenceService;

    // 잠금 경합 줄이려면 우선 작게. 상황 봐가며 키워도 됨.
    private static final int UPSERT_BATCH_SIZE = 1_000; // raw_* → platform_car
    private static final int LINK_BATCH_SIZE   = 1_000; // platform_car → car_master
    private static final boolean PARALLEL_ALL  = false;
    private static final String AD_DATE_EXPR_CHACHACHA = """
            CASE
              WHEN COALESCE(STR_TO_DATE(r.AD_DAY, '%Y%m%d'), STR_TO_DATE(r.AD_DAY, '%Y-%m-%d')) IS NULL THEN NULL
              ELSE TIMESTAMP(
                COALESCE(STR_TO_DATE(r.AD_DAY, '%Y%m%d'), STR_TO_DATE(r.AD_DAY, '%Y-%m-%d')),
                COALESCE(
                  TIME(STR_TO_DATE(r.ORDER_DT, '%Y-%m-%d %H:%i:%s')),
                  TIME(STR_TO_DATE(r.ORDER_DT, '%Y%m%d%H%i%s')),
                  '00:00:00'
                )
              )
            END
            """.strip();
    private static final String AD_DATE_EXPR_ENCAR = """
            COALESCE(
              STR_TO_DATE(SUBSTRING(REPLACE(REPLACE(CAST(r.regist_dt AS CHAR), 'T', ' '), '/', '-'), 1, 19), '%Y-%m-%d %H:%i:%s'),
              STR_TO_DATE(CONCAT(SUBSTRING(REPLACE(REPLACE(CAST(r.regist_dt AS CHAR), 'T', ' '), '/', '-'), 1, 10), ' 00:00:00'), '%Y-%m-%d %H:%i:%s'),
              STR_TO_DATE(CONCAT(TRIM(CAST(r.regist_dt AS CHAR)), ' 00:00:00'), '%Y%m%d %H:%i:%s')
            )
            """.strip();
    private static final String AD_DATE_EXPR_KCAR = "DATE_SUB(NOW(), INTERVAL 3 MONTH)";
    private static final String AD_DATE_EXPR_CHUTCHA = "COALESCE(r.ad_date, DATE_SUB(NOW(), INTERVAL 3 MONTH))";
    private static final String AD_DATE_EXPR_CHARANCHA = """
            COALESCE(
              STR_TO_DATE(SUBSTRING(REPLACE(REPLACE(COALESCE(
                JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.sell_start_dt')),
                JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.sellStartDt'))
              ), 'T', ' '), '/', '-'), 1, 19), '%Y-%m-%d %H:%i:%s'),
              STR_TO_DATE(CONCAT(SUBSTRING(REPLACE(REPLACE(COALESCE(
                JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.sell_start_dt')),
                JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.sellStartDt'))
              ), 'T', ' '), '/', '-'), 1, 10), ' 00:00:00'), '%Y-%m-%d %H:%i:%s')
            )
            """.strip();
    private static final String AD_DATE_EXPR_TCAR = """
            CASE
              WHEN r.reg_dt_compact IS NULL OR TRIM(CAST(r.reg_dt_compact AS CHAR)) = '' THEN NULL
              WHEN LENGTH(TRIM(CAST(r.reg_dt_compact AS CHAR))) = 8
                THEN STR_TO_DATE(CONCAT(TRIM(CAST(r.reg_dt_compact AS CHAR)), ' 00:00:00'), '%Y%m%d %H:%i:%s')
              ELSE STR_TO_DATE(
                CONCAT(SUBSTRING(REPLACE(TRIM(CAST(r.reg_dt_compact AS CHAR)), '/', '-'), 1, 10), ' 00:00:00'),
                '%Y-%m-%d %H:%i:%s'
              )
            END
            """.strip();
    private static final String FUEL_MAPPING_EXPR_TEMPLATE = """
            CASE
              WHEN __RAW_FUEL__ IS NULL OR TRIM(CAST(__RAW_FUEL__ AS CHAR)) = '' THEN '기타'
              WHEN LOWER(TRIM(CAST(__RAW_FUEL__ AS CHAR))) = 'null' THEN '기타'
              WHEN UPPER(TRIM(CAST(__RAW_FUEL__ AS CHAR))) = 'CNG' THEN '기타'
              WHEN UPPER(TRIM(CAST(__RAW_FUEL__ AS CHAR))) = 'LPG' THEN 'LPG'
              WHEN UPPER(TRIM(CAST(__RAW_FUEL__ AS CHAR))) = 'LPG(일반인 구입)' THEN 'LPG(일반인)'
              WHEN UPPER(TRIM(CAST(__RAW_FUEL__ AS CHAR))) = 'LPG(일반인)' THEN 'LPG(일반인)'
              WHEN UPPER(TRIM(CAST(__RAW_FUEL__ AS CHAR))) = 'LPG+전기' THEN '하이브리드(LPG)'
              WHEN TRIM(CAST(__RAW_FUEL__ AS CHAR)) = '가솔린' THEN '가솔린'
              WHEN UPPER(TRIM(CAST(__RAW_FUEL__ AS CHAR))) = '가솔린+CNG' THEN '가솔린'
              WHEN UPPER(TRIM(CAST(__RAW_FUEL__ AS CHAR))) = '가솔린+LPG' THEN '가솔린'
              WHEN TRIM(CAST(__RAW_FUEL__ AS CHAR)) = '가솔린+전기' THEN '하이브리드(가솔린)'
              WHEN TRIM(CAST(__RAW_FUEL__ AS CHAR)) = '기타' THEN '기타'
              WHEN TRIM(CAST(__RAW_FUEL__ AS CHAR)) = '디젤' THEN '디젤'
              WHEN TRIM(CAST(__RAW_FUEL__ AS CHAR)) = '디젤+전기' THEN '하이브리드(디젤)'
              WHEN TRIM(CAST(__RAW_FUEL__ AS CHAR)) = '수소' THEN '수소'
              WHEN TRIM(CAST(__RAW_FUEL__ AS CHAR)) = '수소전기' THEN '기타'
              WHEN TRIM(CAST(__RAW_FUEL__ AS CHAR)) = '전기' THEN '전기'
              WHEN UPPER(TRIM(CAST(__RAW_FUEL__ AS CHAR))) = '전기(EV)' THEN '전기'
              WHEN TRIM(CAST(__RAW_FUEL__ AS CHAR)) = '하이브리드' THEN '하이브리드(가솔린)'
              WHEN UPPER(TRIM(CAST(__RAW_FUEL__ AS CHAR))) = '하이브리드(LPG)' THEN '하이브리드(LPG)'
              WHEN TRIM(CAST(__RAW_FUEL__ AS CHAR)) = '하이브리드(가솔린)' THEN '하이브리드(가솔린)'
              WHEN TRIM(CAST(__RAW_FUEL__ AS CHAR)) = '하이브리드(디젤)' THEN '하이브리드(디젤)'
              ELSE '기타'
            END
            """.strip();
    private static final String COLOR_MAPPING_EXPR_TEMPLATE = """
            CASE
              WHEN __RAW_COLOR__ IS NULL OR TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '' THEN '기타'
              WHEN LOWER(TRIM(CAST(__RAW_COLOR__ AS CHAR))) = 'null' THEN '기타'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '흰색투톤' THEN '흰색투톤'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '흰색' THEN '흰색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '회색' THEN '회색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '하늘색' THEN '하늘색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '하늘' THEN '하늘색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '파랑색' THEN '파랑색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '파랑' THEN '파랑색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '파란색' THEN '파랑색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '초록색' THEN '초록색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '청옥색' THEN '초록색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '청색' THEN '파랑색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '진주투톤' THEN '진주색투톤'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '진주색' THEN '진주색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '진주' THEN '진주색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '쥐색' THEN '회색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '주황색' THEN '주황색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '주황' THEN '주황색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '자주색' THEN '보라색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '인기색상' THEN '기타'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '은회색' THEN '은색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '은하색' THEN '은색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '은색투톤' THEN '은색투톤'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '은색' THEN '은색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '연두색' THEN '초록색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '연금색' THEN '금색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '빨강색' THEN '빨강색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '빨강' THEN '빨강색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '빨간색' THEN '빨강색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '분홍색' THEN '분홍색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '분홍' THEN '분홍색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '보라색' THEN '보라색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '보라' THEN '보라색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '미색' THEN '미색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '명은색' THEN '은색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '담녹색' THEN '초록색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '녹색' THEN '초록색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '노랑색' THEN '노랑색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '노랑' THEN '노랑색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '노란색' THEN '노랑색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '남색' THEN '파랑색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '기타' THEN '기타'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '금색투톤' THEN '금색투톤'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '금색' THEN '금색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '검정투톤' THEN '검정투톤'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '검정색' THEN '검정색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '검정' THEN '검정색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '갈색투톤' THEN '갈색투톤'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '갈색' THEN '갈색'
              WHEN TRIM(CAST(__RAW_COLOR__ AS CHAR)) = '갈대색' THEN '미색'
              ELSE '기타'
            END
            """.strip();

    private static String mapFuelExpr(String rawFuelExpr) {
        return FUEL_MAPPING_EXPR_TEMPLATE.replace("__RAW_FUEL__", rawFuelExpr);
    }

    private static String mapColorExpr(String rawColorExpr) {
        return COLOR_MAPPING_EXPR_TEMPLATE.replace("__RAW_COLOR__", rawColorExpr);
    }

    /* ====================== 유틸 ====================== */

    @FunctionalInterface
    interface TxCallable<T> { T call(); }

    /** Deadlock/Lock wait 시 재시도 (선형 + 지터) */
    private <T> T runWithRetry(int maxRetry, long baseMillis, TxCallable<T> work) {
        int attempt = 0;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        while (true) {
            try {
                return work.call();
            } catch (RuntimeException e) {
                String msg = String.valueOf(e.getMessage());
                boolean retryable = msg.contains("Deadlock found")
                        || msg.contains("Lock wait timeout exceeded");
                if (!retryable || attempt++ >= maxRetry) throw e;
                long sleep = baseMillis * attempt + rnd.nextLong(0, baseMillis);
                log.warn("Retrying ({}/{}) after {}ms: {}", attempt, maxRetry, sleep, msg);
                try { Thread.sleep(sleep); } catch (InterruptedException ignored) {}
            }
        }
    }

    private void logBatchSql(String stage, String sql, Object... params) {
        if (!log.isInfoEnabled()) return;
        log.info("[batch-sql] {}:\n{}\nparams={}", stage, sql == null ? "" : sql.strip(), Arrays.toString(params));
    }

    /** 배치 내부에서 사용할 REQUIRES_NEW 템플릿 (READ_COMMITTED) */
    private TransactionTemplate requiresNew() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return tx;
    }

    /** 같은 커넥션/트랜잭션에서 Named Lock 획득 후 작업 실행 */
    private <T> T inTxWithNamedLock(String lockName, TxCallable<T> work) {
        return runWithRetry(3, 200L, () ->
                requiresNew().execute(status -> {
                    Integer ok = jdbc.queryForObject("SELECT GET_LOCK(?, 3)", Integer.class, lockName);
                    if (ok == null || ok != 1) {
                        throw new IllegalStateException("could not acquire lock: " + lockName);
                    }
                    try {
                        return work.call();
                    } finally {
                        try {
                            jdbc.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, lockName);
                        } catch (Exception e) {
                            log.warn("RELEASE_LOCK({}) failed: {}", lockName, e.getMessage());
                        }
                    }
                })
        );
    }

    /* ========== 외부 호출 (상위 트랜잭션 비활성화) ========== */

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int mergeAllPlatforms(LocalDate bizDate) {
        long mergeStart = System.currentTimeMillis();
        log.info("[merge] all start bizDate={} parallel={}", bizDate, PARALLEL_ALL);
        if (PARALLEL_ALL) {
            var ex = Executors.newFixedThreadPool(6);
            try {
                log.info("[merge] stage 1/2 platform merges start (parallel)");
                CompletableFuture.allOf(
                        CompletableFuture.runAsync(() -> mergeChachachaDetail(bizDate), ex),
                        CompletableFuture.runAsync(() -> mergeEncarDetail(bizDate), ex),
                        CompletableFuture.runAsync(() -> mergeKcarDetail(bizDate), ex),
                        CompletableFuture.runAsync(() -> mergeChutchaDetail(bizDate), ex),
                        CompletableFuture.runAsync(() -> mergeCharanchaDetail(bizDate), ex),
                        CompletableFuture.runAsync(() -> mergeTcarDetail(bizDate), ex)
                ).join();
                log.info("[merge] stage 1/2 platform merges done (parallel)");
            } finally { ex.shutdown(); }
        } else {
            long stageStart = System.currentTimeMillis();
            log.info("[merge] stage 1/7 CHACHACHA start");
            mergeChachachaDetail(bizDate);
            log.info("[merge] stage 1/7 CHACHACHA done elapsedMs={}", System.currentTimeMillis() - stageStart);

            stageStart = System.currentTimeMillis();
            log.info("[merge] stage 2/7 ENCAR start");
            mergeEncarDetail(bizDate);
            log.info("[merge] stage 2/7 ENCAR done elapsedMs={}", System.currentTimeMillis() - stageStart);

            stageStart = System.currentTimeMillis();
            log.info("[merge] stage 3/7 KCAR start");
            mergeKcarDetail(bizDate);
            log.info("[merge] stage 3/7 KCAR done elapsedMs={}", System.currentTimeMillis() - stageStart);

            stageStart = System.currentTimeMillis();
            log.info("[merge] stage 4/7 CHUTCHA start");
            mergeChutchaDetail(bizDate);
            log.info("[merge] stage 4/7 CHUTCHA done elapsedMs={}", System.currentTimeMillis() - stageStart);

            stageStart = System.currentTimeMillis();
            log.info("[merge] stage 5/7 CHARANCHA start");
            mergeCharanchaDetail(bizDate);
            log.info("[merge] stage 5/7 CHARANCHA done elapsedMs={}", System.currentTimeMillis() - stageStart);

            stageStart = System.currentTimeMillis();
            log.info("[merge] stage 6/7 TCAR start");
            mergeTcarDetail(bizDate);
            log.info("[merge] stage 6/7 TCAR done elapsedMs={}", System.currentTimeMillis() - stageStart);
        }
        long postProcessStart = System.currentTimeMillis();
        log.info("[merge] stage 7/7 postProcess start");
        int result = postProcess(bizDate);
        log.info("[merge] stage 7/7 postProcess done elapsedMs={}", System.currentTimeMillis() - postProcessStart);
        log.info("[merge] all done elapsedMs={}", System.currentTimeMillis() - mergeStart);
        return result;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int mergeChachacha(LocalDate bizDate) { mergeChachachaDetail(bizDate); return postProcess(bizDate); }
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int mergeCharancha(LocalDate bizDate) { mergeCharanchaDetail(bizDate); return postProcess(bizDate); }
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int mergeEncar(LocalDate bizDate)     { mergeEncarDetail(bizDate);     return postProcess(bizDate); }
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int mergeKcar(LocalDate bizDate)      { mergeKcarDetail(bizDate);      return postProcess(bizDate); }
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int mergeChutcha(LocalDate bizDate)   { mergeChutchaDetail(bizDate);   return postProcess(bizDate); }
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int mergeTcar(LocalDate bizDate)      { mergeTcarDetail(bizDate);      return postProcess(bizDate); }

    /* ====================== 공통: 커서 범위 계산 ====================== */

    private Long nextUpperIdFor(String table, long fromId, int size) {
        // 안전을 위해 하드코딩 테이블명만 허용
        String sql;
        switch (table) {
            case "raw_chachacha" -> sql = """
                SELECT MAX(id) FROM (
                  SELECT id FROM raw_chachacha WHERE id > ? ORDER BY id LIMIT ?
                ) x
            """;
            case "raw_encar" -> sql = """
                SELECT MAX(id) FROM (
                  SELECT id FROM raw_encar WHERE id > ? ORDER BY id LIMIT ?
                ) x
            """;
            case "raw_encar_truck" -> sql = """
                SELECT MAX(id) FROM (
                  SELECT id FROM raw_encar_truck WHERE id > ? ORDER BY id LIMIT ?
                ) x
            """;
            case "raw_kcar" -> sql = """
                SELECT MAX(id) FROM (
                  SELECT id FROM raw_kcar WHERE id > ? ORDER BY id LIMIT ?
                ) x
            """;
            case "raw_chutcha" -> sql = """
                SELECT MAX(id) FROM (
                  SELECT id FROM raw_chutcha WHERE id > ? ORDER BY id LIMIT ?
                ) x
            """;
            case "raw_charancha" -> sql = """
                SELECT MAX(id) FROM (
                  SELECT id FROM raw_charancha WHERE id > ? ORDER BY id LIMIT ?
                ) x
            """;
            case "raw_tcar" -> sql = """
                SELECT MAX(id) FROM (
                  SELECT id FROM raw_tcar WHERE id > ? ORDER BY id LIMIT ?
                ) x
            """;
            default -> throw new IllegalArgumentException("unknown table " + table);
        }
        return jdbc.queryForObject(sql, Long.class, fromId, size);
    }

    /* ========== 1) RAW → platform_car (플랫폼별 Named Lock + ODKU + 범위 처리) ========== */

    public void mergeChachachaDetail(LocalDate bizDate) {
        long from = 0L;
        final String lockName = "merge:CHACHACHA";
        while (true) {
            Long to = nextUpperIdFor("raw_chachacha", from, UPSERT_BATCH_SIZE);
            if (to == null) break;
            final long cursorFrom = from;
            final long cursorTo   = to;

            inTxWithNamedLock(lockName, () -> {
                String bizDateStr = bizDate.toString();
                String sql = """
                    INSERT INTO platform_car
                      (platform_name, platform_car_key, car_no, car_id,
                       maker_code, model_group_code, model_code, trim_code, grade_code,
                       maker_name, model_group_name, model_name, trim_name, grade_name,
                      price, km, displacement, yymm, status, color, fuel, transmission, body_type, region,
                       m_url, pc_url, first_ad_day, ad_date, created_at, updated_at, extra, last_seen_date, car_image_url,
                       option_array)
                    SELECT
                       'CHACHACHA', r.car_seq, r.car_no, NULL,
                       r.MAKER_CODE, r.CLASS_CODE, r.CAR_CODE, r.MODEL_CODE, r.GRADE_CODE,
                       r.MAKER_NAME, r.CLASS_NAME, r.CAR_NAME, r.MODEL_NAME, r.GRADE_NAME,
                       r.SELL_AMT, r.KM, r.displacement, r.YYMM, 'ONSALE', COLOR_EXPR, FUEL_EXPR, r.auto_gbn_name, r.use_code_name, r.REGION,
                       CONCAT('https://m.kbchachacha.com/public/web/car/detail.kbc?carSeq=', r.CAR_SEQ),
                       CONCAT('https://www.kbchachacha.com/public/car/detail.kbc?carSeq=', r.car_seq),
                       r.FIRST_AD_DAY, AD_DATE_EXPR_CHACHACHA, NOW(), NOW(), r.payload, DATE('BIZ_DATE_PLACEHOLDER'), r.car_image_url,
                       r.option_array
                    FROM raw_chachacha r
                    WHERE r.id > ? AND r.id <= ?
                    ON DUPLICATE KEY UPDATE
                       price          = VALUES(price),
                       status         = VALUES(status),
                       extra          = VALUES(extra),
                       ad_date        = COALESCE(VALUES(ad_date), platform_car.ad_date),
                       car_image_url  = VALUES(car_image_url),
                       color          = VALUES(color),
                       fuel           = VALUES(fuel),
                       option_array   = COALESCE(NULLIF(VALUES(option_array), ''), platform_car.option_array),
                       last_seen_date = VALUES(last_seen_date),
                       updated_at     = NOW(),
                       car_no = COALESCE(platform_car.car_no, VALUES(car_no)),
                       maker_code = COALESCE(platform_car.maker_code, VALUES(maker_code)),
                       model_group_code = COALESCE(platform_car.model_group_code, VALUES(model_group_code)),
                       model_code = COALESCE(platform_car.model_code, VALUES(model_code)),
                       trim_code = COALESCE(platform_car.trim_code, VALUES(trim_code)),
                       grade_code = COALESCE(platform_car.grade_code, VALUES(grade_code)),
                       maker_name = COALESCE(platform_car.maker_name, VALUES(maker_name)),
                       model_group_name = COALESCE(platform_car.model_group_name, VALUES(model_group_name)),
                       model_name = COALESCE(platform_car.model_name, VALUES(model_name)),
                       trim_name = COALESCE(platform_car.trim_name, VALUES(trim_name)),
                       grade_name = COALESCE(platform_car.grade_name, VALUES(grade_name))
                """.replace("BIZ_DATE_PLACEHOLDER", bizDateStr)
                   .replace("AD_DATE_EXPR_CHACHACHA", AD_DATE_EXPR_CHACHACHA)
                   .replace("COLOR_EXPR", mapColorExpr("r.COLOR"))
                   .replace("FUEL_EXPR", mapFuelExpr("r.GAS_NAME"));
                int affected = jdbc.update(sql, cursorFrom, cursorTo);
                log.debug("CHACHACHA upsert affected={}", affected);
                return null;
            });

            from = cursorTo;
        }
    }

    public void mergeEncarDetail(LocalDate bizDate) {
        mergeEncarDetailBySource(bizDate, "raw_encar", true, "merge:ENCAR", "ENCAR", "ENCAR");
        mergeEncarDetailBySource(bizDate, "raw_encar_truck", false, "merge:ENCAR_TRUCK", "ENCAR_TRUCK", "ENCAR_TRUCK");
    }

    private void mergeEncarDetailBySource(
            LocalDate bizDate,
            String rawTable,
            boolean requireNormalSellType,
            String lockName,
            String logTag,
            String platformName
    ) {
        String sourceTable = validateEncarRawTable(rawTable);
        long from = 0L;
        while (true) {
            Long to = nextUpperIdFor(sourceTable, from, UPSERT_BATCH_SIZE);
            if (to == null) break;
            final long cursorFrom = from;
            final long cursorTo   = to;

            inTxWithNamedLock(lockName, () -> {
                String bizDateStr = bizDate.toString();
                String sellTypeFilter = requireNormalSellType ? " AND r.sell_type = 'NORMAL'" : "";
                String sql = """
                    INSERT INTO platform_car
                      (platform_name, platform_car_key, car_no, car_id,
                       maker_code, model_group_code, model_code, trim_code, grade_code,
                       maker_name, model_group_name, model_name, trim_name, grade_name,
                       price, price_new, km, displacement, yymm, status, color, fuel, transmission, body_type, region,
                       m_url, pc_url, first_ad_day, ad_date, created_at, updated_at, extra, last_seen_date, car_image_url,
                       option_array, sel_option_array, seat_count, my_accident_cnt, flood_total_loss_cnt)
                    SELECT
                      'PLATFORM_NAME_LITERAL', r.vehicle_id, r.vehicle_no, NULL,
                      r.manufacturer_code, r.model_group_code, r.model_code, r.grade_code, r.grade_detail_code,
                      r.manufacturer_name, r.model_group_name, r.model_name, r.grade_name, r.grade_detail_name,
                      COALESCE(CAST(REPLACE(JSON_UNQUOTE(JSON_EXTRACT(r.payload,'$.advertisement.price')), ',', '') AS UNSIGNED), r.price),
                      NULLIF(r.price_new, 0),
                      r.mileage, r.displacement , r.form_year,
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload,'$.advertisement.status')),
                      COLOR_EXPR, FUEL_EXPR, r.transmission,
                      CASE r.body_type
                        WHEN '준중형차' THEN '준중형' WHEN '경차' THEN '경차' WHEN '중형차' THEN '중형'
                        WHEN 'SUV' THEN 'SUV' WHEN '소형차' THEN '소형' WHEN '대형차' THEN '대형'
                        WHEN 'RV' THEN 'RV' WHEN '기타' THEN '기타' WHEN '스포츠카' THEN '스포츠카'
                        WHEN '화물차' THEN '트럭' WHEN '승합차' THEN '승합' WHEN '경승합차' THEN '승합'
                        ELSE r.body_type
                      END,
                      r.region,
                      CONCAT('https://fem.encar.com/cars/detail/', r.vehicle_id),
                      CONCAT('https://fem.encar.com/cars/detail/', r.vehicle_id),
                      DATE_FORMAT(r.first_ad_dt, '%Y%m%d'),
                      AD_DATE_EXPR_ENCAR, NOW(), NOW(), r.payload, DATE('BIZ_DATE_PLACEHOLDER'), r.car_image_url,
                      r.option_array, r.sel_option_array, r.seat_count, r.my_accident_cnt, r.flood_total_loss_cnt
                    FROM RAW_TABLE r
                    WHERE r.id > ? AND r.id <= ?
                      AND COALESCE(r.use_yn, 'Y') = 'Y'
                      SELL_TYPE_FILTER
                    ON DUPLICATE KEY UPDATE
                      price          = VALUES(price),
                      price_new      = COALESCE(VALUES(price_new), platform_car.price_new),
                      status         = VALUES(status),
                      extra          = VALUES(extra),
                      ad_date        = COALESCE(VALUES(ad_date), platform_car.ad_date),
                      car_image_url  = VALUES(car_image_url),
                      color          = VALUES(color),
                      fuel           = VALUES(fuel),
                      option_array   = COALESCE(NULLIF(VALUES(option_array), ''), platform_car.option_array),
                      sel_option_array = COALESCE(NULLIF(VALUES(sel_option_array), ''), platform_car.sel_option_array),
                      seat_count     = COALESCE(VALUES(seat_count), platform_car.seat_count),
                      my_accident_cnt = COALESCE(VALUES(my_accident_cnt), platform_car.my_accident_cnt),
                      flood_total_loss_cnt = COALESCE(VALUES(flood_total_loss_cnt), platform_car.flood_total_loss_cnt),
                      last_seen_date = VALUES(last_seen_date),
                      updated_at     = NOW(),
                      car_no = COALESCE(platform_car.car_no, VALUES(car_no)),
                      maker_code = COALESCE(platform_car.maker_code, VALUES(maker_code)),
                      model_group_code = COALESCE(platform_car.model_group_code, VALUES(model_group_code)),
                      model_code = COALESCE(platform_car.model_code, VALUES(model_code)),
                      trim_code = COALESCE(platform_car.trim_code, VALUES(trim_code)),
                      grade_code = COALESCE(platform_car.grade_code, VALUES(grade_code)),
                      maker_name = COALESCE(platform_car.maker_name, VALUES(maker_name)),
                      model_group_name = COALESCE(platform_car.model_group_name, VALUES(model_group_name)),
                      model_name = COALESCE(platform_car.model_name, VALUES(model_name)),
                      trim_name = COALESCE(platform_car.trim_name, VALUES(trim_name)),
                      grade_name = COALESCE(platform_car.grade_name, VALUES(grade_name))
                """.replace("BIZ_DATE_PLACEHOLDER", bizDateStr)
                   .replace("AD_DATE_EXPR_ENCAR", AD_DATE_EXPR_ENCAR)
                   .replace("RAW_TABLE", sourceTable)
                   .replace("SELL_TYPE_FILTER", sellTypeFilter)
                   .replace("PLATFORM_NAME_LITERAL", platformName)
                   .replace("COLOR_EXPR", mapColorExpr("r.color"))
                   .replace("FUEL_EXPR", mapFuelExpr("r.fuel"));
                int affected = jdbc.update(sql, cursorFrom, cursorTo);
                log.debug("{} upsert affected={}", logTag, affected);
                return null;
            });

            from = cursorTo;
        }
    }

    public void mergeKcarDetail(LocalDate bizDate) {
        long from = 0L;
        final String lockName = "merge:KCAR";
        while (true) {
            Long to = nextUpperIdFor("raw_kcar", from, UPSERT_BATCH_SIZE);
            if (to == null) break;
            final long cursorFrom = from;
            final long cursorTo   = to;

            inTxWithNamedLock(lockName, () -> {
                String bizDateStr = bizDate.toString();
                String sql = """
                    INSERT INTO platform_car
                      (platform_name, platform_car_key, car_no, car_id,
                       maker_code, model_group_code, model_code, trim_code, grade_code,
                       maker_name, model_group_name, model_name, trim_name, grade_name,
                      price, km, displacement, yymm, status, color, fuel, transmission, body_type, region,
                       m_url, pc_url, first_ad_day, ad_date, created_at, updated_at, extra, last_seen_date, car_image_url,
                       option_array)
                    SELECT
                      'KCAR', r.car_cd, r.cno, NULL,
                      r.maker_code, r.model_group_code, r.model_code, r.grade_code, r.grade_detail_code,
                      r.maker_name, r.model_group_name, r.model_name, r.grade_name, r.grade_detail_name,
                      r.price, r.mileage, r.displacement, r.yymm,
                      'SALE',
                      COLOR_EXPR, FUEL_EXPR, r.transmission,
                      CASE r.body_type
                        WHEN '중형차' THEN '중형' WHEN 'SUV' THEN 'SUV' WHEN '대형차' THEN '대형'
                        WHEN '경차' THEN '경차' WHEN '준중형차' THEN '준중형' WHEN '화물차' THEN '화물'
                        WHEN 'RV' THEN 'RV' WHEN '소형차' THEN '소형' WHEN '승합차' THEN '승합'
                        WHEN '스포츠카' THEN '스포츠카'
                        ELSE r.body_type
                      END,
                      r.region,
                      CONCAT('https://m.kcar.com/bc/detail/carInfoDtl?i_sCarCd=', r.car_cd),
                      CONCAT('https://www.kcar.com/bc/detail/carInfoDtl?i_sCarCd=', r.car_cd),
                      NULL, AD_DATE_EXPR_KCAR, NOW(), NOW(), r.payload, DATE('BIZ_DATE_PLACEHOLDER'), r.main_img,
                      r.option_array
                    FROM raw_kcar r
                    WHERE r.id > ? AND r.id <= ?
                    ON DUPLICATE KEY UPDATE
                      price          = VALUES(price),
                      status         = VALUES(status),
                      extra          = VALUES(extra),
                      ad_date        = COALESCE(VALUES(ad_date), platform_car.ad_date),
                      car_image_url  = VALUES(car_image_url),
                      color          = VALUES(color),
                      fuel           = VALUES(fuel),
                      option_array   = COALESCE(NULLIF(VALUES(option_array), ''), platform_car.option_array),
                      last_seen_date = VALUES(last_seen_date),
                      updated_at     = NOW(),
                      car_no = COALESCE(platform_car.car_no, VALUES(car_no)),
                      maker_code = COALESCE(platform_car.maker_code, VALUES(maker_code)),
                      model_group_code = COALESCE(platform_car.model_group_code, VALUES(model_group_code)),
                      model_code = COALESCE(platform_car.model_code, VALUES(model_code)),
                      trim_code = COALESCE(platform_car.trim_code, VALUES(trim_code)),
                      grade_code = COALESCE(platform_car.grade_code, VALUES(grade_code)),
                      maker_name = COALESCE(platform_car.maker_name, VALUES(maker_name)),
                      model_group_name = COALESCE(platform_car.model_group_name, VALUES(model_group_name)),
                      model_name = COALESCE(platform_car.model_name, VALUES(model_name)),
                      trim_name = COALESCE(platform_car.trim_name, VALUES(trim_name)),
                      grade_name = COALESCE(platform_car.grade_name, VALUES(grade_name))
                """.replace("BIZ_DATE_PLACEHOLDER", bizDateStr)
                   .replace("AD_DATE_EXPR_KCAR", AD_DATE_EXPR_KCAR)
                   .replace("COLOR_EXPR", mapColorExpr("r.color"))
                   .replace("FUEL_EXPR", mapFuelExpr("r.fuel"));
                int affected = jdbc.update(sql, cursorFrom, cursorTo);
                log.debug("KCAR upsert affected={}", affected);
                return null;
            });

            from = cursorTo;
        }
    }

    public void mergeChutchaDetail(LocalDate bizDate) {
        long from = 0L;
        final String lockName = "merge:CHUTCHA";
        while (true) {
            Long to = nextUpperIdFor("raw_chutcha", from, UPSERT_BATCH_SIZE);
            if (to == null) break;
            final long cursorFrom = from;
            final long cursorTo   = to;

            inTxWithNamedLock(lockName, () -> {
                String bizDateStr = bizDate.toString();
                String sql = """
                    INSERT INTO platform_car
                      (platform_name, platform_car_key, car_no, car_id,
                       maker_name, model_group_name, model_name, trim_name, grade_name,
                       price, km, displacement, yymm, status, color, fuel, transmission, body_type, region,
                       m_url, pc_url, first_ad_day, ad_date, created_at, updated_at, extra, last_seen_date, car_image_url,
                       option_array)
                    SELECT
                      'CHUTCHA', r.car_id, r.number_plate, NULL,
                      r.brand_name, r.model_name, r.sub_model_name, r.grade_name, r.sub_grade_name,
                      r.price, r.mileage, r.displacement, r.first_reg_year, NULL,
                      COLOR_EXPR, FUEL_EXPR, r.transmission_name,
                      CASE r.car_type
                        WHEN '경차' THEN '경차' WHEN '중대형' THEN '중형' WHEN '대형' THEN '대형'
                        WHEN '준중형' THEN '준중형' WHEN 'SUV' THEN 'SUV' WHEN '소형' THEN '소형'
                        WHEN '스포츠카/쿠페' THEN '스포츠카' WHEN '상용' THEN '상용'
                        ELSE r.car_type
                      END,
                      r.shop_addr_short,
                      CONCAT('https://www.chutcha.net/share/car/detail/', JSON_UNQUOTE(JSON_EXTRACT(r.payload,'$.detail_link_hash'))),
                      CONCAT('https://web.chutcha.net/bmc/detail/', JSON_UNQUOTE(JSON_EXTRACT(r.payload,'$.detail_link_hash'))),
                      NULL, AD_DATE_EXPR_CHUTCHA, NOW(), NOW(), r.payload, DATE('BIZ_DATE_PLACEHOLDER'), r.car_image_url,
                      r.option_array
                    FROM raw_chutcha r
                    WHERE r.id > ? AND r.id <= ? AND r.CAR_ID IS NOT NULL
                    ON DUPLICATE KEY UPDATE
                      price          = VALUES(price),
                      extra          = VALUES(extra),
                      ad_date        = COALESCE(VALUES(ad_date), platform_car.ad_date),
                      car_image_url  = VALUES(car_image_url),
                      color          = VALUES(color),
                      fuel           = VALUES(fuel),
                      option_array   = COALESCE(NULLIF(VALUES(option_array), ''), platform_car.option_array),
                      last_seen_date = VALUES(last_seen_date),
                      updated_at     = NOW(),
                      car_no = COALESCE(platform_car.car_no, VALUES(car_no)),
                      maker_name = COALESCE(platform_car.maker_name, VALUES(maker_name)),
                      model_group_name = COALESCE(platform_car.model_group_name, VALUES(model_group_name)),
                      model_name = COALESCE(platform_car.model_name, VALUES(model_name)),
                      trim_name = COALESCE(platform_car.trim_name, VALUES(trim_name)),
                      grade_name = COALESCE(platform_car.grade_name, VALUES(grade_name))
                """.replace("BIZ_DATE_PLACEHOLDER", bizDateStr)
                   .replace("AD_DATE_EXPR_CHUTCHA", AD_DATE_EXPR_CHUTCHA)
                   .replace("COLOR_EXPR", mapColorExpr("r.color"))
                   .replace("FUEL_EXPR", mapFuelExpr("r.fuel_name"));
                int affected = jdbc.update(sql, cursorFrom, cursorTo);
                log.debug("CHUTCHA upsert affected={}", affected);
                return null;
            });

            from = cursorTo;
        }
    }


    public void mergeCharanchaDetail(LocalDate bizDate) {
        long from = 0L;
        final String lockName = "merge:CHARANCHA";
        while (true) {
            Long to = nextUpperIdFor("raw_charancha", from, UPSERT_BATCH_SIZE);
            if (to == null) break;
            final long cursorFrom = from;
            final long cursorTo   = to;

            inTxWithNamedLock(lockName, () -> {
                String bizDateStr = bizDate.toString();
                String sql = """
                    INSERT INTO platform_car
                                          (platform_name, platform_car_key, car_no, car_id,
                                           maker_code, model_group_code, model_code, trim_code, grade_code,
                                           maker_name, model_group_name, model_name, trim_name, grade_name,
                                           price, km, displacement, yymm, status,
                                           color, fuel, transmission, body_type, region,
                                           m_url, pc_url,
                                           first_ad_day, ad_date, created_at, updated_at, extra, last_seen_date, car_image_url)
                    
                    SELECT
                    'CHARANCHA',
                    CHARAN_NO_EXPR, CHARAN_CAR_NO_EXPR, NULL,
                    CHARAN_MAKER_CODE, CHARAN_MODEL_GROUP_CODE, CHARAN_MODEL_CODE, CHARAN_TRIM_CODE, CHARAN_GRADE_CODE,
                    CHARAN_MAKER_NAME, CHARAN_MODEL_GROUP_NAME, CHARAN_MODEL_NAME, CHARAN_TRIM_NAME, CHARAN_GRADE_NAME,
                    CHARAN_PRICE, CHARAN_KM, CHARAN_DISPLACEMENT,
                    SUBSTR(CHARAN_YYYMM,1,4), 'SALE',
                    COLOR_EXPR, FUEL_EXPR,
                    CHARAN_TRANSMISSION,
                    CASE CHARAN_CAR_TYPE
                      WHEN '소형' THEN '소형' WHEN '중형' THEN '중형' WHEN '대형' THEN '대형'
                      WHEN '경형(일반형)' THEN '경차' WHEN '준중형' THEN '준중형'
                      WHEN '기타' THEN '기타' WHEN '경형(초소형)' THEN '경차'
                      ELSE CHARAN_CAR_TYPE
                    END,
                    CHARAN_REGION,
                    CONCAT('https://charancha.com/bu/sell/view?sellNo=', CHARAN_NO_EXPR),
                    CONCAT('https://charancha.com/bu/sell/view?sellNo=', CHARAN_NO_EXPR),
                    NULL, AD_DATE_EXPR_CHARANCHA, NOW(), NOW(), r.payload, DATE('BIZ_DATE_PLACEHOLDER'), r.car_image_url
                    FROM raw_charancha r
                    WHERE r.id > ? AND r.id <= ?
                    ON DUPLICATE KEY UPDATE
                      price          = VALUES(price),
                      status         = VALUES(status),
                      extra          = VALUES(extra),
                      ad_date        = COALESCE(VALUES(ad_date), platform_car.ad_date),
                      car_image_url  = VALUES(car_image_url),
                      color          = VALUES(color),
                      fuel           = VALUES(fuel),
                      last_seen_date = VALUES(last_seen_date),
                      updated_at     = NOW(),
                      car_no = COALESCE(platform_car.car_no, VALUES(car_no)),
                      maker_code = COALESCE(platform_car.maker_code, VALUES(maker_code)),
                      model_group_code = COALESCE(platform_car.model_group_code, VALUES(model_group_code)),
                      model_code = COALESCE(platform_car.model_code, VALUES(model_code)),
                      trim_code = COALESCE(platform_car.trim_code, VALUES(trim_code)),
                      grade_code = COALESCE(platform_car.grade_code, VALUES(grade_code)),
                      maker_name = COALESCE(platform_car.maker_name, VALUES(maker_name)),
                      model_group_name = COALESCE(platform_car.model_group_name, VALUES(model_group_name)),
                      model_name = COALESCE(platform_car.model_name, VALUES(model_name)),
                      trim_name = COALESCE(platform_car.trim_name, VALUES(trim_name)),
                      grade_name = COALESCE(platform_car.grade_name, VALUES(grade_name))
                """.replace("BIZ_DATE_PLACEHOLDER", bizDateStr)
                   .replace("AD_DATE_EXPR_CHARANCHA", AD_DATE_EXPR_CHARANCHA)
                   .replace("CHARAN_NO_EXPR", "r.sell_no")
                   .replace("CHARAN_CAR_NO_EXPR", "r.car_no")
                   .replace("CHARAN_MAKER_CODE", "r.maker_code")
                   .replace("CHARAN_MODEL_GROUP_CODE", "r.model_code")
                   .replace("CHARAN_MODEL_CODE", "r.model_detail_code")
                   .replace("CHARAN_TRIM_CODE", "r.grade_code")
                   .replace("CHARAN_GRADE_CODE", "NULL")
                   .replace("CHARAN_MAKER_NAME", "r.maker_name")
                   .replace("CHARAN_MODEL_GROUP_NAME", "COALESCE(r.model_name, '')")
                   .replace("CHARAN_MODEL_NAME", "r.model_name")
                   .replace("CHARAN_TRIM_NAME", "r.grade_name")
                   .replace("CHARAN_GRADE_NAME", "NULL")
                   .replace("CHARAN_PRICE", "r.sell_price")
                   .replace("CHARAN_KM", "r.mileage")
                   .replace("CHARAN_DISPLACEMENT", "r.displacement")
                   .replace("CHARAN_YYYMM", "r.yyyymm")
                   .replace("CHARAN_TRANSMISSION", "r.transmission_name")
                   .replace("CHARAN_REGION", "r.region_name")
                   .replace("CHARAN_CAR_TYPE", "r.car_type")
                   .replace("COLOR_EXPR", mapColorExpr("r.color_name"))
                   .replace("FUEL_EXPR", mapFuelExpr("r.fuel_name"));
                int affected = jdbc.update(sql, cursorFrom, cursorTo);
                log.debug("CHARANCHA upsert affected={}", affected);
                return null;
            });

            from = cursorTo;
        }
    }

    public void mergeTcarDetail(LocalDate bizDate) {
        long from = 0L;
        final String lockName = "merge:TCAR";
        boolean isFirstBatch = true;
        while (true) {
            Long to = nextUpperIdFor("raw_tcar", from, UPSERT_BATCH_SIZE);
            if (to == null) break;
            final long cursorFrom = from;
            final long cursorTo   = to;
            final boolean isFirst = isFirstBatch;
            isFirstBatch = false;

            inTxWithNamedLock(lockName, () -> {
                String bizDateStr = bizDate.toString();
                String sql = """
                    INSERT INTO platform_car
                      (platform_name, platform_car_key, car_no, car_id,
                       maker_code, model_group_code, model_code, trim_code, grade_code,
                       maker_name, model_group_name, model_name, trim_name, grade_name,
                       price, price_new, km, displacement, yymm, status, color, fuel, transmission, body_type, region,
                       m_url, pc_url, first_ad_day, ad_date, created_at, updated_at, extra, last_seen_date, car_image_url
                    SELECT
                      'TCAR', 
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.carId')),
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.plateNumber')),
                      NULL,
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.brandId')),
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.modelgroupId')),
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.modelId')),
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.subgradeId')),
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.gradeId')),
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.brandName')),
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.modelgroupName')),
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.modelName')),
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.subgradeName')),
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.gradeName')),
                      COALESCE(
                        NULLIF(CAST(NULLIF(TRIM(REPLACE(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.returnPrice')), JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.return_price'))), ',', '')), '') AS UNSIGNED), 0) DIV 10000,
                        CAST(NULLIF(TRIM(REPLACE(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.price')), JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.priceNew')), JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.priceSell')), JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.promotionPrice'))), ',', '')), '') AS UNSIGNED)),
                      NULLIF(CAST(NULLIF(TRIM(REPLACE(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.priceNew')), ''), ',', '')), '') AS UNSIGNED) DIV 10000, 0),
                      CAST(NULLIF(REPLACE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.mileage')), ',', ''), 'null') AS UNSIGNED),
                      CAST(NULLIF(REPLACE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.displacement')), ',', ''), 'null') AS UNSIGNED),
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.regYear')),
                      CASE WHEN JSON_EXTRACT(r.payload, '$.status') = 0 THEN 'ONSALE' 
                           WHEN JSON_EXTRACT(r.payload, '$.status') = 1 THEN 'SOLD'
                           ELSE 'ONSALE' END,
                      COLOR_EXPR,
                      FUEL_EXPR,
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.trans')),
                      r.body_type,
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.areaCd')),
      CONCAT('https://mycarsave.lotterentacar.net/cr/search/view?carId=', JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.carId'))),
      CONCAT('https://mycarsave.lotterentacar.net/cr/search/view?carId=', JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.carId'))),
      CASE
        WHEN JSON_EXTRACT(r.payload, '$.postStartDt') IS NULL
             OR JSON_EXTRACT(r.payload, '$.postStartDt') = JSON_QUOTE('null')
             OR JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.postStartDt')) = 'null'
             OR JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.postStartDt')) = ''
             OR JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.postStartDt')) IS NULL
        THEN NULL
        ELSE DATE_FORMAT(STR_TO_DATE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.postStartDt')), '%Y-%m-%d %H:%i:%s.%f'), '%Y%m%d')
      END,
                      AD_DATE_EXPR_TCAR, NOW(), NOW(), r.payload, DATE('BIZ_DATE_PLACEHOLDER'), r.car_image_url2
                    FROM raw_tcar r
                    WHERE r.id > ? AND r.id <= ?
                      AND UPPER(TRIM(COALESCE(
                        JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.sale_type')),
                        JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.saleType')),
                        JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.SALE_TYPE')),
                        ''
                      ))) = 'S'
                    ON DUPLICATE KEY UPDATE
                      price          = VALUES(price),
                      price_new      = COALESCE(VALUES(price_new), platform_car.price_new),
                      status         = VALUES(status),
                      extra          = VALUES(extra),
                      ad_date        = COALESCE(VALUES(ad_date), platform_car.ad_date),
                      car_image_url  = VALUES(car_image_url),
                      color          = VALUES(color),
                      fuel           = VALUES(fuel),
                      last_seen_date = VALUES(last_seen_date),
                      updated_at     = NOW(),
                      car_no = COALESCE(platform_car.car_no, VALUES(car_no)),
                      maker_code = COALESCE(platform_car.maker_code, VALUES(maker_code)),
                      model_group_code = COALESCE(platform_car.model_group_code, VALUES(model_group_code)),
                      model_code = COALESCE(platform_car.model_code, VALUES(model_code)),
                      trim_code = COALESCE(platform_car.trim_code, VALUES(trim_code)),
                      grade_code = COALESCE(platform_car.grade_code, VALUES(grade_code)),
                      maker_name = COALESCE(platform_car.maker_name, VALUES(maker_name)),
                      model_group_name = COALESCE(platform_car.model_group_name, VALUES(model_group_name)),
                      model_name = COALESCE(platform_car.model_name, VALUES(model_name)),
                      trim_name = COALESCE(platform_car.trim_name, VALUES(trim_name)),
                      grade_name = COALESCE(platform_car.grade_name, VALUES(grade_name))
                """.replace("BIZ_DATE_PLACEHOLDER", bizDateStr)
                   .replace("AD_DATE_EXPR_TCAR", AD_DATE_EXPR_TCAR)
                   .replace("COLOR_EXPR", mapColorExpr("JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.color'))"))
                   .replace("FUEL_EXPR", mapFuelExpr("JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.fuel'))"));
                int affected = jdbc.update(sql, cursorFrom, cursorTo);
                log.debug("TCAR upsert affected={}", affected);
                
                // 디버깅: 첫 번째 배치의 payload 샘플 로깅
                if (isFirst) {
                    String samplePayload = jdbc.queryForObject(
                        """
                        SELECT payload
                        FROM raw_tcar
                        WHERE id > ?
                          AND UPPER(TRIM(COALESCE(
                            JSON_UNQUOTE(JSON_EXTRACT(payload, '$.sale_type')),
                            JSON_UNQUOTE(JSON_EXTRACT(payload, '$.saleType')),
                            JSON_UNQUOTE(JSON_EXTRACT(payload, '$.SALE_TYPE')),
                            ''
                          ))) = 'S'
                        LIMIT 1
                        """,
                        String.class, cursorFrom);
                    if (samplePayload != null) {
                        log.info("[TCAR] sample payload (first batch): {}", samplePayload);
                    }
                }
                return null;
            });

            from = cursorTo;
        }
    }

    /* ========== 2) master INSERT & car_id 매핑 (청크별 REQUIRES_NEW 커밋) ========== */

    static record PcRow(long platformCarId, String carNo) {}
    static record ExecResult(int processed, long nextCursor) {}

    public int postProcess(LocalDate bizDate) {
        purgeLowPricePlatformCars();
        int linked = linkToMaster();
        snapshotPrices(bizDate);
        closeMissingAds(bizDate);
        return linked;
    }

    /** 모든 플랫폼 공통: 100만원 미만(또는 비정상 0/음수) 매물은 platform_car에서 제거 */
    public void purgeLowPricePlatformCars() {
        runWithRetry(3, 200L, () ->
                requiresNew().execute(status -> {
                    int deleted = jdbc.update("""
                        DELETE FROM platform_car
                        WHERE price IS NULL OR price < 100
                    """);
                    if (deleted > 0) {
                        log.info("[merge] purgeLowPricePlatformCars done: {} rows deleted (price < 100)", deleted);
                    } else {
                        log.info("[merge] purgeLowPricePlatformCars done: no rows");
                    }
                    return null;
                })
        );
    }

    /** TRUNCATE 후 platform_car와 car_master 재생성 (순수 INSERT만 사용, 더 빠름) */
    public Map<String, Object> rebuildFromScratch(LocalDate bizDate) {
        log.warn("[merge] rebuildFromScratch: TRUNCATE platform_car, car_master, car_price_history and rebuild!");
        long nextCarId = carMasterIdSequenceService.snapshotNextCarId();
        log.info("[merge] preserve next car_id={} before TRUNCATE", nextCarId);
        
        // 1단계: TRUNCATE (car_price_history → car_master → platform_car 순서)
        jdbc.execute("TRUNCATE TABLE car_price_history");
        log.info("[merge] car_price_history TRUNCATE done");
        
        jdbc.execute("TRUNCATE TABLE car_master");
        carMasterIdSequenceService.restoreNextCarId(nextCarId);
        log.info("[merge] car_master TRUNCATE done");
        
        jdbc.execute("TRUNCATE TABLE platform_car");
        log.info("[merge] platform_car TRUNCATE done");
        
        // 2단계: platform_car 재생성 (raw_*에서 INSERT만 - ON DUPLICATE KEY UPDATE 제거)
        log.info("[merge] platform_car rebuild start...");
        int platformCarCount = mergeAllPlatformsInsertOnly(bizDate);
        log.info("[merge] platform_car rebuild done: {} rows", platformCarCount);
        
        return Map.of("platformCarCount", platformCarCount);
    }

    /** 플랫폼 차량만 TRUNCATE 후 재생성. car_master는 그대로 유지 */
    public int rebuildPlatformCarOnly(LocalDate bizDate) {
        log.warn("[merge] rebuildPlatformCarOnly: TRUNCATE platform_car and rebuild!");
        jdbc.execute("TRUNCATE TABLE platform_car");
        log.info("[merge] platform_car TRUNCATE done (car_master/cost_history preserved)");

        int platformCarCount = mergeAllPlatformsInsertOnly(bizDate);
        log.info("[merge] platform_car rebuild only done: {} rows", platformCarCount);
        return platformCarCount;
    }

    /** raw_*에서 platform_car로 INSERT만 수행 (ON DUPLICATE KEY UPDATE 없음) */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    private int mergeAllPlatformsInsertOnly(LocalDate bizDate) {
        if (PARALLEL_ALL) {
            var ex = Executors.newFixedThreadPool(6);
            try {
                CompletableFuture.allOf(
                        CompletableFuture.runAsync(() -> mergeChachachaDetailInsertOnly(bizDate), ex),
                        CompletableFuture.runAsync(() -> mergeEncarDetailInsertOnly(bizDate), ex),
                        CompletableFuture.runAsync(() -> mergeKcarDetailInsertOnly(bizDate), ex),
                        CompletableFuture.runAsync(() -> mergeChutchaDetailInsertOnly(bizDate), ex),
                        CompletableFuture.runAsync(() -> mergeCharanchaDetailInsertOnly(bizDate), ex),
                        CompletableFuture.runAsync(() -> mergeTcarDetailInsertOnly(bizDate), ex)
                ).join();
            } finally { ex.shutdown(); }
        } else {
            mergeChachachaDetailInsertOnly(bizDate);
            mergeEncarDetailInsertOnly(bizDate);
            mergeKcarDetailInsertOnly(bizDate);
            mergeChutchaDetailInsertOnly(bizDate);
            mergeCharanchaDetailInsertOnly(bizDate);
            mergeTcarDetailInsertOnly(bizDate);
        }
        
        // 재생성 후 개수 조회
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM platform_car", Integer.class);
        return count != null ? count : 0;
    }

    /** CHACHACHA INSERT만 (ON DUPLICATE KEY UPDATE 제거) */
    private void mergeChachachaDetailInsertOnly(LocalDate bizDate) {
        long from = 0L;
        final String lockName = "merge:CHACHACHA";
        String bizDateStr = bizDate.toString();
        
        while (true) {
            Long to = nextUpperIdFor("raw_chachacha", from, UPSERT_BATCH_SIZE);
            if (to == null) break;
            final long cursorFrom = from;
            final long cursorTo   = to;

            inTxWithNamedLock(lockName, () -> {
                String sellNoExpr = "COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.sell_no')),\n" +
                        "                     JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.sellNo')),\n" +
                        "                     JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.SELL_NO')),\n" +
                        "                     JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.car_seq_no')),\n" +
                        "                     '')";
                String carNoExpr = "COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.car_no')),\n" +
                        "                    JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.carNo')),\n" +
                        "                    JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.CAR_NO')),\n" +
                        "                    '')";
                String makerCodeExpr = "COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.maker_code')),\n" +
                        "                        JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.makerCode')),\n" +
                        "                        JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.makerCd')),\n" +
                        "                        '')";
                String modelGroupCodeExpr = "COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.model_code')),\n" +
                        "                            JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.modelCode')),\n" +
                        "                            JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.model_cd')),\n" +
                        "                            '')";
                String modelCodeExpr = "COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.model_detail_code')),\n" +
                        "                    JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.modelDetailCode')),\n" +
                        "                    JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.model_detail')),\n" +
                        "                    '')";
                String gradeCodeExpr = "COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.grade_code')),\n" +
                        "                   JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.gradeCode')),\n" +
                        "                   JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.grade_cd')),\n" +
                        "                   '')";
                String makerNameExpr = "COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.maker_name')),\n" +
                        "                   JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.makerName')),\n" +
                        "                   JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.makerNm')),\n" +
                        "                   '')";
                String modelGroupNameExpr = "COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.model_group_name')),\n" +
                        "                         JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.modelGroupName')),\n" +
                        "                         JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.model_name')),\n" +
                        "                         JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.modelName')),\n" +
                        "                         '')";
                String modelNameExpr = "COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.model_name')),\n" +
                        "                    JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.modelName')),\n" +
                        "                    JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.modelNm')),\n" +
                        "                    '')";
                String trimNameExpr = "COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.model_detail_name')),\n" +
                        "                   JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.modelDetailName')),\n" +
                        "                   JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.trim_name')),\n" +
                        "                   JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.trimName')),\n" +
                        "                   '')";
                String gradeNameExpr = "COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.grade_name')),\n" +
                        "                    JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.gradeName')),\n" +
                        "                    JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.gradeNm')),\n" +
                        "                    '')";
                String priceExpr = "CAST(NULLIF(REPLACE(TRIM(CAST(COALESCE(\n" +
                        "  JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.sell_price')),\n" +
                        "  JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.sellPrice')),\n" +
                        "  ''\n" +
                        " ) AS CHAR)), ',', ''), '') AS UNSIGNED)";
                String kmExpr = "CAST(NULLIF(REPLACE(TRIM(CAST(COALESCE(\n" +
                        "  JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.mileage')),\n" +
                        "  JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.runDistance')),\n" +
                        "  ''\n" +
                        " ) AS CHAR)), ',', ''), '') AS UNSIGNED)";
                String displacementExpr = "CAST(NULLIF(REPLACE(TRIM(CAST(COALESCE(\n" +
                        "  JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.displacement')),\n" +
                        "  JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.capacity')),\n" +
                        "  ''\n" +
                        " ) AS CHAR)), ',', ''), '') AS UNSIGNED)";
                String yyyymmExpr = "COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.yyyymm')),\n" +
                        "                    JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.yyymm')),\n" +
                        "                    JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.carYear')))";
                String carTypeExpr = "COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.car_type')),\n" +
                        "                    JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.carType')),\n" +
                        "                    '')";
                String regionExpr = "COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.region_name')),\n" +
                        "                   JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.regionName')),\n" +
                        "                   JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.areaNm')),\n" +
                        "                   '')";
                String transmissionExpr = "COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.transmission_name')),\n" +
                        "                          JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.transmissionName')),\n" +
                        "                          JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.transmission')),\n" +
                        "                          '')";
                String colorExpr = "COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.color_name')),\n" +
                        "                  JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.colorName')),\n" +
                        "                  JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.color')))";
                String fuelExpr = "COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.fuel_name')),\n" +
                        "                 JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.fuelName')),\n" +
                        "                 JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.fuel')))";
                String sql = """
                    INSERT IGNORE INTO platform_car
                      (platform_name, platform_car_key, car_no, car_id,
                       maker_code, model_group_code, model_code, trim_code, grade_code,
                       maker_name, model_group_name, model_name, trim_name, grade_name,
                       price, km, displacement, yymm, status, color, fuel, transmission, body_type, region,
                       m_url, pc_url, first_ad_day, ad_date, created_at, updated_at, extra, last_seen_date, car_image_url,
                       option_array)
                    SELECT
                       'CHACHACHA', r.car_seq, r.car_no, NULL,
                       r.MAKER_CODE, r.CLASS_CODE, r.CAR_CODE, r.MODEL_CODE, r.GRADE_CODE,
                       r.MAKER_NAME, r.CLASS_NAME, r.CAR_NAME, r.MODEL_NAME, r.GRADE_NAME,
                       r.SELL_AMT, r.KM, r.displacement, r.YYMM, 'ONSALE', COLOR_EXPR, FUEL_EXPR, r.auto_gbn_name, r.use_code_name, r.REGION,
                       CONCAT('https://m.kbchachacha.com/public/web/car/detail.kbc?carSeq=', r.CAR_SEQ),
                       CONCAT('https://www.kbchachacha.com/public/car/detail.kbc?carSeq=', r.car_seq),
                       r.FIRST_AD_DAY, AD_DATE_EXPR_CHACHACHA, NOW(), NOW(), r.payload, DATE('BIZ_DATE_PLACEHOLDER'), r.car_image_url,
                       r.option_array
                    FROM raw_chachacha r
                    WHERE r.id > ? AND r.id <= ?
                """.replace("BIZ_DATE_PLACEHOLDER", bizDateStr)
                   .replace("AD_DATE_EXPR_CHACHACHA", AD_DATE_EXPR_CHACHACHA)
                   .replace("COLOR_EXPR", mapColorExpr("r.COLOR"))
                   .replace("FUEL_EXPR", mapFuelExpr("r.GAS_NAME"));
                jdbc.update(sql, cursorFrom, cursorTo);
                return null;
            });

            from = cursorTo;
        }
    }

    /** ENCAR INSERT만 */
    private void mergeEncarDetailInsertOnly(LocalDate bizDate) {
        mergeEncarDetailInsertOnlyBySource(bizDate, "raw_encar", true, "merge:ENCAR", "ENCAR");
        mergeEncarDetailInsertOnlyBySource(bizDate, "raw_encar_truck", false, "merge:ENCAR_TRUCK", "ENCAR_TRUCK");
    }

    private void mergeEncarDetailInsertOnlyBySource(
            LocalDate bizDate,
            String rawTable,
            boolean requireNormalSellType,
            String lockName,
            String platformName
    ) {
        String sourceTable = validateEncarRawTable(rawTable);
        long from = 0L;
        String bizDateStr = bizDate.toString();

        while (true) {
            Long to = nextUpperIdFor(sourceTable, from, UPSERT_BATCH_SIZE);
            if (to == null) break;
            final long cursorFrom = from;
            final long cursorTo   = to;

            inTxWithNamedLock(lockName, () -> {
                String sellTypeFilter = requireNormalSellType ? " AND r.sell_type = 'NORMAL'" : "";
                String sql = """
                    INSERT IGNORE INTO platform_car
                      (platform_name, platform_car_key, car_no, car_id,
                       maker_code, model_group_code, model_code, trim_code, grade_code,
                       maker_name, model_group_name, model_name, trim_name, grade_name,
                       price, price_new, km, displacement, yymm, status, color, fuel, transmission, body_type, region,
                       m_url, pc_url, first_ad_day, ad_date, created_at, updated_at, extra, last_seen_date, car_image_url,
                       option_array, sel_option_array, seat_count, my_accident_cnt, flood_total_loss_cnt)
                    SELECT DISTINCT
                      'PLATFORM_NAME_LITERAL', r.vehicle_id, r.vehicle_no, NULL,
                      r.manufacturer_code, r.model_group_code, r.model_code, r.grade_code, r.grade_detail_code,
                      r.manufacturer_name, r.model_group_name, r.model_name, r.grade_name, r.grade_detail_name,
                      COALESCE(CAST(REPLACE(JSON_UNQUOTE(JSON_EXTRACT(r.payload,'$.advertisement.price')), ',', '') AS UNSIGNED), r.price),
                      NULLIF(r.price_new, 0),
                      r.mileage, r.displacement , r.form_year,
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload,'$.advertisement.status')),
                      COLOR_EXPR, FUEL_EXPR, r.transmission,
                      CASE r.body_type
                        WHEN '준중형차' THEN '준중형' WHEN '경차' THEN '경차' WHEN '중형차' THEN '중형'
                        WHEN 'SUV' THEN 'SUV' WHEN '소형차' THEN '소형' WHEN '대형차' THEN '대형'
                        WHEN 'RV' THEN 'RV' WHEN '기타' THEN '기타' WHEN '스포츠카' THEN '스포츠카'
                        WHEN '화물차' THEN '트럭' WHEN '승합차' THEN '승합' WHEN '경승합차' THEN '승합'
                        ELSE r.body_type
                      END,
                      r.region,
                      CONCAT('https://fem.encar.com/cars/detail/', r.vehicle_id),
                      CONCAT('https://fem.encar.com/cars/detail/', r.vehicle_id),
                      DATE_FORMAT(r.first_ad_dt, '%Y%m%d'),
                      AD_DATE_EXPR_ENCAR, NOW(), NOW(), r.payload, DATE('BIZ_DATE_PLACEHOLDER'), r.car_image_url,
                      r.option_array, r.sel_option_array, r.seat_count, r.my_accident_cnt, r.flood_total_loss_cnt
                    FROM RAW_TABLE r
                    WHERE r.id > ? AND r.id <= ?
                      AND COALESCE(r.use_yn, 'Y') = 'Y'
                      SELL_TYPE_FILTER
                """.replace("BIZ_DATE_PLACEHOLDER", bizDateStr)
                   .replace("AD_DATE_EXPR_ENCAR", AD_DATE_EXPR_ENCAR)
                   .replace("RAW_TABLE", sourceTable)
                   .replace("SELL_TYPE_FILTER", sellTypeFilter)
                   .replace("PLATFORM_NAME_LITERAL", platformName)
                   .replace("COLOR_EXPR", mapColorExpr("r.color"))
                   .replace("FUEL_EXPR", mapFuelExpr("r.fuel"));
                jdbc.update(sql, cursorFrom, cursorTo);
                return null;
            });

            from = cursorTo;
        }
    }

    private String validateEncarRawTable(String rawTable) {
        if ("raw_encar".equals(rawTable) || "raw_encar_truck".equals(rawTable)) {
            return rawTable;
        }
        throw new IllegalArgumentException("unsupported ENCAR raw table: " + rawTable);
    }

    /** KCAR INSERT만 */
    private void mergeKcarDetailInsertOnly(LocalDate bizDate) {
        long from = 0L;
        final String lockName = "merge:KCAR";
        String bizDateStr = bizDate.toString();
        
        while (true) {
            Long to = nextUpperIdFor("raw_kcar", from, UPSERT_BATCH_SIZE);
            if (to == null) break;
            final long cursorFrom = from;
            final long cursorTo   = to;

            inTxWithNamedLock(lockName, () -> {
                String sql = """
                    INSERT IGNORE INTO platform_car
                      (platform_name, platform_car_key, car_no, car_id,
                       maker_code, model_group_code, model_code, trim_code, grade_code,
                       maker_name, model_group_name, model_name, trim_name, grade_name,
                       price, km, displacement, yymm, status, color, fuel, transmission, body_type, region,
                       m_url, pc_url, first_ad_day, ad_date, created_at, updated_at, extra, last_seen_date, car_image_url,
                       option_array)
                    SELECT
                      'KCAR', r.car_cd, r.cno, NULL,
                      r.maker_code, r.model_group_code, r.model_code, r.grade_code, r.grade_detail_code,
                      r.maker_name, r.model_group_name, r.model_name, r.grade_name, r.grade_detail_name,
                      r.price, r.mileage, r.displacement, r.yymm, 'SALE',
                      COLOR_EXPR, FUEL_EXPR, r.transmission,
                      CASE r.body_type
                        WHEN '중형차' THEN '중형' WHEN 'SUV' THEN 'SUV' WHEN '대형차' THEN '대형'
                        WHEN '경차' THEN '경차' WHEN '준중형차' THEN '준중형' WHEN '화물차' THEN '화물'
                        WHEN 'RV' THEN 'RV' WHEN '소형차' THEN '소형' WHEN '승합차' THEN '승합'
                        WHEN '스포츠카' THEN '스포츠카'
                        ELSE r.body_type
                      END,
                      r.region,
                      CONCAT('https://m.kcar.com/bc/detail/carInfoDtl?i_sCarCd=', r.car_cd),
                      CONCAT('https://www.kcar.com/bc/detail/carInfoDtl?i_sCarCd=', r.car_cd),
                      NULL, AD_DATE_EXPR_KCAR, NOW(), NOW(), r.payload, DATE('BIZ_DATE_PLACEHOLDER'), r.main_img,
                      r.option_array
                    FROM raw_kcar r
                    WHERE r.id > ? AND r.id <= ?
                """.replace("BIZ_DATE_PLACEHOLDER", bizDateStr)
                   .replace("AD_DATE_EXPR_KCAR", AD_DATE_EXPR_KCAR)
                   .replace("COLOR_EXPR", mapColorExpr("r.color"))
                   .replace("FUEL_EXPR", mapFuelExpr("r.fuel"));
                if (log.isDebugEnabled()) {
                    log.debug("[merge] KCAR insert-only SQL batch {} -> {}:\n{}", cursorFrom, cursorTo, sql);
                }
                jdbc.update(sql, cursorFrom, cursorTo);
                return null;
            });

            from = cursorTo;
        }
    }

    /** CHUTCHA INSERT만 */
    private void mergeChutchaDetailInsertOnly(LocalDate bizDate) {
        long from = 0L;
        final String lockName = "merge:CHUTCHA";
        String bizDateStr = bizDate.toString();
        
        while (true) {
            Long to = nextUpperIdFor("raw_chutcha", from, UPSERT_BATCH_SIZE);
            if (to == null) break;
            final long cursorFrom = from;
            final long cursorTo   = to;

            inTxWithNamedLock(lockName, () -> {
                String sql = """
                    INSERT IGNORE INTO platform_car
                      (platform_name, platform_car_key, car_no, car_id,
                       maker_name, model_group_name, model_name, trim_name, grade_name,
                       price, km, displacement, yymm, status, color, fuel, transmission, body_type, region,
                       m_url, pc_url, first_ad_day, ad_date, created_at, updated_at, extra, last_seen_date, car_image_url,
                       option_array)
                    SELECT
                      'CHUTCHA', r.car_id, r.number_plate, NULL,
                      r.brand_name, r.model_name, r.sub_model_name, r.grade_name, r.sub_grade_name,
                      r.price, r.mileage, r.displacement, r.first_reg_year, NULL,
                      COLOR_EXPR, FUEL_EXPR, r.transmission_name,
                      CASE r.car_type
                        WHEN '경차' THEN '경차' WHEN '중대형' THEN '중형' WHEN '대형' THEN '대형'
                        WHEN '준중형' THEN '준중형' WHEN 'SUV' THEN 'SUV' WHEN '소형' THEN '소형'
                        WHEN '스포츠카/쿠페' THEN '스포츠카' WHEN '상용' THEN '상용'
                        ELSE r.car_type
                      END,
                      r.shop_addr_short,
                      CONCAT('https://www.chutcha.net/share/car/detail/', JSON_UNQUOTE(JSON_EXTRACT(r.payload,'$.detail_link_hash'))),
                      CONCAT('https://web.chutcha.net/bmc/detail/', JSON_UNQUOTE(JSON_EXTRACT(r.payload,'$.detail_link_hash'))),
                      NULL, AD_DATE_EXPR_CHUTCHA, NOW(), NOW(), r.payload, DATE('BIZ_DATE_PLACEHOLDER'), r.car_image_url,
                      r.option_array
                    From raw_chutcha r
                    WHERE r.id > ? AND r.id <= ? AND r.CAR_ID IS NOT NULL
                """.replace("BIZ_DATE_PLACEHOLDER", bizDateStr)
                   .replace("AD_DATE_EXPR_CHUTCHA", AD_DATE_EXPR_CHUTCHA)
                   .replace("COLOR_EXPR", mapColorExpr("r.color"))
                   .replace("FUEL_EXPR", mapFuelExpr("r.fuel_name"));
                jdbc.update(sql, cursorFrom, cursorTo);
                return null;
            });

            from = cursorTo;
        }
    }

    /** CHARANCHA INSERT만 */
    private void mergeCharanchaDetailInsertOnly(LocalDate bizDate) {
        long from = 0L;
        final String lockName = "merge:CHARANCHA";
        String bizDateStr = bizDate.toString();
        
        while (true) {
            Long to = nextUpperIdFor("raw_charancha", from, UPSERT_BATCH_SIZE);
            if (to == null) break;
            final long cursorFrom = from;
            final long cursorTo   = to;

            inTxWithNamedLock(lockName, () -> {
                String sql = """
                    INSERT IGNORE INTO platform_car
                      (platform_name, platform_car_key, car_no, car_id,
                       maker_code, model_group_code, model_code, trim_code, grade_code,
                       maker_name, model_group_name, model_name, trim_name, grade_name,
                      price, km, displacement, yymm, status,
                      color, fuel, transmission, body_type, region,
                      m_url, pc_url,
                      first_ad_day, ad_date, created_at, updated_at, extra, last_seen_date, car_image_url)
                    SELECT
                      'CHARANCHA', CHARAN_NO_EXPR, CHARAN_CAR_NO_EXPR, NULL,
                      CHARAN_MAKER_CODE, CHARAN_MODEL_GROUP_CODE, CHARAN_MODEL_CODE, CHARAN_TRIM_CODE, CHARAN_GRADE_CODE,
                      CHARAN_MAKER_NAME, CHARAN_MODEL_GROUP_NAME, CHARAN_MODEL_NAME, CHARAN_TRIM_NAME, CHARAN_GRADE_NAME,
                      CHARAN_PRICE, CHARAN_KM, CHARAN_DISPLACEMENT,
                      SUBSTR(CHARAN_YYYMM,1,4), 'SALE',
                      COLOR_EXPR, FUEL_EXPR, CHARAN_TRANSMISSION,
                      CASE CHARAN_CAR_TYPE
                        WHEN '소형' THEN '소형' WHEN '중형' THEN '중형' WHEN '대형' THEN '대형'
                        WHEN '경형(일반형)' THEN '경차' WHEN '준중형' THEN '준중형'
                        WHEN '기타' THEN '기타' WHEN '경형(초소형)' THEN '경차'
                        ELSE CHARAN_CAR_TYPE
                      END,
                      CHARAN_REGION,
                      CONCAT('https://charancha.com/bu/sell/view?sellNo=', CHARAN_NO_EXPR),
                      CONCAT('https://charancha.com/bu/sell/view?sellNo=', CHARAN_NO_EXPR),
                      NULL, AD_DATE_EXPR_CHARANCHA, NOW(), NOW(), r.payload, DATE('BIZ_DATE_PLACEHOLDER'), r.car_image_url
                    FROM raw_charancha r
                    WHERE r.id > ? AND r.id <= ?
                """.replace("BIZ_DATE_PLACEHOLDER", bizDateStr)
                   .replace("AD_DATE_EXPR_CHARANCHA", AD_DATE_EXPR_CHARANCHA)
                   .replace("CHARAN_NO_EXPR", "r.sell_no")
                   .replace("CHARAN_CAR_NO_EXPR", "r.car_no")
                   .replace("CHARAN_MAKER_CODE", "r.maker_code")
                   .replace("CHARAN_MODEL_GROUP_CODE", "r.model_code")
                   .replace("CHARAN_MODEL_CODE", "r.model_detail_code")
                   .replace("CHARAN_TRIM_CODE", "r.grade_code")
                   .replace("CHARAN_GRADE_CODE", "NULL")
                   .replace("CHARAN_MAKER_NAME", "r.maker_name")
                   .replace("CHARAN_MODEL_GROUP_NAME", "COALESCE(r.model_name, '')")
                   .replace("CHARAN_MODEL_NAME", "r.model_name")
                   .replace("CHARAN_TRIM_NAME", "r.grade_name")
                   .replace("CHARAN_GRADE_NAME", "NULL")
                   .replace("CHARAN_PRICE", "r.sell_price")
                   .replace("CHARAN_KM", "r.mileage")
                   .replace("CHARAN_DISPLACEMENT", "r.displacement")
                   .replace("CHARAN_YYYMM", "r.yyyymm")
                   .replace("CHARAN_CAR_TYPE", "r.car_type")
                   .replace("CHARAN_REGION", "r.region_name")
                   .replace("CHARAN_TRANSMISSION", "r.transmission_name")
                   .replace("COLOR_EXPR", mapColorExpr("r.color_name"))
                   .replace("FUEL_EXPR", mapFuelExpr("r.fuel_name"));
                jdbc.update(sql, cursorFrom, cursorTo);
                return null;
            });

            from = cursorTo;
        }
    }

    /** TCAR INSERT만 */
    private void mergeTcarDetailInsertOnly(LocalDate bizDate) {
        long from = 0L;
        final String lockName = "merge:TCAR";
        String bizDateStr = bizDate.toString();
        
        while (true) {
            Long to = nextUpperIdFor("raw_tcar", from, UPSERT_BATCH_SIZE);
            if (to == null) break;
            final long cursorFrom = from;
            final long cursorTo   = to;

            inTxWithNamedLock(lockName, () -> {
                String sql = """
                    INSERT IGNORE INTO platform_car
                      (platform_name, platform_car_key, car_no, car_id,
                       maker_code, model_group_code, model_code, trim_code, grade_code,
                       maker_name, model_group_name, model_name, trim_name, grade_name,
                       price, price_new, km, displacement, yymm, status, color, fuel, transmission, body_type, region,
                       m_url, pc_url, first_ad_day, ad_date, created_at, updated_at, extra, last_seen_date, car_image_url)
                    SELECT
                      'TCAR', 
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.carId')),
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.plateNumber')),
                      NULL,
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.brandId')),
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.modelgroupId')),
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.modelId')),
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.subgradeId')),
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.gradeId')),
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.brandName')),
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.modelgroupName')),
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.modelName')),
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.subgradeName')),
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.gradeName')),
                      COALESCE(
                        (COALESCE(
                               CAST(NULLIF(REPLACE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.return_price')), ',', ''), 'null') AS UNSIGNED),
                               CAST(NULLIF(REPLACE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.returnPrice')), ',', ''), 'null') AS UNSIGNED))) DIV 10000,
                        COALESCE(
                               CAST(NULLIF(REPLACE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.price')), ',', ''), 'null') AS UNSIGNED),
                               CAST(NULLIF(REPLACE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.priceNew')), ',', ''), 'null') AS UNSIGNED),
                               CAST(NULLIF(REPLACE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.priceSell')), ',', ''), 'null') AS UNSIGNED),
                               CAST(NULLIF(REPLACE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.promotionPrice')), ',', ''), 'null') AS UNSIGNED))),
                      NULLIF(CAST(NULLIF(TRIM(REPLACE(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.priceNew')), ''), ',', '')), '') AS UNSIGNED) DIV 10000, 0),
                      CAST(NULLIF(REPLACE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.mileage')), ',', ''), 'null') AS UNSIGNED),
                      CAST(NULLIF(REPLACE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.displacement')), ',', ''), 'null') AS UNSIGNED),
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.regYear')),
                      CASE WHEN JSON_EXTRACT(r.payload, '$.status') = 0 THEN 'ONSALE' 
                           WHEN JSON_EXTRACT(r.payload, '$.status') = 1 THEN 'SOLD'
                           ELSE 'ONSALE' END,
                      COLOR_EXPR,
                      FUEL_EXPR,
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.trans')),
                      r.body_type,
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.areaCd')),
      CONCAT('https://mycarsave.lotterentacar.net/cr/search/view?carId=', JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.carId'))),
      CONCAT('https://mycarsave.lotterentacar.net/cr/search/view?carId=', JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.carId'))),
      CASE
        WHEN JSON_EXTRACT(r.payload, '$.postStartDt') IS NULL
             OR JSON_EXTRACT(r.payload, '$.postStartDt') = JSON_QUOTE('null')
             OR JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.postStartDt')) = 'null'
             OR JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.postStartDt')) = ''
             OR JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.postStartDt')) IS NULL
        THEN NULL
        ELSE DATE_FORMAT(STR_TO_DATE(JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.postStartDt')), '%Y-%m-%d %H:%i:%s.%f'), '%Y%m%d')
      END,
                      AD_DATE_EXPR_TCAR, NOW(), NOW(), r.payload, DATE('BIZ_DATE_PLACEHOLDER'), r.car_image_url2
                    FROM raw_tcar r
                    WHERE r.id > ? AND r.id <= ?
                      AND UPPER(TRIM(COALESCE(
                        JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.sale_type')),
                        JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.saleType')),
                        JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.SALE_TYPE')),
                        ''
                      ))) = 'S'
                """.replace("BIZ_DATE_PLACEHOLDER", bizDateStr)
                   .replace("AD_DATE_EXPR_TCAR", AD_DATE_EXPR_TCAR)
                   .replace("COLOR_EXPR", mapColorExpr("JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.color'))"))
                   .replace("FUEL_EXPR", mapFuelExpr("JSON_UNQUOTE(JSON_EXTRACT(r.payload, '$.fuel'))"));
                jdbc.update(sql, cursorFrom, cursorTo);
                return null;
            });

            from = cursorTo;
        }
    }

    public int linkToMaster() {
        final int CHUNK = LINK_BATCH_SIZE;
        int total = 0;
        long cursor = 0L;

        while (true) {
            final long curId = cursor; // 람다 캡쳐용 복사본

            ExecResult res = runWithRetry(3, 200L, () ->
                    requiresNew().execute(status -> {
                        String selectBatchSql = """
                        SELECT platform_car_id, car_no
                          FROM platform_car
                         WHERE car_id IS NULL
                           AND platform_car_id > ?
                         ORDER BY platform_car_id
                         LIMIT ?
                         FOR UPDATE SKIP LOCKED
                    """;
                        logBatchSql("linkToMaster.selectBatch", selectBatchSql, curId, CHUNK);
                        // 1) 작업 대상 청크 잠금
                        List<PcRow> batch = jdbc.query(selectBatchSql, (rs, i) -> new PcRow(rs.getLong(1), rs.getString(2)), curId, CHUNK);

                        if (batch.isEmpty()) return new ExecResult(0, curId);

                        long next = batch.get(batch.size() - 1).platformCarId();

                        // 후보 car_no
                        List<String> carNos = batch.stream()
                                .map(PcRow::carNo).filter(Objects::nonNull).distinct().toList();

                        // 2-a) 없는 car_no만 INSERT
                        if (!carNos.isEmpty()) {
                            String insertCarMasterSql = """
                            INSERT INTO car_master (car_no, created_at, updated_at)
                            SELECT :car_no, NOW(), NOW()
                            WHERE NOT EXISTS (SELECT 1 FROM car_master cm WHERE cm.car_no = :car_no)
                        """;
                            SqlParameterSource[] params = carNos.stream()
                                    .map(c -> new MapSqlParameterSource().addValue("car_no", c))
                                    .toArray(SqlParameterSource[]::new);

                            logBatchSql("linkToMaster.insertMissingCarMaster", insertCarMasterSql, "size=" + params.length);
                            npJdbc.batchUpdate(insertCarMasterSql, params);
                        }

                        // 2-b) 매핑 조회
                        String selectCarIdSql = """
                                SELECT car_no, MAX(car_id) AS car_id
                                  FROM car_master
                                 WHERE car_no IN (:nos)
                                 GROUP BY car_no
                            """;
                        if (!carNos.isEmpty()) {
                            logBatchSql("linkToMaster.selectCarId", selectCarIdSql, carNos.size());
                        }
                        Map<String, Long> cmByCarNo = carNos.isEmpty() ? Map.of() :
                                npJdbc.query(selectCarIdSql, Map.of("nos", carNos), rs -> {
                                    Map<String, Long> m = new HashMap<>();
                                    while (rs.next()) m.put(rs.getString("car_no"), rs.getLong("car_id"));
                                    return m;
                                });

                        // 3) platform_car.car_id 업데이트
                        List<Object[]> params = new ArrayList<>();
                        for (PcRow r : batch) {
                            Long cmId = (r.carNo() == null) ? null : cmByCarNo.get(r.carNo());
                            if (cmId != null) params.add(new Object[]{cmId, r.platformCarId()});
                        }
                        if (params.isEmpty()) return new ExecResult(0, next);

                        String updateSql = "UPDATE platform_car SET car_id = ? WHERE platform_car_id = ? AND car_id IS NULL";
                        logBatchSql("linkToMaster.updateBatch", updateSql, "batchSize=" + params.size());
                        int processed = Arrays.stream(
                                jdbc.batchUpdate(updateSql, params)
                        ).sum();

                        return new ExecResult(processed, next);
                    })
            );

            if (res == null || res.processed() == 0) break;
            cursor = res.nextCursor();
            total  += res.processed();
        }

        // car_id 링크 후: car_master.price_new가 비어 있으면 platform_car의 price_new로 채움
        runWithRetry(3, 200L, () ->
            requiresNew().execute(status -> {
                String updatePriceSql = """
                    UPDATE car_master cm
                    INNER JOIN (
                        SELECT car_id, MAX(price_new) AS price_new
                        FROM platform_car
                        WHERE car_id IS NOT NULL AND price_new IS NOT NULL AND price_new > 0
                        GROUP BY car_id
                    ) pc ON pc.car_id = cm.car_id
                    SET cm.price_new = pc.price_new, cm.updated_at = NOW()
                    WHERE cm.price_new IS NULL
                    """;
                logBatchSql("linkToMaster.backfillPriceNew", updatePriceSql);
                int filled = jdbc.update(updatePriceSql);
                if (filled > 0) log.info("[merge] backfill car_master.price_new from platform_car: {} rows", filled);
                return null;
            }));

        // car_id 링크 후: car_master.ad_date를 platform_car.ad_date의 최대값으로 동기화
        runWithRetry(3, 200L, () ->
            requiresNew().execute(status -> {
                String syncAdDateSql = """
                    UPDATE car_master cm
                    INNER JOIN (
                        SELECT car_id, MAX(ad_date) AS max_ad_date
                        FROM platform_car
                        WHERE car_id IS NOT NULL AND ad_date IS NOT NULL
                        GROUP BY car_id
                    ) pc ON pc.car_id = cm.car_id
                    SET cm.ad_date = pc.max_ad_date, cm.updated_at = NOW()
                    WHERE cm.ad_date IS NULL OR cm.ad_date <> pc.max_ad_date
                    """;
                logBatchSql("linkToMaster.syncAdDate", syncAdDateSql);
                int synced = jdbc.update(syncAdDateSql);
                if (synced > 0) log.info("[merge] sync car_master.ad_date from platform_car max: {} rows", synced);
                return null;
            }));

        // car_id 링크 후: ENCAR 확장 필드(seat/사고) 동기화
        runWithRetry(3, 200L, () ->
            requiresNew().execute(status -> {
                String syncAccidentSql = """
                    UPDATE car_master cm
                    INNER JOIN (
                        SELECT car_id,
                               MAX(seat_count) AS seat_count,
                               MAX(my_accident_cnt) AS my_accident_cnt,
                               MAX(flood_total_loss_cnt) AS flood_total_loss_cnt
                        FROM platform_car
                        WHERE car_id IS NOT NULL
                        GROUP BY car_id
                    ) pc ON pc.car_id = cm.car_id
                    SET cm.seat_count = COALESCE(pc.seat_count, cm.seat_count),
                        cm.my_accident_cnt = COALESCE(pc.my_accident_cnt, cm.my_accident_cnt),
                        cm.flood_total_loss_cnt = COALESCE(pc.flood_total_loss_cnt, cm.flood_total_loss_cnt),
                        cm.updated_at = NOW()
                    WHERE (pc.seat_count IS NOT NULL AND (cm.seat_count IS NULL OR cm.seat_count <> pc.seat_count))
                        OR (pc.my_accident_cnt IS NOT NULL AND (cm.my_accident_cnt IS NULL OR cm.my_accident_cnt <> pc.my_accident_cnt))
                       OR (pc.flood_total_loss_cnt IS NOT NULL AND (cm.flood_total_loss_cnt IS NULL OR cm.flood_total_loss_cnt <> pc.flood_total_loss_cnt))
                    """;
                logBatchSql("linkToMaster.syncSeatAccident", syncAccidentSql);
                int synced = jdbc.update(syncAccidentSql);
                if (synced > 0) log.info("[merge] sync car_master seat/accident fields from platform_car: {} rows", synced);
                return null;
            }));

        // car_id 링크 후: option_array/sel_option_array 동기화 (최신 ENCAR 계열 우선)
        runWithRetry(3, 200L, () ->
            requiresNew().execute(status -> {
                String syncOptionSql = """
                    UPDATE car_master cm
                    SET cm.option_array = COALESCE((
                            SELECT pc.option_array
                            FROM platform_car pc
                            WHERE pc.car_id = cm.car_id
                              AND pc.option_array IS NOT NULL
                              AND TRIM(pc.option_array) <> ''
                            ORDER BY (pc.platform_name IN ('ENCAR', 'ENCAR_TRUCK')) DESC,
                                     pc.ad_date DESC,
                                     pc.updated_at DESC,
                                     pc.platform_car_id DESC
                            LIMIT 1
                        ), cm.option_array),
                        cm.sel_option_array = COALESCE((
                            SELECT pc.sel_option_array
                            FROM platform_car pc
                            WHERE pc.car_id = cm.car_id
                              AND pc.sel_option_array IS NOT NULL
                              AND TRIM(pc.sel_option_array) <> ''
                            ORDER BY (pc.platform_name IN ('ENCAR', 'ENCAR_TRUCK')) DESC,
                                     pc.ad_date DESC,
                                     pc.updated_at DESC,
                                     pc.platform_car_id DESC
                            LIMIT 1
                        ), cm.sel_option_array),
                        cm.updated_at = NOW()
                    WHERE cm.car_id IS NOT NULL
                      AND (
                           (cm.option_array IS NULL AND EXISTS (
                               SELECT 1
                               FROM platform_car p1
                               WHERE p1.car_id = cm.car_id
                                 AND p1.option_array IS NOT NULL
                                 AND TRIM(p1.option_array) <> ''
                           ))
                        OR (cm.sel_option_array IS NULL AND EXISTS (
                              SELECT 1
                              FROM platform_car p2
                               WHERE p2.car_id = cm.car_id
                                 AND p2.sel_option_array IS NOT NULL
                                 AND TRIM(p2.sel_option_array) <> ''
                           ))
                      )
                    """;
                logBatchSql("linkToMaster.syncOptionArrays", syncOptionSql);
                int synced = jdbc.update(syncOptionSql);
                if (synced > 0) log.info("[merge] sync car_master option arrays from platform_car: {} rows", synced);
                return null;
            }));

        log.info("linkToMaster linked rows: {}", total);
        return total;
    }

    /** platform_car.car_id가 car_master를 참조하지 않으면 NULL로 정리 */
    public int normalizePlatformCarCarIdRefs() {
        return runWithRetry(3, 200L, () ->
                requiresNew().execute(status -> {
                    String sql = """
                        UPDATE platform_car p
                        LEFT JOIN car_master cm
                               ON cm.CAR_ID = p.CAR_ID
                        SET p.CAR_ID = NULL
                        WHERE p.CAR_ID IS NOT NULL
                          AND cm.CAR_ID IS NULL
                    """;
                    logBatchSql("normalizePlatformCarCarIdRefs", sql);
                    int updated = jdbc.update(sql);
                    log.info("[merge] normalizePlatformCarCarIdRefs done: {}", updated);
                    return updated;
                })
        );
    }

    /* ========== 3) 가격 스냅샷 & 미노출 처리 (각 단계 REQUIRES_NEW 커밋) ========== */

    public void snapshotPrices(LocalDate bizDate) {
        runWithRetry(3, 200L, () ->
                requiresNew().execute(status -> {
                    jdbc.update("""
                    INSERT INTO car_price_history (platform_car_id, price, checked_at, is_current)
                    SELECT p.platform_car_id, p.price, NOW(), 1
                      FROM platform_car p
                     WHERE p.last_seen_date = ?
                       AND NOT EXISTS (
                         SELECT 1 FROM car_price_history h
                          WHERE h.platform_car_id = p.platform_car_id
                            AND h.is_current = 1
                            AND h.price = p.price
                       )
                """, bizDate);

                    jdbc.update("""
                    UPDATE car_price_history h
                    JOIN platform_car p
                      ON h.platform_car_id = p.platform_car_id
                     AND p.last_seen_date = ?
                    SET h.is_current = 0, h.last_seen_at = NOW()
                   WHERE h.is_current = 1
                     AND h.price <> p.price
                """, bizDate);

                    jdbc.update("""
                    UPDATE car_price_history h
                    JOIN platform_car p
                      ON h.platform_car_id = p.platform_car_id
                     AND h.price = p.price
                     AND p.last_seen_date = ?
                   SET h.last_seen_at = NOW()
                  WHERE h.is_current = 1
                """, bizDate);
                    return null;
                })
        );
    }

    public void closeMissingAds(LocalDate bizDate) {
        final int BATCH_SIZE = 5000; // 배치 크기
        
        runWithRetry(3, 200L, () ->
                requiresNew().execute(status -> {
                    // platform_car에서 오늘 날짜에 보이지 않는 차량을 SOLD 처리
                    // 서브쿼리 + 배치 처리로 성능 최적화 (MySQL UPDATE JOIN LIMIT 제한 회피)
                    int totalAffected = 0;
                    int currentBatch = 0;
                    
                    do {
                        // 서브쿼리로 먼저 업데이트할 car_no 선택 후 IN 절로 업데이트
                        currentBatch = jdbc.update("""
                        UPDATE car_master m
                        SET m.adv_status = 'SOLD', m.updated_at = NOW()
                        WHERE m.car_no IN (
                            SELECT car_no FROM (
                                SELECT m2.car_no
                                FROM car_master m2
                                LEFT JOIN (
                                    SELECT DISTINCT car_no
                                    FROM platform_car
                                    WHERE last_seen_date = ?
                                      AND car_no IS NOT NULL
                                ) p ON p.car_no = m2.car_no
                                WHERE p.car_no IS NULL
                                  AND m2.adv_status = 'ONSALE'
                                LIMIT ?
                            ) AS subquery
                        )
                        """, bizDate, BATCH_SIZE);
                        
                        totalAffected += currentBatch;
                        
                        // 배치 처리 간 짧은 대기 (락 완화)
                        if (currentBatch > 0) {
                            try {
                                Thread.sleep(10);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    } while (currentBatch == BATCH_SIZE);
                    
                    log.info("[SOLD] closeMissingAds done: {} rows", totalAffected);
                    return null;
                })
        );
    }
}

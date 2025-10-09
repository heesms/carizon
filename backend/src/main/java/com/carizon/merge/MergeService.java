package com.carizon.merge;

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

import java.sql.Date;
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

    // 잠금 경합 줄이려면 우선 작게. 상황 봐가며 키워도 됨.
    private static final int UPSERT_BATCH_SIZE = 1_000; // raw_* → platform_car
    private static final int LINK_BATCH_SIZE   = 1_000; // platform_car → car_master
    private static final boolean PARALLEL_ALL  = false;

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
        if (PARALLEL_ALL) {
            var ex = Executors.newFixedThreadPool(4);
            try {
                CompletableFuture.allOf(
                        CompletableFuture.runAsync(() -> mergeChachachaDetail(bizDate), ex),
                        CompletableFuture.runAsync(() -> mergeEncarDetail(bizDate), ex),
                        CompletableFuture.runAsync(() -> mergeKcarDetail(bizDate), ex),
                        CompletableFuture.runAsync(() -> mergeChutchaDetail(bizDate), ex),
                        CompletableFuture.runAsync(() -> mergeCharanchaDetail(bizDate), ex)
                ).join();
            } finally { ex.shutdown(); }
        } else {
            mergeChachachaDetail(bizDate);
            mergeEncarDetail(bizDate);
            mergeKcarDetail(bizDate);
            mergeChutchaDetail(bizDate);
            mergeCharanchaDetail(bizDate);
        }
        return postProcess(bizDate);
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
                int affected = jdbc.update("""
                    INSERT INTO platform_car
                      (platform_name, platform_car_key, car_no, car_id,
                       maker_code, model_group_code, model_code, trim_code, grade_code,
                       maker_name, model_group_name, model_name, trim_name, grade_name,
                       price, km, displacement, yymm, status, color, fuel, transmission, body_type, region,
                       m_url, pc_url, first_ad_day, created_at, updated_at, extra, last_seen_date)
                    SELECT
                       'CHACHACHA', r.car_seq, r.car_no, NULL,
                       r.MAKER_CODE, r.CLASS_CODE, r.CAR_CODE, r.MODEL_CODE, r.GRADE_CODE,
                       r.MAKER_NAME, r.CLASS_NAME, r.CAR_NAME, r.MODEL_NAME, r.GRADE_NAME,
                       r.SELL_AMT, r.KM, R.displacement, r.YYMM, 'ONSALE', r.COLOR, r.GAS_NAME, r.auto_gbn_name, r.use_code_name, r.REGION,
                       CONCAT('https://m.kbchachacha.com/public/web/car/detail.kbc?carSeq=', r.CAR_SEQ),
                       CONCAT('https://www.kbchachacha.com/public/car/detail.kbc?carSeq=', r.car_seq),
                       r.FIRST_AD_DAY, NOW(), NOW(), r.payload, ?
                    FROM raw_chachacha r
                    WHERE r.id > ? AND r.id <= ?
                    ON DUPLICATE KEY UPDATE
                       price          = VALUES(price),
                       status         = VALUES(status),
                       extra          = VALUES(extra),
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
                """, Date.valueOf(bizDate), cursorFrom, cursorTo);
                log.debug("CHACHACHA upsert affected={}", affected);
                return null;
            });

            from = cursorTo;
        }
    }

    public void mergeEncarDetail(LocalDate bizDate) {
        long from = 0L;
        final String lockName = "merge:ENCAR";
        while (true) {
            Long to = nextUpperIdFor("raw_encar", from, UPSERT_BATCH_SIZE);
            if (to == null) break;
            final long cursorFrom = from;
            final long cursorTo   = to;

            inTxWithNamedLock(lockName, () -> {
                int affected = jdbc.update("""
                    INSERT INTO platform_car
                      (platform_name, platform_car_key, car_no, car_id,
                       maker_code, model_group_code, model_code, trim_code, grade_code,
                       maker_name, model_group_name, model_name, trim_name, grade_name,
                       price, km, displacement, yymm, status, color, fuel, transmission, body_type, region,
                       m_url, pc_url, first_ad_day, created_at, updated_at, extra, last_seen_date)
                    SELECT
                      'ENCAR', r.vehicle_id, r.vehicle_no, NULL,
                      r.manufacturer_code, r.model_group_code, r.model_code, r.grade_code, r.grade_detail_code,
                      r.manufacturer_name, r.model_group_name, r.model_name, r.grade_name, r.grade_detail_name,
                      COALESCE(CAST(REPLACE(JSON_UNQUOTE(JSON_EXTRACT(r.payload,'$.advertisement.price')), ',', '') AS UNSIGNED), r.price),
                      r.mileage, R.displacement , r.form_year,
                      JSON_UNQUOTE(JSON_EXTRACT(r.payload,'$.advertisement.status')),
                      r.color, r.fuel, r.transmission, r.body_type, r.region,
                      CONCAT('https://fem.encar.com/cars/detail/', r.vehicle_id),
                      CONCAT('https://fem.encar.com/cars/detail/', r.vehicle_id),
                      DATE_FORMAT(r.first_ad_dt, '%Y%m%d'),
                      NOW(), NOW(), r.payload, ?
                    FROM raw_encar r
                    WHERE r.id > ? AND r.id <= ?
                    ON DUPLICATE KEY UPDATE
                      price          = VALUES(price),
                      status         = VALUES(status),
                      extra          = VALUES(extra),
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
                """, Date.valueOf(bizDate), cursorFrom, cursorTo);
                log.debug("ENCAR upsert affected={}", affected);
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
                int affected = jdbc.update("""
                    INSERT INTO platform_car
                      (platform_name, platform_car_key, car_no, car_id,
                       maker_code, model_group_code, model_code, trim_code, grade_code,
                       maker_name, model_group_name, model_name, trim_name, grade_name,
                       price, km, displacement, yymm, status, color, fuel, transmission, body_type, region,
                       m_url, pc_url, first_ad_day, created_at, updated_at, extra, last_seen_date)
                    SELECT
                      'KCAR', r.car_cd, r.cno, NULL,
                      r.maker_code, r.model_group_code, r.model_code, r.grade_code, r.grade_detail_code,
                      r.maker_name, r.model_group_name, r.model_name, r.grade_name, r.grade_detail_name,
                      r.price, r.mileage, R.displacement, r.yymm,
                      'SALE',
                      r.color, r.fuel, r.transmission, r.body_type, r.region,
                      CONCAT('https://m.kcar.com/bc/detail/carInfoDtl?i_sCarCd=', r.car_cd),
                      CONCAT('https://www.kcar.com/bc/detail/carInfoDtl?i_sCarCd=', r.car_cd),
                      NULL, NOW(), NOW(), r.payload, ?
                    FROM raw_kcar r
                    WHERE r.id > ? AND r.id <= ?
                    ON DUPLICATE KEY UPDATE
                      price          = VALUES(price),
                      status         = VALUES(status),
                      extra          = VALUES(extra),
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
                """, Date.valueOf(bizDate), cursorFrom, cursorTo);
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
                int affected = jdbc.update("""
                    INSERT INTO platform_car
                      (platform_name, platform_car_key, car_no, car_id,
                       maker_name, model_group_name, model_name, trim_name, grade_name,
                       price, km, displacement, yymm, status, color, fuel, transmission, body_type, region,
                       m_url, pc_url, first_ad_day, created_at, updated_at, extra, last_seen_date)
                    SELECT
                      'CHUTCHA', r.car_id, r.number_plate, NULL,
                      r.brand_name, r.model_name, r.sub_model_name, r.grade_name, r.sub_grade_name,
                      r.price, r.mileage, R.displacement, r.first_reg_year, NULL,
                      r.color, r.fuel_name, r.transmission_name, r.car_type, r.shop_addr_short,
                      CONCAT('https://www.chutcha.net/share/car/detail/', JSON_UNQUOTE(JSON_EXTRACT(r.payload,'$.detail_link_hash'))),
                      CONCAT('https://web.chutcha.net/bmc/detail/', JSON_UNQUOTE(JSON_EXTRACT(r.payload,'$.detail_link_hash'))),
                      NULL, NOW(), NOW(), r.payload, ?
                    FROM raw_chutcha r
                    WHERE r.id > ? AND r.id <= ? AND r.CAR_ID IS NOT NULL
                    ON DUPLICATE KEY UPDATE
                      price          = VALUES(price),
                      extra          = VALUES(extra),
                      last_seen_date = VALUES(last_seen_date),
                      updated_at     = NOW(),
                      car_no = COALESCE(platform_car.car_no, VALUES(car_no)),
                      maker_name = COALESCE(platform_car.maker_name, VALUES(maker_name)),
                      model_group_name = COALESCE(platform_car.model_group_name, VALUES(model_group_name)),
                      model_name = COALESCE(platform_car.model_name, VALUES(model_name)),
                      trim_name = COALESCE(platform_car.trim_name, VALUES(trim_name)),
                      grade_name = COALESCE(platform_car.grade_name, VALUES(grade_name))
                """, Date.valueOf(bizDate), cursorFrom, cursorTo);
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
                int affected = jdbc.update("""
                    INSERT INTO platform_car
                                          (platform_name, platform_car_key, car_no, car_id,
                                           maker_code, model_group_code, model_code, trim_code,
                                           maker_name, model_group_name, model_name, trim_name,
                                           price, km, displacement, yymm, status,
                                           color, fuel, transmission, body_type, region,
                                           m_url, pc_url,
                                           first_ad_day, created_at, updated_at, extra, last_seen_date)
                    
                    SELECT
                    'CHARANCHA', R.SELL_NO, R.CAR_NO, NULL,
                    R.maker_code, R.model_code, R.model_detail_code, R.grade_code,
                    R.maker_name, R.model_name, R.model_detail_name, R.grade_name,
                    r.sell_price, r.mileage, r.displacement, substr(r.yyyymm,1,4), 'SALE',
                    R.color_name, R.fuel_name, R.transmission_name, R.car_type, r.region_name,
                    CONCAT('https://charancha.com/bu/sell/view?sellNo=', r.SELL_NO),
                    CONCAT('https://charancha.com/bu/sell/view?sellNo=', r.SELL_NO),
                      NULL, NOW(), NOW(), r.payload, ?
                    FROM RAW_CHARANCHA r
                    WHERE r.id > ? AND r.id <= ?
                    ON DUPLICATE KEY UPDATE
                      price          = VALUES(price),
                      status         = VALUES(status),
                      extra          = VALUES(extra),
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
                """, Date.valueOf(bizDate), cursorFrom, cursorTo);
                log.debug("CHARANCHA upsert affected={}", affected);
                return null;
            });

            from = cursorTo;
        }
    }

    /* ========== 2) master INSERT & car_id 매핑 (청크별 REQUIRES_NEW 커밋) ========== */

    static record PcRow(long platformCarId, String carNo) {}
    static record ExecResult(int processed, long nextCursor) {}

    public int postProcess(LocalDate bizDate) {
        int linked = linkToMaster();
        snapshotPrices(bizDate);
        closeMissingAds(bizDate);
        return linked;
    }

    public int linkToMaster() {
        final int CHUNK = LINK_BATCH_SIZE;
        int total = 0;
        long cursor = 0L;

        while (true) {
            final long curId = cursor; // 람다 캡쳐용 복사본

            ExecResult res = runWithRetry(3, 200L, () ->
                    requiresNew().execute(status -> {
                        // 1) 작업 대상 청크 잠금
                        List<PcRow> batch = jdbc.query("""
                        SELECT platform_car_id, car_no
                          FROM platform_car
                         WHERE car_id IS NULL
                           AND platform_car_id > ?
                         ORDER BY platform_car_id
                         LIMIT ?
                         FOR UPDATE SKIP LOCKED
                    """, (rs, i) -> new PcRow(rs.getLong(1), rs.getString(2)), curId, CHUNK);

                        if (batch.isEmpty()) return new ExecResult(0, curId);

                        long next = batch.get(batch.size() - 1).platformCarId();

                        // 후보 car_no
                        List<String> carNos = batch.stream()
                                .map(PcRow::carNo).filter(Objects::nonNull).distinct().toList();

                        // 2-a) 없는 car_no만 INSERT
                        if (!carNos.isEmpty()) {
                            SqlParameterSource[] params = carNos.stream()
                                    .map(c -> new MapSqlParameterSource().addValue("car_no", c))
                                    .toArray(SqlParameterSource[]::new);

                            npJdbc.batchUpdate("""
                            INSERT INTO car_master (car_no, created_at, updated_at)
                            SELECT :car_no, NOW(), NOW()
                            WHERE NOT EXISTS (SELECT 1 FROM car_master cm WHERE cm.car_no = :car_no)
                        """, params);
                        }

                        // 2-b) 매핑 조회
                        Map<String, Long> cmByCarNo = carNos.isEmpty() ? Map.of() :
                                npJdbc.query("""
                                SELECT car_no, MAX(car_id) AS car_id
                                  FROM car_master
                                 WHERE car_no IN (:nos)
                                 GROUP BY car_no
                            """, Map.of("nos", carNos), rs -> {
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

                        int processed = Arrays.stream(
                                jdbc.batchUpdate(
                                        "UPDATE platform_car SET car_id = ? WHERE platform_car_id = ? AND car_id IS NULL",
                                        params
                                )
                        ).sum();

                        return new ExecResult(processed, next);
                    })
            );

            if (res == null || res.processed() == 0) break;
            cursor = res.nextCursor();
            total  += res.processed();
        }
        log.info("linkToMaster linked rows: {}", total);
        return total;
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
        runWithRetry(3, 200L, () ->
                requiresNew().execute(status -> {
                    jdbc.update("""
                    UPDATE car_master m
                       SET m.adv_status = 'SOLD', m.updated_at = NOW()
                     WHERE NOT EXISTS (
                       SELECT 1 FROM platform_car p
                        WHERE p.car_id = m.car_id
                          AND p.last_seen_date = ?
                     )
                """, bizDate);
                    return null;
                })
        );
    }
}

package com.carizon.merge;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.*;


@Slf4j
@Service
@RequiredArgsConstructor
public class MergeService {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    NamedParameterJdbcTemplate npJdbc;
    private static final int BATCH_SIZE = 1_000;

    /* ===== 외부에서 호출 ===== */

    /**
     * convenience wrapper to call individual merges and return a count
     */
    @Transactional
    public int mergeAllPlatforms(LocalDate bizDate) {
        mergeChachachaDetail(bizDate);
        mergeEncarDetail(bizDate);
        mergeChutchaDetail(bizDate);
        mergeKcarDetail(bizDate);
        return newMasters(bizDate);
    }

    public void mergeChachachaDetail(LocalDate bizDate) {
        insertChachacha(bizDate);                  // INSERT → 커밋
        updateChachachaBatched(bizDate);    // UPDATE(1,000건씩) → 배치 커밋
    }

    public int mergeChachacha(LocalDate bizDate) {
        mergeChachachaDetail(bizDate);
        return newMasters(bizDate);
    }

    public void mergeEncarDetail(LocalDate bizDate) {
        insertEncar(bizDate);                  // INSERT → 커밋
        updateEncarBatched(bizDate);    // UPDATE(1,000건씩) → 배치 커밋
    }

    public int mergeEncar(LocalDate bizDate) {
        mergeEncarDetail(bizDate);
        return newMasters(bizDate);
    }

    public void mergeKcarDetail(LocalDate bizDate) {
        insertKcar(bizDate);                  // INSERT → 커밋
        updateKcarBatched(bizDate);    // UPDATE(1,000건씩) → 배치 커밋
    }

    public int mergeKcar(LocalDate bizDate) {
        mergeKcarDetail(bizDate);
        return newMasters(bizDate);
    }

    public void mergeChutchaDetail(LocalDate bizDate) {
        insertChutcha(bizDate);                  // INSERT → 커밋
        updateChutchaBatched(bizDate);    // UPDATE(1,000건씩) → 배치 커밋
    }

    public int mergeChutcha(LocalDate bizDate) {
        mergeChutchaDetail(bizDate);
        return newMasters(bizDate);
    }


    public void updatePlatformCarIdByCarMaster(LocalDate bizDate) {
        final String sql = """
                UPDATE platform_car p
                JOIN (
                  SELECT
                   A.CAR_ID,
                   A.CAR_NO
                  FROM car_master A, PLATFORM_CAR B
                  WHERE A.CAR_NO = B.CAR_NO
                    AND B.CAR_ID IS NULL
                  ORDER BY A.CAR_ID
                  LIMIT ?, ?
                ) C
                 ON P.CAR_ID IS NULL
                 AND p.CAR_NO = C.CAR_NO
                SET p.CAR_ID          = C.CAR_ID,
                    p.updated_at     = ?
                """;
        runBatched(sql, bizDate, "MERGE");

    }

    public int newMasters(LocalDate bizDate) {

        int newMasters = createMastersForUnlinkedPlatformCars(bizDate);

        // 3) 가격 이력 스냅샷
        snapshotPrices(bizDate);

        // 4) 미노출 차량 SOLD 처리
        closeMissingAds(bizDate);

        // updatePlatformCarIdByCarMaster(bizDate);

        return newMasters;
    }


    /* ===== INSERT (없는 것만) ===== */

    private void insertChachacha(LocalDate bizDate) {
        final String sql = """
                INSERT IGNORE INTO platform_car
                  (platform_name, platform_car_key, CAR_NO, car_id, 
                    MAKER_CODE, MODEL_GROUP_CODE, MODEL_CODE, TRIM_CODE, GRADE_CODE,
                    MAKER_NAME, MODEL_GROUP_NAME, MODEL_NAME, TRIM_NAME, GRADE_NAME,
                    price, KM, YYMM, status, COLOR, FUEL, TRANSMISSiON, BODY_TYPE, REGION,
                    M_URL, 
                    PC_url, 
                    FIRST_AD_DAY, created_at, updated_at , extra, last_seen_date)
                SELECT
                  'CHACHACHA', r.car_seq , r.car_no, NULL,
                  R.MAKER_CODE, r.CLASS_CODE, r.CAR_CODE, r.MODEL_CODE, r.GRADE_CODE,
                  R.MAKER_NAME, r.CLASS_NAME, r.CAR_NAME, r.MODEL_NAME, r.GRADE_NAME,
                  R.SELL_AMT, R.KM, R.YYMM, 'ONSALE', R.COLOR, R.GAS_NAME, R.auto_gbn_name, R.use_code_name, R.REGION,
                  CONCAT('https://m.kbchachacha.com/public/web/car/detail.kbc?carSeq=', R.CAR_SEQ),
                  CONCAT('https://www.kbchachacha.com/public/car/detail.kbc?carSeq=', r.car_seq),
                  R.FIRST_AD_DAY,  ?, NOW() , r.payload, NOW()
                FROM raw_chachacha r
                WHERE NOT EXISTS (SELECT 1 FROM platform_car p WHERE P.PLATFORM_NAME = 'CHACHACHA' AND p.platform_car_key = r.car_seq )
                """;
        var d = java.sql.Date.valueOf(bizDate);
        tx.execute(st -> {
            jdbc.update(sql, d);
            return null;
        });
    }

    private void insertEncar(LocalDate bizDate) {
        final String sql = """
                   INSERT IGNORE INTO platform_car
                                  (platform_name, platform_car_key, CAR_NO, car_id,
                                    MAKER_CODE, MODEL_GROUP_CODE, MODEL_CODE, TRIM_CODE, GRADE_CODE,
                                    MAKER_NAME, MODEL_GROUP_NAME, MODEL_NAME, TRIM_NAME, GRADE_NAME,
                                    price, KM, YYMM, status, COLOR, FUEL, TRANSMISSiON, BODY_TYPE, REGION,
                                    M_URL,
                                    PC_url,
                                    FIRST_AD_DAY,
                                    created_at, updated_at , extra, last_seen_date)
                                SELECT
                                  'ENCAR', r.vehicle_id , r.vehicle_no, NULL,
                                  R.manufacturer_code, R.model_group_code, R.MODEL_CODE, R.GRADE_CODE, R.GRADE_DETAIL_CODE,
                                  R.manufacturer_NAME, R.model_group_NAME, R.MODEL_NAME, R.GRADE_NAME, R.GRADE_DETAIL_NAME,
                                  R.price, R.MILEAGE, R.form_year, 'ONSALE', R.COLOR, R.FUEL, R.TRANSMISSION, R.BODY_TYPE, r.region,
                                  CONCAT('https://fem.encar.com/cars/detail/', r.vehicle_id ),
                                  CONCAT('https://fem.encar.com/cars/detail/', r.vehicle_id ),
                                  DATE_FORMAT(first_ad_dt, '%Y%m%d'),
                                  ?, NOW(), r.payload, NOW()
                                FROM raw_encar r
                                WHERE NOT EXISTS (SELECT 1 FROM platform_car p WHERE P.PLATFORM_NAME = 'ENCAR' AND p.platform_car_key = r.vehicle_id)
                """;
        var d = java.sql.Date.valueOf(bizDate);
        tx.execute(st -> {
            jdbc.update(sql, d);
            return null;
        });
    }

    private void insertKcar(LocalDate bizDate) {
        final String sql = """
                     INSERT IGNORE INTO platform_car
                                      (platform_name, platform_car_key, CAR_NO, car_id,
                                        MAKER_CODE, MODEL_GROUP_CODE, MODEL_CODE, TRIM_CODE, GRADE_CODE,
                                        MAKER_NAME, MODEL_GROUP_NAME, MODEL_NAME, TRIM_NAME, GRADE_NAME,
                                        price, KM, YYMM, status, COLOR, FUEL, TRANSMISSiON, BODY_TYPE, REGION,
                                        M_URL,
                                        PC_url,
                                        FIRST_AD_DAY, created_at, updated_at , extra, last_seen_date)
                        SELECT 'KCAR', a.car_cd, a.cno, NULL,
                                A.maker_code,A.model_group_code,A.model_code,A.grade_code,A.grade_detail_code,
                                A.maker_name,A.model_group_name,A.model_name,A.grade_name,A.grade_detail_name,
                                A.price,A.mileage,A.yymm,'SALE',    A.color,    A.fuel,A.transmission, A.body_type, A.REGION,
                                 CONCAT('https://m.kcar.com/bc/detail/carInfoDtl?i_sCarCd=EC61246262', A.car_cd),
                                 CONCAT('https://www.kcar.com/bc/detail/carInfoDtl?i_sCarCd=EC61246262', A.car_cd),
                                '',  ?, NOW() , A.payload, now()
                        FROM raw_kcar a
                       WHERE NOT EXISTS (SELECT 1 FROM platform_car p WHERE P.PLATFORM_NAME = 'KCAR' AND p.platform_car_key = A.car_cd )
                """;
        var d = java.sql.Date.valueOf(bizDate);
        tx.execute(st -> {
            jdbc.update(sql, d);
            return null;
        });
    }

    private void insertChutcha(LocalDate bizDate) {
        final String sql = """
                   INSERT IGNORE INTO platform_car
                                  (platform_name, platform_car_key, CAR_NO, car_id,
                                    MAKER_NAME, MODEL_GROUP_NAME, MODEL_NAME, TRIM_NAME, GRADE_NAME,
                                    price, KM, YYMM, status, COLOR, FUEL, TRANSMISSiON, BODY_TYPE, REGION,
                                    M_URL,
                                    PC_url,
                                    created_at, updated_at , extra, last_seen_date)
                                 SELECT
                                  'CHUTCHA',
                                   r.car_id,
                                   r.number_plate,
                                  NULL,
                                  r.brand_name, r.model_name, r.sub_model_name, r.grade_name, r.sub_grade_name,
                                  price, mileage, first_reg_year,null, color, fuel_name,transmission_name,car_type,shop_addr_short,
                                  CONCAT('https://www.chutcha.net/share/car/detail/', JSON_UNQUOTE(JSON_EXTRACT(payload,'$.detail_link_hash'))),
                                  CONCAT('https://web.chutcha.net/bmc/detail/', JSON_UNQUOTE(JSON_EXTRACT(payload,'$.detail_link_hash'))),
                                   ? ,  NOW() , r.payload,  NOW()
                                FROM raw_chutcha r
                                WHERE NOT EXISTS (SELECT 1 FROM platform_car p WHERE P.PLATFORM_NAME = 'CHUTCHA' AND p.platform_car_key = r.CAR_id)
                               
                """;
        var d = java.sql.Date.valueOf(bizDate);
        tx.execute(st -> {
            jdbc.update(sql, d);
            return null;
        });
    }

    /* ===== UPDATE (1,000건씩) ===== */

    private int updateChachachaBatched(LocalDate bizDate) {
        final String sql = """
                UPDATE platform_car p
                JOIN (
                  SELECT
                    r.car_seq  AS k,
                    r.sell_amt                     AS price,
                    r.maker_name                   AS maker,
                    r.model_name                   AS model,
                    r.payload                      AS payload
                  FROM raw_chachacha r
                  ORDER BY r.car_seq
                  LIMIT ?, ?
                ) r
                  ON p.platform_name='CHACHACHA'
                 AND p.platform_car_key = r.k
                SET p.price          = r.price,
                    p.extra          = r.payload,
                    p.last_seen_date = ?,
                    p.updated_at     = NOW()
                """;
        return runBatched(sql, bizDate, "CHACHACHA");
    }

    private int updateEncarBatched(LocalDate bizDate) {
        final String sql = """
                UPDATE platform_car p
                JOIN (
                  SELECT
                    r.vehicle_id  AS k,
                    JSON_UNQUOTE(JSON_EXTRACT(r.payload,'$.advertisement.status')) AS adv_status,
                    COALESCE(CAST(REPLACE(JSON_UNQUOTE(JSON_EXTRACT(r.payload,'$.advertisement.price')), ',', '') AS UNSIGNED), 0) AS price,
                    r.payload AS payload
                  FROM raw_encar r
                  ORDER BY r.vehicle_id
                  LIMIT ?, ?
                ) r
                  ON p.platform_name='ENCAR'
                 AND p.platform_car_key = r.k
                SET p.price          = r.price,
                    p.status         = r.adv_status,
                    p.extra          = r.payload,
                    p.last_seen_date = ?,
                    p.updated_at     = NOW()
                """;
        return runBatched(sql, bizDate, "ENCAR");
    }

    private int updateKcarBatched(LocalDate bizDate) {
        final String sql = """
                UPDATE platform_car p
                JOIN (
                  SELECT
                    r.car_cd AS k,
                    r.price      AS price,
                    r.payload    AS payload
                  FROM raw_kcar r
                  ORDER BY r.car_cd
                  LIMIT ?, ?
                ) r
                  ON p.platform_name='KCAR'
                 AND p.platform_car_key = r.k
                SET p.price          = r.price,
                    p.extra          = r.payload,
                    p.last_seen_date = ?,
                    p.updated_at     = NOW()
                """;
        return runBatched(sql, bizDate, "KCAR");
    }

    private int updateChutchaBatched(LocalDate bizDate) {
        final String sql = """
                 UPDATE platform_car p
                                JOIN (
                                  SELECT
                                    r.car_id AS car_id,
                                    r.price AS price
                                  FROM raw_chutcha r
                                  ORDER BY r.detail_hash
                                  LIMIT ?, ?
                                ) r
                                  ON p.platform_name='CHUTCHA'
                                 AND p.platform_car_key = r.car_id
                                SET p.price          = r.price,
                                    p.last_seen_date = ?,
                                    p.updated_at     = NOW()
                """;
        return runBatched(sql, bizDate, "CHUTCHA");
    }

    /* ===== 공통 배치 루프 ===== */

    private int runBatched(String sql, LocalDate bizDate, String tag) {
        final var d = java.sql.Date.valueOf(bizDate);
        int offset = 0, total = 0;
        while (true) {
            final int o = offset; // effectively final for lambda
            int cnt = tx.execute(st -> jdbc.update(sql, o, BATCH_SIZE, d));
            total += cnt;
            log.info("[{}] offset={} size={} -> updated={} total={}", tag, o, BATCH_SIZE, cnt, total);
            if (cnt < BATCH_SIZE) break;
            offset += BATCH_SIZE;
        }
        return total;
    }


    static record PcRow(long platformCarId, String carNo) {
    }

    public int createMastersForUnlinkedPlatformCars(LocalDate bizDate) {
        final int CHUNK = 1000; // 배치 크기
        int total = 0;

        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        while (true) {
            int processed = tx.execute(status -> {
                // 1) 이번 배치 대상 고정 + 잠금
                //    (MySQL 8 이상이면 FOR UPDATE 뒤에 SKIP LOCKED 추가해 다중 워커 동시처리 가능)
                List<PcRow> batch = jdbc.query("""
                                    SELECT PLATFORM_CAR_ID, CAR_NO
                                    FROM platform_car
                                    WHERE CAR_ID IS NULL
                                    ORDER BY PLATFORM_CAR_ID
                                    LIMIT ?
                                    FOR UPDATE
                                """,
                        (rs, i) -> new PcRow(rs.getLong("PLATFORM_CAR_ID"), rs.getString("CAR_NO")),
                        CHUNK);

                if (batch.isEmpty()) return 0;

                // id / car_no 목록 준비
                List<Long> ids = batch.stream().map(PcRow::platformCarId).collect(java.util.stream.Collectors.toList());
                List<String> carNos = batch.stream()
                        .map(PcRow::carNo)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .collect(java.util.stream.Collectors.toList());

                // 2) 대상만 car_master에 INSERT
                //    (중복 master 방지하려면 아래 WHERE 뒤에
                //     "AND NOT EXISTS (SELECT 1 FROM car_master cm WHERE cm.car_no = p.CAR_NO)" 추가 가능)
                int inserted = npJdbc.update("""
                            INSERT INTO car_master (
                                maker_code, model_group_code, model_code, trim_code, grade_code,
                                YEAR, MILEAGE, COLOR, TRANSMISSION, FUEL,
                                created_at, updated_at, CAR_NO
                            )
                            SELECT
                                NULL, NULL, NULL, NULL, NULL,
                                COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(p.extra, '$.category.yearMonth')) AS UNSIGNED)/100, NULL),
                                COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(p.extra, '$.mileage')) AS UNSIGNED), NULL),
                                JSON_UNQUOTE(JSON_EXTRACT(p.extra, '$.color')),
                                JSON_UNQUOTE(JSON_EXTRACT(p.extra, '$.transmission')),
                                JSON_UNQUOTE(JSON_EXTRACT(p.extra, '$.fuel')),
                                NOW(), NOW(), p.CAR_NO
                            FROM platform_car p
                            WHERE p.PLATFORM_CAR_ID IN (:ids)
                              AND p.CAR_ID IS NULL
                        """, java.util.Map.of("ids", ids));

                // 3) car_no -> 최신 car_master.id 맵 조회
                java.util.Map<String, Long> cmByCarNo = npJdbc.query("""
                            SELECT cm.CAR_NO AS car_no, MAX(cm.CAR_id) AS cm_id
                            FROM car_master cm
                            WHERE cm.CAR_NO IN (:carNos)
                            GROUP BY cm.CAR_NO
                        """, java.util.Map.of("carNos", carNos), rs -> {
                    java.util.Map<String, Long> m = new java.util.HashMap<>();
                    while (rs.next()) m.put(rs.getString("car_no"), rs.getLong("cm_id"));
                    return m;
                });

                // 4) platform_car.CAR_ID 배치 업데이트 (PLATFORM_CAR_ID로 매핑)
                java.util.List<Object[]> params = new java.util.ArrayList<>();
                for (PcRow r : batch) {
                    Long cmId = (r.carNo() == null) ? null : cmByCarNo.get(r.carNo());
                    if (cmId != null) {
                        params.add(new Object[]{cmId, r.platformCarId()});
                    }
                }
                if (params.isEmpty()) return 0;

                int[] upd = jdbc.batchUpdate(
                        "UPDATE platform_car SET CAR_ID = ? WHERE PLATFORM_CAR_ID = ? AND CAR_ID IS NULL",
                        params
                );

                return java.util.Arrays.stream(upd).sum();
            });

            if (processed == 0) break;  // 더 이상 처리할 대상 없음
            total += processed;         // 누적
        }
        return total;
    }

    /**
     * 가격 히스토리 스냅샷: platform_car(last_seen_date=bizDate) 을 기준으로 car_price_history 업데이트
     */
    @Transactional
    public void snapshotPrices(LocalDate bizDate) {
        // 1) 신규 또는 가격 변동시 insert
        jdbc.update("""
                    INSERT INTO car_price_history (platform_car_id, price, checked_at, is_current)
                    SELECT p.platform_car_id, p.price, NOW(), 1
                      FROM platform_car p
                     WHERE p.last_seen_date = ?
                       AND NOT EXISTS (
                           SELECT 1 FROM car_price_history h
                            WHERE h.platform_car_id = p.platform_car_id AND h.is_current=1 AND h.price = p.price
                       )
                """, bizDate);

        // 2) 기존 current 중 가격 달라진 것 종료
        jdbc.update("""
                    UPDATE car_price_history h
                    JOIN platform_car p ON h.platform_car_id = p.platform_car_id
                    SET h.is_current = 0, h.last_seen_at = NOW()
                    WHERE h.is_current = 1
                      AND p.last_seen_date = ?
                      AND h.price <> p.price
                """, bizDate);

        // 3) 동일가격은 last_seen_at 갱신
        jdbc.update("""
                    UPDATE car_price_history h
                    JOIN platform_car p ON h.platform_car_id = p.platform_car_id AND h.price = p.price
                    SET h.last_seen_at = NOW()
                    WHERE h.is_current = 1
                      AND p.last_seen_date = ?
                """, bizDate);
    }

    /**
     * 오늘 수집되지 않은 CAR_MASTER를 판매완료(SOLD) 처리
     */
    @Transactional
    public void closeMissingAds(LocalDate bizDate) {
        jdbc.update("""
                    UPDATE car_master m
                    SET m.adv_status = 'SOLD', m.updated_at = NOW()
                    WHERE NOT EXISTS (
                        SELECT 1 FROM platform_car p WHERE p.car_id = m.car_id AND p.last_seen_date = ?
                    )
                """, bizDate);
    }


    public Map<String, Object> runFullBatch(LocalDate bizDate) {
        // 1) 플랫폼 머지 (각 플랫폼 raw_* -> platform_car)
        int e = mergeEncar(bizDate);
        int c1 = mergeChachacha(bizDate);
        int c2 = mergeChutcha(bizDate);
        int k = mergeKcar(bizDate); // KCar는 platform_key가 유니크하므로 포함

        int newMasters = newMasters(bizDate);

        return Map.of(
                "encarMerged", e,
                "chachaMerged", c1,
                "chutchaMerged", c2,
                "kcarMerged", k,
                "newMastersCreated", newMasters
        );
    }

}

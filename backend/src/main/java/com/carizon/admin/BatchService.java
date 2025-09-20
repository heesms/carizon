package com.carizon.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchService {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    private static final int BATCH_SIZE = 1_000;

    /* ===== 외부에서 호출 ===== */

    public Map<String,Object> runFullBatch(LocalDate bizDate) {
        // 1) 플랫폼 머지 (각 플랫폼 raw_* -> platform_car)
        int e = mergeEncar(bizDate);
        int c1 = mergeChachacha(bizDate);
        int c2 = mergeChutcha(bizDate);
        int k = mergeKcar(bizDate); // KCar는 platform_key가 유니크하므로 포함

        // 2) platform_car (car_id NULL) -> 새 CAR_MASTER 생성 및 연결
        int newMasters = createMastersForUnlinkedPlatformCars(bizDate);

        // 3) 가격 이력 스냅샷
        snapshotPrices(bizDate);

        // 4) 미노출 차량 SOLD 처리
        closeMissingAds(bizDate);

        return Map.of(
                "encarMerged", e,
                "chachaMerged", c1,
                "chutchaMerged", c2,
                "kcarMerged", k,
                "newMastersCreated", newMasters
        );
    }

    public int mergeChachacha(LocalDate bizDate) {
        insertChachacha(bizDate);                  // INSERT → 커밋
        return updateChachachaBatched(bizDate);    // UPDATE(1,000건씩) → 배치 커밋
    }

    public int mergeEncar(LocalDate bizDate) {
        insertEncar(bizDate);
        return updateEncarBatched(bizDate);
    }

    public int mergeKcar(LocalDate bizDate) {
        insertKcar(bizDate);
        return updateKcarBatched(bizDate);
    }

    public int mergeChutcha(LocalDate bizDate) {
        insertChutcha(bizDate);
        return updateChutchaBatched(bizDate);
    }

    /* ===== INSERT (없는 것만) ===== */

    private void insertChachacha(LocalDate bizDate) {
        final String sql = """
            INSERT IGNORE INTO platform_car
              (platform_name, platform_car_key, car_id, title, price, status, url, extra, last_seen_date, created_at, updated_at)
            SELECT
              'CHACHACHA',
             r.car_seq ,
              NULL,
              CONCAT(r.maker_name,' ',r.model_name),
              r.sell_amt,
              NULL,
              CONCAT('https://www.kbchachacha.com/public/car/detail.kbc?carSeq=', r.car_seq),
              r.payload,
              ?, NOW(), NOW()
            FROM raw_chachacha r
            LEFT JOIN platform_car p
              ON p.platform_name='CHACHACHA'
             AND p.platform_car_key = r.car_seq 
            WHERE p.platform_car_key IS NULL
            """;
        var d = java.sql.Date.valueOf(bizDate);
        tx.execute(st -> { jdbc.update(sql, d); return null; });
    }

    private void insertEncar(LocalDate bizDate) {
        final String sql = """
            INSERT IGNORE INTO platform_car
              (platform_name, platform_car_key, car_id, title, price, status, url, extra, last_seen_date, created_at, updated_at)
            SELECT
              'ENCAR',
              r.vehicle_id ,
              NULL,
              JSON_UNQUOTE(JSON_EXTRACT(r.payload,'$.advertisement.title')),
              COALESCE(CAST(REPLACE(JSON_UNQUOTE(JSON_EXTRACT(r.payload,'$.advertisement.price')), ',', '') AS UNSIGNED), 0),
              JSON_UNQUOTE(JSON_EXTRACT(r.payload,'$.advertisement.status')),
              CONCAT('https://fem.encar.com/cars/detail/', CAST(r.vehicle_id AS CHAR(64))),
              r.payload,
              ?, NOW(), NOW()
            FROM raw_encar r
            LEFT JOIN platform_car p
              ON p.platform_name='ENCAR'
             AND p.platform_car_key = r.vehicle_id 
            WHERE p.platform_car_key IS NULL
            """;
        var d = java.sql.Date.valueOf(bizDate);
        tx.execute(st -> { jdbc.update(sql, d); return null; });
    }

    private void insertKcar(LocalDate bizDate) {
        final String sql = """
            INSERT IGNORE INTO platform_car
              (platform_name, platform_car_key, car_id, title, price, status, url, extra, last_seen_date, created_at, updated_at)
            SELECT
              'KCAR',
              r.car_cd,
              NULL,
              r.model_full,
              r.price,
              NULL,
              CONCAT('https://www.kcar.com/buy/car/detail/', r.car_cd),
              r.payload,
              ?, NOW(), NOW()
            FROM raw_kcar r
            LEFT JOIN platform_car p
              ON p.platform_name='KCAR'
             AND p.platform_car_key = r.car_cd
            WHERE p.platform_car_key IS NULL
            """;
        var d = java.sql.Date.valueOf(bizDate);
        tx.execute(st -> { jdbc.update(sql, d); return null; });
    }

    private void insertChutcha(LocalDate bizDate) {
        final String sql = """
            INSERT IGNORE INTO platform_car
              (platform_name, platform_car_key, car_id, title, price, status, url, extra, last_seen_date, created_at, updated_at)
            SELECT
              'CHUTCHA',
              JSON_UNQUOTE(JSON_EXTRACT(payload,'$.detail_link_hash')),
              NULL,
              CONCAT(JSON_UNQUOTE(JSON_EXTRACT(payload,'$.brand_name')),' ', JSON_UNQUOTE(JSON_EXTRACT(payload,'$.model_name'))),
              COALESCE(CAST(REPLACE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.price')), ',', '') AS UNSIGNED), 0),
              NULL,
               CONCAT('https://web.chutcha.net/bmc/detail/', JSON_UNQUOTE(JSON_EXTRACT(payload,'$.detail_link_hash'))),
              r.payload,
              ?, NOW(), NOW()
            FROM raw_chutcha r
            LEFT JOIN platform_car p
              ON p.platform_name='CHUTCHA'
             AND p.platform_car_key = r.detail_hash
            WHERE p.platform_car_key IS NULL
            """;
        var d = java.sql.Date.valueOf(bizDate);
        tx.execute(st -> { jdbc.update(sql, d); return null; });
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
                p.title          = CONCAT(r.maker,' ',r.model),
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
                JSON_UNQUOTE(JSON_EXTRACT(r.payload,'$.advertisement.title'))  AS title,
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
                p.title          = r.title,
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
                r.model_full AS title,
                r.price      AS price,
                r.payload    AS payload
              FROM raw_kcar r
              ORDER BY r.car_cd
              LIMIT ?, ?
            ) r
              ON p.platform_name='KCAR'
             AND p.platform_car_key = r.k
            SET p.price          = r.price,
                p.title          = r.title,
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
                r.detail_hash AS k,
                CONCAT(r.brand_name,' ',r.model_name,' ',r.grade_name) AS title,
                r.price AS price,
                r.payload AS payload
              FROM raw_chutcha r
              ORDER BY r.detail_hash
              LIMIT ?, ?
            ) r
              ON p.platform_name='CHUTCHA'
             AND p.platform_car_key = r.k
            SET p.price          = r.price,
                p.title          = r.title,
                p.extra          = r.payload,
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

    @Transactional
    public int createMastersForUnlinkedPlatformCars(LocalDate bizDate) {
        // 1) Insert CAR_MASTER rows using platform_car.extra (payload). We'll create one master per platform_car without car_id.
        //    For simplicity, create minimal CAR_MASTER with some attributes from JSON; more mapping can be added later.
        jdbc.update("""
            INSERT INTO car_master (maker_code, model_group_code, model_code, trim_code, grade_code, YEAR, MILEAGE, COLOR, TRANSMISSION, FUEL, created_at, updated_at)
            SELECT
                NULL, NULL, NULL, NULL, NULL,
                COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(p.extra, '$.category.yearMonth')) AS UNSIGNED)/100, NULL),
                COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(p.extra, '$.mileage')) AS UNSIGNED), NULL),
                JSON_UNQUOTE(JSON_EXTRACT(p.extra, '$.color')) ,
                JSON_UNQUOTE(JSON_EXTRACT(p.extra, '$.transmission')),
                JSON_UNQUOTE(JSON_EXTRACT(p.extra, '$.fuel')),
                NOW(), NOW()
            FROM platform_car p
            WHERE p.car_id IS NULL
            LIMIT 10000
        """);
        // NOTE: 위 INSERT 는 플랫폼별 payload 구조(필드명)가 다양하므로 실제 운영에서는 플랫폼별 세부 필드 매핑을 정확히 해줘야 함.

        // 2) 이제 새로 생성된 car_master 레코드를 platform_car의 car_id에 연결해야 함.
        //    간단한 방법: 최근에 생성된 car_master들을 platform_car에 매핑(1:1) — 이건 위험할 수 있으니 여기선 플랫폼별 PK로 연결하지 않는다.
        //    안전한 방법: platform_car에 이미 car_id 존재하는지 확인 후, 새 car_master와의 매핑은 운영자/별도 매칭으로 보강 권장.
        // For now, return number of unlinked platform_car (just informational)
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM platform_car WHERE car_id IS NULL", Integer.class);
        return count != null ? count : 0;
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

    /**
     * convenience wrapper to call individual merges and return a count
     */
    @Transactional
    public int mergeAllPlatforms(LocalDate bizDate) {
        mergeEncar(bizDate);
        mergeChachacha(bizDate);
        mergeChutcha(bizDate);
        mergeKcar(bizDate);
        return 4;
    }
}

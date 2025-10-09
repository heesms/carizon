package com.carizon.mapping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MasterMergeService {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;          // execute(...) 용
    private final TransactionTemplate txTemplate;  // REQUIRES_NEW 권장 (설정에 따라)

    private static final int CHUNK_SIZE = 1000;
    private static final String CL = "utf8mb4_general_ci"; // JOIN 시 collation 강제

    /** 최초 1회: 우선순위 시드 보장 (없으면 삽입) */
    public void ensurePrioritySeed() {
        String ddl = """
            CREATE TABLE IF NOT EXISTS cz_platform_priority (
              platform_name VARCHAR(50) NOT NULL PRIMARY KEY,
              priority      INT NOT NULL,
              updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                             ON UPDATE CURRENT_TIMESTAMP
            )
        """;
        jdbc.execute(ddl);

        String upsert = """
            INSERT INTO cz_platform_priority(platform_name, priority) VALUES
              ('CHACHACHA', 1),
              ('ENCAR',     2),
              ('KCAR',      3),
              ('CHUTCHA',   4),
              ('CHARANCHA', 5),
              ('TCAR',      6)
            ON DUPLICATE KEY UPDATE priority=VALUES(priority)
        """;
        jdbc.update(upsert);
    }

    /** 오늘 수집(alive) 기준 car_master upsert (필요 시 유지) */
    public int upsertAliveToCarMaster(LocalDate bizDate) {
        return tx.execute(status -> jdbc.update("""
            INSERT INTO car_master
            (CAR_NO, MAKER_CODE, MODEL_GROUP_CODE, MODEL_CODE, TRIM_CODE, GRADE_CODE,
             adv_status, last_seen_date, UPDATED_AT)
            SELECT t.CAR_NO,
                   t.MAKER_CODE, t.MODEL_GROUP_CODE, t.MODEL_CODE, t.TRIM_CODE, t.GRADE_CODE,
                   'ONSALE', ?, NOW()
            FROM (
              SELECT p.CAR_NO,
                     ANY_VALUE(cm_m.maker_code)       AS MAKER_CODE,
                     ANY_VALUE(cm_g.model_group_code) AS MODEL_GROUP_CODE,
                     ANY_VALUE(cm_md.model_code)      AS MODEL_CODE,
                     ANY_VALUE(cm_t.trim_code)        AS TRIM_CODE,
                     ANY_VALUE(cm_gr.grade_code)      AS GRADE_CODE
              FROM platform_car p
              LEFT JOIN cz_code_map cm_m  ON cm_m.platform_name=p.PLATFORM_NAME AND cm_m.level='MAKER'       AND cm_m.platform_code=p.MAKER_CODE       AND cm_m.status IN ('LOCKED','AUTO')
              LEFT JOIN cz_code_map cm_g  ON cm_g.platform_name=p.PLATFORM_NAME AND cm_g.level='MODEL_GROUP' AND cm_g.platform_code=p.MODEL_GROUP_CODE AND cm_g.status IN ('LOCKED','AUTO')
              LEFT JOIN cz_code_map cm_md ON cm_md.platform_name=p.PLATFORM_NAME AND cm_md.level='MODEL'      AND cm_md.platform_code=p.MODEL_CODE      AND cm_md.status IN ('LOCKED','AUTO')
              LEFT JOIN cz_code_map cm_t  ON cm_t.platform_name=p.PLATFORM_NAME AND cm_t.level='TRIM'        AND cm_t.platform_code=p.TRIM_CODE        AND cm_t.status IN ('LOCKED','AUTO')
              LEFT JOIN cz_code_map cm_gr ON cm_gr.platform_name=p.PLATFORM_NAME AND cm_gr.level='GRADE'      AND cm_gr.platform_code=p.GRADE_CODE      AND cm_gr.status IN ('LOCKED','AUTO')
              WHERE DATE(p.last_seen_date)=?
              GROUP BY p.CAR_NO
            ) t
            ON DUPLICATE KEY UPDATE
              MAKER_CODE       = VALUES(MAKER_CODE),
              MODEL_GROUP_CODE = VALUES(MODEL_GROUP_CODE),
              MODEL_CODE       = VALUES(MODEL_CODE),
              TRIM_CODE        = VALUES(TRIM_CODE),
              GRADE_CODE       = VALUES(GRADE_CODE),
              adv_status       = 'ONSALE',
              last_seen_date   = VALUES(last_seen_date),
              UPDATED_AT       = NOW()
        """, bizDate, bizDate));
    }

    /** 우선순위 기반 매핑 적용 (청크 처리, 플랫폼 1건만 선택) */
    public int updateCarMasterFromMapping() {
        ensurePrioritySeed(); // 안전하게 보장

        List<Long> ids = jdbc.query(
                "SELECT CAR_ID FROM car_master WHERE adv_status='ONSALE'",
                (rs, i) -> rs.getLong(1));
        if (ids.isEmpty()) return 0;

        int affectedTotal = 0;
        for (int from = 0; from < ids.size(); from += CHUNK_SIZE) {
            int to = Math.min(from + CHUNK_SIZE, ids.size());
            List<Long> batch = ids.subList(from, to);

            Integer affected = txTemplate.execute(s -> doUpdateChunk(batch));
            affectedTotal += (affected == null ? 0 : affected);

            log.info("[master] mapping chunk {}/{} size={} affected={}",
                    (to + CHUNK_SIZE - 1)/CHUNK_SIZE, (ids.size() + CHUNK_SIZE -1)/CHUNK_SIZE, batch.size(), affected);
        }
        log.info("[master] mapping total affected={}", affectedTotal);
        return affectedTotal;
    }

    /** 동일 CAR_NO 다플랫폼 → 우선순위 1건만 선택해서 매핑 */
    private Integer doUpdateChunk(List<Long> carIds) {
        if (carIds.isEmpty()) return 0;

        String ids = carIds.stream().map(x -> "?").collect(Collectors.joining(","));

        String sql = ("""
            UPDATE car_master cm
            /* 우선순위 1건만 뽑은 플랫폼 차량 */
            JOIN (
              SELECT *
              FROM (
                SELECT pc.*,
                       COALESCE(pp.priority, 9) AS pr,
                       ROW_NUMBER() OVER (
                         PARTITION BY pc.CAR_NO
                         ORDER BY COALESCE(pp.priority, 9),
                                  pc.last_seen_date DESC,
                                  pc.PLATFORM_CAR_ID DESC
                       ) AS rn
                FROM platform_car pc
                JOIN car_master cm2  ON cm2.CAR_NO = pc.CAR_NO
                                    AND cm2.CAR_ID IN (__IDS__)
                LEFT JOIN cz_platform_priority pp
                       ON pp.platform_name = pc.PLATFORM_NAME
                WHERE cm2.adv_status = 'ONSALE'
              ) t
              WHERE t.rn = 1
            ) pc
              ON cm.CAR_NO = pc.CAR_NO
            /* 코드 매핑 */
            JOIN cz_code_map m
              ON m.platform_name = pc.PLATFORM_NAME
             AND COALESCE(m.p_maker_code,'')       COLLATE %s = COALESCE(pc.MAKER_CODE,'')       COLLATE %s
             AND COALESCE(m.p_model_group_code,'') COLLATE %s = COALESCE(pc.MODEL_GROUP_CODE,'') COLLATE %s
             AND COALESCE(m.p_model_code,'')       COLLATE %s = COALESCE(pc.MODEL_CODE,'')       COLLATE %s
             AND COALESCE(m.p_trim_code,'')        COLLATE %s = COALESCE(pc.TRIM_CODE,'')        COLLATE %s
             AND COALESCE(m.p_grade_code,'')       COLLATE %s = COALESCE(pc.GRADE_CODE,'')       COLLATE %s
            SET cm.MAKER_CODE       = m.maker_code,
                cm.MODEL_GROUP_CODE = m.model_group_code,
                cm.MODEL_CODE       = m.model_code,
                cm.TRIM_CODE        = m.trim_code,
                cm.GRADE_CODE       = m.grade_code,
                cm.UPDATED_AT       = NOW(),
                cm.YEAR =   pc.YYMM,
                cm.MILEAGE =  pc.KM,
                cm.COLOR = pc.COLOR,
                cm.TRANSMISSiON = pc.TRANSMISSiON,
                cm.FUEL = pc.FUEL,
                cm.REGION = pc.REGION,
                cm.DISPLACEMENT = pc.DISPLACEMENT,
                cm.BODY_TYPE = pc.BODY_TYPE
            WHERE cm.adv_status = 'ONSALE'
              AND cm.CAR_ID IN (__IDS__)
            """).formatted(CL,CL, CL,CL, CL,CL, CL,CL, CL,CL)
                .replace("__IDS__", ids);

        // IN 절이 위/아래 두 곳 → 파라미터 두 세트
        List<Object> params = new ArrayList<>(carIds.size() * 2);
        params.addAll(carIds);
        params.addAll(carIds);

        return jdbc.update(sql, params.toArray());
    }

    /** 오늘자에 없는 차량 SOLD 처리 */
    public int markSold(LocalDate bizDate, int batchSize) {
        int total = 0;
        while (true) {
            int n = tx.execute(status -> jdbc.update("""
                UPDATE car_master m
                LEFT JOIN (
                    SELECT DISTINCT CAR_NO
                    FROM platform_car
                    WHERE DATE(last_seen_date)=?
                ) a ON a.CAR_NO = m.CAR_NO
                SET m.adv_status='SOLD', m.UPDATED_AT=NOW()
                WHERE a.CAR_NO IS NULL
                  AND m.adv_status <> 'SOLD'
                LIMIT ?
            """, bizDate, batchSize));
            total += n;
            if (n < batchSize) break;
        }
        return total;
    }

    /** 가격 이력 append (그대로 유지) */
    public int appendPriceHistory(LocalDate bizDate) {
        int closed = tx.execute(s -> jdbc.update("""
            UPDATE car_price_history h
            JOIN platform_car p
              ON p.PLATFORM_CAR_ID = h.PLATFORM_CAR_ID
             AND DATE(p.last_seen_date)=?
            SET h.is_current = 0
            WHERE h.is_current = 1
              AND (h.PRICE <> p.PRICE OR h.PRICE IS NULL AND p.PRICE IS NOT NULL OR h.PRICE IS NOT NULL AND p.PRICE IS NULL)
        """, bizDate));

        int inserted = tx.execute(s -> jdbc.update("""
            INSERT INTO car_price_history (PLATFORM_CAR_ID, PRICE, CHECKED_AT, is_current, last_seen_at)
            SELECT p.PLATFORM_CAR_ID, p.PRICE, NOW(), 1, NOW()
            FROM platform_car p
            LEFT JOIN car_price_history h
              ON h.PLATFORM_CAR_ID = p.PLATFORM_CAR_ID
             AND h.is_current = 1
            WHERE DATE(p.last_seen_date)=?
              AND (h.PLATFORM_CAR_ID IS NULL OR h.PRICE <> p.PRICE
                   OR (h.PRICE IS NULL AND p.PRICE IS NOT NULL)
                   OR (h.PRICE IS NOT NULL AND p.PRICE IS NULL))
        """, bizDate));

        log.info("appendPriceHistory closed={}, inserted={}", closed, inserted);
        return inserted;
    }
}

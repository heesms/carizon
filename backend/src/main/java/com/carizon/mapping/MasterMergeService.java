package com.carizon.mapping;

import com.carizon.common.service.CarMasterIdSequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
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
    private final CarMasterIdSequenceService carMasterIdSequenceService;

    private static final int CHUNK_SIZE = 1000;
    private static final int MASTER_MERGE_BATCH_SIZE = 5000; // car_master 머지 배치 크기

    /** 최초 1회: 우선순위 시드 보장 (없으면 삽입, 락 타임아웃 방지를 위해 INSERT IGNORE 사용) */
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

        // INSERT IGNORE 사용하여 락 타임아웃 방지 (이미 있으면 무시)
        String insert = """
            INSERT IGNORE INTO cz_platform_priority(platform_name, priority) VALUES
              ('CHACHACHA', 1),
              ('ENCAR',     2),
              ('KCAR',      3),
              ('CHUTCHA',   4),
              ('CHARANCHA', 5),
              ('TCAR',      6)
        """;
        jdbc.update(insert);
    }

    /** 오늘 수집(alive) 기준 car_master upsert (배치 처리로 최적화) */
    public int upsertAliveToCarMaster(LocalDate bizDate) {
        // 인덱스 활용을 위해 DATE() 함수 대신 범위 검색 사용
        java.sql.Date dateStart = java.sql.Date.valueOf(bizDate);
        java.sql.Date dateEnd = java.sql.Date.valueOf(bizDate.plusDays(1));
        
        log.info("[master] upsertAliveToCarMaster: start (batch mode)");
        
        // 1단계: 처리할 CAR_NO 목록 조회 (배치 처리용)
        List<String> carNos = jdbc.query("""
            SELECT DISTINCT p.CAR_NO
            FROM platform_car p
            WHERE p.last_seen_date >= ? AND p.last_seen_date < ?
              AND p.CAR_NO IS NOT NULL
            ORDER BY p.CAR_NO
        """, (rs, i) -> rs.getString(1), dateStart, dateEnd);
        
        if (carNos.isEmpty()) {
            log.info("[master] upsertAliveToCarMaster: no data to process");
            return 0;
        }
        
        log.info("[master] upsertAliveToCarMaster: {} CAR_NO to process", carNos.size());
        
        // 2단계: 배치별로 처리
        int totalAffected = 0;
        int batchCount = 0;
        
        for (int from = 0; from < carNos.size(); from += MASTER_MERGE_BATCH_SIZE) {
            int to = Math.min(from + MASTER_MERGE_BATCH_SIZE, carNos.size());
            List<String> batch = carNos.subList(from, to);
            batchCount++;
            
            long batchStart = System.currentTimeMillis();
            Integer affected = txTemplate.execute(status -> {
                // 배치용 CAR_NO 플레이스홀더 생성
                String placeholders = batch.stream()
                    .map(c -> "?")
                    .collect(Collectors.joining(","));
                
                // 파라미터 배열 생성: bizDate, dateStart, dateEnd, batch...
                List<Object> params = new ArrayList<>();
                params.add(bizDate);
                params.add(dateStart);
                params.add(dateEnd);
                params.addAll(batch);
                
                return jdbc.update(String.format("""
                    INSERT INTO car_master
                    (CAR_NO, MAKER_CODE, MODEL_GROUP_CODE, MODEL_CODE, TRIM_CODE, GRADE_CODE,
                     price_new, adv_status, last_seen_date, UPDATED_AT)
                    SELECT t.CAR_NO,
                           MAX(t.MAKER_CODE) AS MAKER_CODE,
                           MAX(t.MODEL_GROUP_CODE) AS MODEL_GROUP_CODE,
                           MAX(t.MODEL_CODE) AS MODEL_CODE,
                           MAX(t.TRIM_CODE) AS TRIM_CODE,
                           MAX(t.GRADE_CODE) AS GRADE_CODE,
                           MAX(CASE WHEN t.price_new IS NOT NULL AND t.price_new > 0 THEN t.price_new END) AS price_new,
                           'ONSALE', ?, NOW()
                    FROM (
                      SELECT p.CAR_NO,
                             p.price_new AS price_new,
                             cm_m.maker_code AS MAKER_CODE,
                             cm_mg.model_group_code AS MODEL_GROUP_CODE,
                             cm_mo.model_code AS MODEL_CODE,
                             cm_t.trim_code AS TRIM_CODE,
                             NULLIF(cm_g.grade_code, 'null') AS GRADE_CODE
                      FROM platform_car p
                      LEFT JOIN cz_code_map cm_m
                        ON cm_m.platform_name = p.PLATFORM_NAME
                        AND cm_m.p_maker_code = p.MAKER_CODE
                        AND cm_m.status IN ('LOCKED','AUTO')
                      LEFT JOIN cz_code_map cm_mg
                        ON cm_mg.platform_name = p.PLATFORM_NAME
                        AND cm_mg.p_model_group_code = p.MODEL_GROUP_CODE
                        AND cm_mg.status IN ('LOCKED','AUTO')
                      LEFT JOIN cz_code_map cm_mo
                        ON cm_mo.platform_name = p.PLATFORM_NAME
                        AND cm_mo.p_model_code = p.MODEL_CODE
                        AND cm_mo.status IN ('LOCKED','AUTO')
                      LEFT JOIN cz_code_map cm_t
                        ON cm_t.platform_name = p.PLATFORM_NAME
                        AND cm_t.p_trim_code = p.TRIM_CODE
                        AND cm_t.status IN ('LOCKED','AUTO')
                      LEFT JOIN cz_code_map cm_g
                        ON cm_g.platform_name = p.PLATFORM_NAME
                        AND cm_g.p_grade_code = p.GRADE_CODE
                        AND cm_g.status IN ('LOCKED','AUTO')
                      WHERE p.last_seen_date >= ? AND p.last_seen_date < ?
                        AND p.CAR_NO IS NOT NULL
                        AND p.CAR_NO IN (%s)
                    ) t
                    GROUP BY t.CAR_NO
                    ON DUPLICATE KEY UPDATE
                      MAKER_CODE       = VALUES(MAKER_CODE),
                      MODEL_GROUP_CODE = VALUES(MODEL_GROUP_CODE),
                      MODEL_CODE       = VALUES(MODEL_CODE),
                      TRIM_CODE        = VALUES(TRIM_CODE),
                      GRADE_CODE       = VALUES(GRADE_CODE),
                      price_new        = COALESCE(NULLIF(VALUES(price_new), 0), car_master.price_new),
                      adv_status       = 'ONSALE',
                      last_seen_date   = VALUES(last_seen_date),
                      UPDATED_AT       = NOW()
                """, placeholders), 
                params.toArray());
            });
            
            int batchAffected = (affected == null ? 0 : affected);
            totalAffected += batchAffected;
            long batchTime = System.currentTimeMillis() - batchStart;
            
            log.info("[master] upsertAliveToCarMaster batch {}/{} done: {} rows ({}ms)", 
                batchCount, (carNos.size() + MASTER_MERGE_BATCH_SIZE - 1) / MASTER_MERGE_BATCH_SIZE, 
                batchAffected, batchTime);
        }
        
        log.info("[master] upsertAliveToCarMaster done: {} rows ({} batches)", totalAffected, batchCount);
        return totalAffected;
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

        // 서브쿼리로 CZ_CODE_MAP 직접 조회하여 인덱스 활용 최적화
        String sql = """
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
                                  CASE WHEN TRIM(COALESCE(pc.FUEL, '')) IN ('가솔린', '휘발유') OR UPPER(TRIM(COALESCE(pc.FUEL, ''))) = 'GASOLINE' THEN 0 ELSE 1 END,
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
            SET cm.MAKER_CODE       = (SELECT m.maker_code FROM cz_code_map m
                                       WHERE m.platform_name = pc.PLATFORM_NAME
                                         AND m.p_maker_code = pc.MAKER_CODE
                                         AND m.status IN ('LOCKED','AUTO')
                                       LIMIT 1),
                cm.MODEL_GROUP_CODE = (SELECT m.model_group_code FROM cz_code_map m
                                       WHERE m.platform_name = pc.PLATFORM_NAME
                                         AND m.p_model_group_code = pc.MODEL_GROUP_CODE
                                         AND m.status IN ('LOCKED','AUTO')
                                       LIMIT 1),
                cm.MODEL_CODE       = (SELECT m.model_code FROM cz_code_map m
                                       WHERE m.platform_name = pc.PLATFORM_NAME
                                         AND m.p_model_code = pc.MODEL_CODE
                                         AND m.status IN ('LOCKED','AUTO')
                                       LIMIT 1),
                cm.TRIM_CODE        = (SELECT m.trim_code FROM cz_code_map m
                                       WHERE m.platform_name = pc.PLATFORM_NAME
                                         AND m.p_trim_code = pc.TRIM_CODE
                                         AND m.status IN ('LOCKED','AUTO')
                                       LIMIT 1),
                cm.GRADE_CODE       = NULLIF((SELECT m.grade_code FROM cz_code_map m
                                       WHERE m.platform_name = pc.PLATFORM_NAME
                                         AND m.p_grade_code = pc.GRADE_CODE
                                         AND m.status IN ('LOCKED','AUTO')
                                       LIMIT 1), 'null'),
                cm.UPDATED_AT       = NOW(),
                cm.YEAR =   pc.YYMM,
                cm.MILEAGE =  pc.KM,
                cm.COLOR = pc.COLOR,
                cm.TRANSMISSiON = pc.TRANSMISSiON,
                cm.FUEL = pc.FUEL,
                cm.REGION = pc.REGION,
                cm.DISPLACEMENT = pc.DISPLACEMENT,
                cm.BODY_TYPE = pc.BODY_TYPE,
                cm.price_new = CASE WHEN pc.price_new IS NOT NULL AND pc.price_new > 0 THEN pc.price_new ELSE cm.price_new END
            WHERE cm.adv_status = 'ONSALE'
              AND cm.CAR_ID IN (__IDS__)
            """.replace("__IDS__", ids);

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
                SET m.adv_status='SOLD', m.UPDATED_AT=NOW()
                WHERE m.CAR_ID IN (
                    SELECT CAR_ID FROM (
                        SELECT m2.CAR_ID
                        FROM car_master m2
                        LEFT JOIN (
                            SELECT DISTINCT CAR_NO
                            FROM platform_car
                            WHERE DATE(last_seen_date)=?
                        ) a ON a.CAR_NO = m2.CAR_NO
                        WHERE a.CAR_NO IS NULL
                          AND m2.adv_status <> 'SOLD'
                        LIMIT ?
                    ) AS subquery
                )
            """, bizDate, batchSize));
            total += n;
            if (n < batchSize) break;
        }
        return total;
    }

    /** TRUNCATE 후 car_master 재생성 (순수 INSERT만 사용, 배치 처리로 최적화) 
     *  주의: platform_car는 이미 TRUNCATE되고 재생성된 상태여야 함
     *  car_price_history도 함께 TRUNCATE해야 할 수 있음 (platform_car_id 참조) */
    public int rebuildCarMasterFromScratch(LocalDate bizDate) {
        log.warn("[master] rebuildCarMasterFromScratch: TRUNCATE car_master and rebuild! (batch mode)");
        long nextCarId = carMasterIdSequenceService.snapshotNextCarId();
        log.info("[master] rebuildCarMasterFromScratch: preserve next car_id={}", nextCarId);
        
        // 우선순위 테이블 보장 (별도 트랜잭션으로 분리하여 락 타임아웃 방지)
        try {
            ensurePrioritySeed();
        } catch (Exception e) {
            log.warn("[master] ensurePrioritySeed failed (lock timeout?), continuing: {}", e.getMessage());
        }
        
        // 1단계: car_master TRUNCATE
        tx.execute(status -> {
            log.info("[master] rebuildCarMasterFromScratch: car_master TRUNCATE start");
            jdbc.execute("TRUNCATE TABLE car_master");
            carMasterIdSequenceService.restoreNextCarId(nextCarId);
            log.info("[master] rebuildCarMasterFromScratch: car_master TRUNCATE done");
            return null;
        });
        
        // 2단계: 처리할 CAR_NO 목록 조회 (배치 처리용)
        // platform_car가 이미 재생성되었으므로 last_seen_date 조건 불필요
        log.info("[master] rebuildCarMasterFromScratch: CAR_NO list fetch start");
        List<String> carNos = jdbc.query("""
            SELECT DISTINCT p.CAR_NO
            FROM platform_car p
            WHERE p.CAR_NO IS NOT NULL
            ORDER BY p.CAR_NO
        """, (rs, i) -> rs.getString(1));
        
        if (carNos.isEmpty()) {
            log.info("[master] rebuildCarMasterFromScratch: no data to process");
            return 0;
        }
        
        log.info("[master] rebuildCarMasterFromScratch: {} CAR_NO to process", carNos.size());
        
        // 3단계: 배치별로 처리
        int totalAffected = 0;
        int batchCount = 0;
        
        for (int from = 0; from < carNos.size(); from += MASTER_MERGE_BATCH_SIZE) {
            int to = Math.min(from + MASTER_MERGE_BATCH_SIZE, carNos.size());
            List<String> batch = carNos.subList(from, to);
            batchCount++;
            
            long batchStart = System.currentTimeMillis();
            Integer affected = txTemplate.execute(status -> {
                // 배치용 CAR_NO 플레이스홀더 생성
                String placeholders = batch.stream()
                    .map(c -> "?")
                    .collect(Collectors.joining(","));
                
                // 파라미터 배열 생성: bizDate, batch...
                List<Object> params = new ArrayList<>();
                params.add(bizDate);
                params.addAll(batch);
                
                return jdbc.update(String.format("""
                    INSERT INTO car_master
                    (CAR_NO, MAKER_CODE, MODEL_GROUP_CODE, MODEL_CODE, TRIM_CODE, GRADE_CODE,
                     YEAR, MILEAGE, COLOR, TRANSMISSiON, FUEL, REGION, DISPLACEMENT, BODY_TYPE, price_new,
                     adv_status, last_seen_date, UPDATED_AT)
                    SELECT 
                      t.CAR_NO,
                      (SELECT cm.maker_code FROM cz_code_map cm
                       WHERE cm.platform_name = t.PLATFORM_NAME
                         AND cm.p_maker_code = t.MAKER_CODE
                         AND cm.status IN ('LOCKED','AUTO')
                       LIMIT 1) AS MAKER_CODE,
                      (SELECT cm.model_group_code FROM cz_code_map cm
                       WHERE cm.platform_name = t.PLATFORM_NAME
                         AND cm.p_maker_code = t.MAKER_CODE
                         AND cm.p_model_group_code = t.MODEL_GROUP_CODE
                         AND cm.status IN ('LOCKED','AUTO')
                       LIMIT 1) AS MODEL_GROUP_CODE,
                      (SELECT cm.model_code FROM cz_code_map cm
                       WHERE cm.platform_name = t.PLATFORM_NAME
                         AND cm.p_maker_code = t.MAKER_CODE
                         AND cm.p_model_group_code = t.MODEL_GROUP_CODE
                         AND cm.p_model_code = t.MODEL_CODE
                         AND cm.status IN ('LOCKED','AUTO')
                       LIMIT 1) AS MODEL_CODE,
                      (SELECT cm.trim_code FROM cz_code_map cm
                       WHERE cm.platform_name = t.PLATFORM_NAME
                         AND cm.p_maker_code = t.MAKER_CODE
                         AND cm.p_model_group_code = t.MODEL_GROUP_CODE
                         AND cm.p_model_code = t.MODEL_CODE
                         AND cm.p_trim_code = t.TRIM_CODE
                         AND cm.status IN ('LOCKED','AUTO')
                       LIMIT 1) AS TRIM_CODE,
                      NULLIF((SELECT cm.grade_code FROM cz_code_map cm
                       WHERE cm.platform_name = t.PLATFORM_NAME
                         AND cm.p_maker_code = t.MAKER_CODE
                         AND cm.p_model_group_code = t.MODEL_GROUP_CODE
                         AND cm.p_model_code = t.MODEL_CODE
                         AND cm.p_trim_code = t.TRIM_CODE
                         AND cm.p_grade_code = t.GRADE_CODE
                         AND cm.status IN ('LOCKED','AUTO')
                       LIMIT 1), 'null') AS GRADE_CODE,
                      t.YYMM AS YEAR,
                      t.KM AS MILEAGE,
                      t.COLOR,
                      t.TRANSMISSiON,
                      t.FUEL,
                      t.REGION,
                      t.DISPLACEMENT,
                      t.BODY_TYPE,
                      (SELECT MAX(pc2.price_new) FROM platform_car pc2
                       WHERE pc2.CAR_NO = t.CAR_NO AND pc2.price_new IS NOT NULL AND pc2.price_new > 0) AS price_new,
                      'ONSALE', ?, NOW()
                    FROM (
                      SELECT t_inner.*
                      FROM (
                        SELECT pc.*,
                               COALESCE(pp.priority, 9) AS pr,
                               ROW_NUMBER() OVER (
                                 PARTITION BY pc.CAR_NO
                                 ORDER BY COALESCE(pp.priority, 9),
                                          CASE WHEN TRIM(COALESCE(pc.FUEL, '')) IN ('가솔린', '휘발유') OR UPPER(TRIM(COALESCE(pc.FUEL, ''))) = 'GASOLINE' THEN 0 ELSE 1 END,
                                          pc.last_seen_date DESC,
                                          pc.PLATFORM_CAR_ID DESC
                               ) AS rn
                        FROM platform_car pc
                        LEFT JOIN cz_platform_priority pp
                               ON pp.platform_name = pc.PLATFORM_NAME
                        WHERE pc.CAR_NO IN (%s)
                      ) t_inner
                      WHERE t_inner.rn = 1
                    ) t
                """, placeholders), params.toArray());
            });
            
            int batchAffected = affected != null ? affected : 0;
            totalAffected += batchAffected;
            long batchTime = System.currentTimeMillis() - batchStart;
            
            log.info("[master] rebuildCarMasterFromScratch batch {}/{} done: {} rows ({}ms)", 
                batchCount, (carNos.size() + MASTER_MERGE_BATCH_SIZE - 1) / MASTER_MERGE_BATCH_SIZE, 
                batchAffected, batchTime);
        }
        
        log.info("[master] rebuildCarMasterFromScratch: done - {} rows ({} batches)", totalAffected, batchCount);
        return totalAffected;
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

package com.carizon.mapping;

import com.carizon.common.service.CarMasterIdSequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;

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

    private void logBatchSql(String stage, String sql, Object... params) {
        if (!log.isInfoEnabled()) return;
        log.info("[batch-sql] {}:\\n{}\\nparams={}", stage, sql == null ? "" : sql.strip(), Arrays.toString(params));
    }

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

    /** 수집 데이터 기준(car_no + 날짜 범위 또는 전체) car_master upsert (배치 처리로 최적화) */
    public int upsertAliveToCarMaster(LocalDate bizDate) {
        boolean fullSync = bizDate == null;
        final java.sql.Date dateStart = fullSync ? null : java.sql.Date.valueOf(bizDate);
        final java.sql.Date dateEnd = fullSync ? null : java.sql.Date.valueOf(bizDate.plusDays(1));
        
        log.info("[master] upsertAliveToCarMaster: start (batch mode)");
        
        // 1단계: 처리할 CAR_NO 목록 조회 (배치 처리용)
        String carNoSql = fullSync
                ? """
                  SELECT DISTINCT p.CAR_NO
                  FROM platform_car p
                  WHERE p.CAR_NO IS NOT NULL
                  ORDER BY p.CAR_NO
                  """
                : """
                  SELECT DISTINCT p.CAR_NO
                  FROM platform_car p
                  WHERE p.last_seen_date >= ? AND p.last_seen_date < ?
                    AND p.CAR_NO IS NOT NULL
                  ORDER BY p.CAR_NO
                  """;
        List<String> carNos = fullSync
                ? jdbc.query(carNoSql, (rs, i) -> rs.getString(1))
                : jdbc.query(carNoSql, (rs, i) -> rs.getString(1), dateStart, dateEnd);
        
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
                
                List<Object> params = new ArrayList<>();
                String insertSql;
                if (fullSync) {
                    insertSql = String.format("""
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
                               'ONSALE', NOW(), NOW()
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
                          WHERE p.CAR_NO IS NOT NULL
                            AND p.CAR_NO IN (%s)
                        ) t
                        GROUP BY t.CAR_NO
                        ON DUPLICATE KEY UPDATE
                          MAKER_CODE       = COALESCE(VALUES(MAKER_CODE), MAKER_CODE),
                          MODEL_GROUP_CODE = COALESCE(VALUES(MODEL_GROUP_CODE), MODEL_GROUP_CODE),
                          MODEL_CODE       = COALESCE(VALUES(MODEL_CODE), MODEL_CODE),
                          TRIM_CODE        = COALESCE(VALUES(TRIM_CODE), TRIM_CODE),
                          GRADE_CODE       = COALESCE(VALUES(GRADE_CODE), GRADE_CODE),
                          price_new        = COALESCE(NULLIF(VALUES(price_new), 0), car_master.price_new),
                          adv_status       = 'ONSALE',
                          last_seen_date   = VALUES(last_seen_date),
                          UPDATED_AT       = NOW()
                        """, placeholders);
                    params.addAll(batch);
                } else {
                    insertSql = String.format("""
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
                          MAKER_CODE       = COALESCE(VALUES(MAKER_CODE), MAKER_CODE),
                          MODEL_GROUP_CODE = COALESCE(VALUES(MODEL_GROUP_CODE), MODEL_GROUP_CODE),
                          MODEL_CODE       = COALESCE(VALUES(MODEL_CODE), MODEL_CODE),
                          TRIM_CODE        = COALESCE(VALUES(TRIM_CODE), TRIM_CODE),
                          GRADE_CODE       = COALESCE(VALUES(GRADE_CODE), GRADE_CODE),
                          price_new        = COALESCE(NULLIF(VALUES(price_new), 0), car_master.price_new),
                          adv_status       = 'ONSALE',
                          last_seen_date   = VALUES(last_seen_date),
                          UPDATED_AT       = NOW()
                        """, placeholders);
                    params.add(dateStart);
                    params.add(dateStart);
                    params.add(dateEnd);
                    params.addAll(batch);
                }
                
                return jdbc.update(insertSql, params.toArray());
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
            SET cm.MAKER_CODE       = COALESCE((SELECT m.maker_code FROM cz_code_map m
                                       WHERE m.platform_name = pc.PLATFORM_NAME
                                         AND m.p_maker_code = pc.MAKER_CODE
                                         AND m.status IN ('LOCKED','AUTO')
                                       LIMIT 1), cm.MAKER_CODE),
                cm.MODEL_GROUP_CODE = COALESCE((SELECT m.model_group_code FROM cz_code_map m
                                       WHERE m.platform_name = pc.PLATFORM_NAME
                                         AND m.p_model_group_code = pc.MODEL_GROUP_CODE
                                         AND m.status IN ('LOCKED','AUTO')
                                       LIMIT 1), cm.MODEL_GROUP_CODE),
                cm.MODEL_CODE       = COALESCE((SELECT m.model_code FROM cz_code_map m
                                       WHERE m.platform_name = pc.PLATFORM_NAME
                                         AND m.p_model_code = pc.MODEL_CODE
                                         AND m.status IN ('LOCKED','AUTO')
                                       LIMIT 1), cm.MODEL_CODE),
                cm.TRIM_CODE        = COALESCE((SELECT m.trim_code FROM cz_code_map m
                                       WHERE m.platform_name = pc.PLATFORM_NAME
                                         AND m.p_trim_code = pc.TRIM_CODE
                                         AND m.status IN ('LOCKED','AUTO')
                                       LIMIT 1), cm.TRIM_CODE),
                cm.GRADE_CODE       = COALESCE(NULLIF((SELECT m.grade_code FROM cz_code_map m
                                       WHERE m.platform_name = pc.PLATFORM_NAME
                                         AND m.p_grade_code = pc.GRADE_CODE
                                         AND m.status IN ('LOCKED','AUTO')
                                       LIMIT 1), 'null'), cm.GRADE_CODE),
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
        
        // 1단계: car_master TRUNCATE + platform_car.car_id 리셋 (linkToMaster 재실행 보장)
        tx.execute(status -> {
            log.info("[master] rebuildCarMasterFromScratch: car_master TRUNCATE start");
            jdbc.execute("TRUNCATE TABLE car_master");
            carMasterIdSequenceService.restoreNextCarId(nextCarId);
            // car_master가 새로 생성되므로 platform_car의 car_id 참조를 초기화
            // (linkToMaster의 WHERE car_id IS NULL 조건이 모든 행에 적용되도록)
            int reset = jdbc.update("UPDATE platform_car SET car_id = NULL WHERE car_id IS NOT NULL");
            log.info("[master] rebuildCarMasterFromScratch: car_master TRUNCATE done, platform_car.car_id reset={}", reset);
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

    /** TRUNCATE 후 car_master 재생성 (동일 CAR_NO는 기존 CAR_ID 유지)
     *  기존 rebuild와 동일한 INSERT 로직 + car_id 매핑 테이블 적용
     */
    public int rebuildCarMasterFromScratchPreserveCarId(LocalDate bizDate) {
        log.warn("[master] rebuildCarMasterFromScratchPreserveCarId: TRUNCATE car_master and rebuild with preserved car_id");
        long nextCarId = snapshotNextCarIdByMax();
        log.info("[master] rebuildCarMasterFromScratchPreserveCarId: preserve next car_id={}", nextCarId);

        String runId = UUID.randomUUID().toString();
        String mapTable = "car_master_id_retain_map";

        try {
            // 기존 car_master의 CAR_NO -> CAR_ID를 run_id 단위로 저장 (동시 실행 안전)
            jdbc.update("DELETE FROM " + mapTable + " WHERE run_id = ?", runId);
            jdbc.update("""
                INSERT INTO car_master_id_retain_map (run_id, car_no, car_id)
                SELECT ?, CAR_NO, CAR_ID
                FROM car_master
                WHERE CAR_NO IS NOT NULL
                """, runId);
            logBatchSql("snapshotCarMasterIdMap", "INSERT INTO car_master_id_retain_map... run_id=" + runId);

            // 동시성/스토리지 관리용 오래된 매핑 정리 (선택)
            jdbc.update("""
                DELETE FROM car_master_id_retain_map
                WHERE created_at < NOW() - INTERVAL 2 DAY
                """);

            // 우선순위 테이블 보장 (별도 트랜잭션으로 분리하여 락 타임아웃 방지)
            try {
                ensurePrioritySeed();
            } catch (Exception e) {
                log.warn("[master] ensurePrioritySeed failed (lock timeout?), continuing: {}", e.getMessage());
            }

            // 1단계: car_master TRUNCATE + platform_car.car_id 리셋 (linkToMaster 재실행 보장)
            tx.execute(status -> {
                log.info("[master] rebuildCarMasterFromScratchPreserveCarId: car_master TRUNCATE start");
                jdbc.execute("TRUNCATE TABLE car_master");
                carMasterIdSequenceService.restoreNextCarId(nextCarId);
                // car_master가 새로 생성되므로 platform_car의 car_id 참조를 초기화
                // (linkToMaster의 WHERE car_id IS NULL 조건이 모든 행에 적용되도록)
                int reset = jdbc.update("UPDATE platform_car SET car_id = NULL WHERE car_id IS NOT NULL");
                log.info("[master] rebuildCarMasterFromScratchPreserveCarId: car_master TRUNCATE done, platform_car.car_id reset={}", reset);
                return null;
            });

            // 2단계: 처리할 CAR_NO 목록 조회 (배치 처리용)
            // platform_car가 이미 재생성되었으므로 last_seen_date 조건 불필요
            log.info("[master] rebuildCarMasterFromScratchPreserveCarId: CAR_NO list fetch start");
            List<String> carNos = jdbc.query("""
                SELECT DISTINCT p.CAR_NO
                FROM platform_car p
                WHERE p.CAR_NO IS NOT NULL
                ORDER BY p.CAR_NO
            """, (rs, i) -> rs.getString(1));

            if (carNos.isEmpty()) {
                log.info("[master] rebuildCarMasterFromScratchPreserveCarId: no data to process");
                return 0;
            }

            log.info("[master] rebuildCarMasterFromScratchPreserveCarId: {} CAR_NO to process", carNos.size());

            // 3단계: 배치별로 처리
            int totalAffected = 0;
            int batchCount = 0;

            for (int from = 0; from < carNos.size(); from += MASTER_MERGE_BATCH_SIZE) {
                int to = Math.min(from + MASTER_MERGE_BATCH_SIZE, carNos.size());
                List<String> batch = carNos.subList(from, to);
                batchCount++;

                long batchStart = System.currentTimeMillis();
                Integer affected = txTemplate.execute(status -> {
                    String placeholders = batch.stream().map(c -> "?").collect(Collectors.joining(","));
                    String sql = String.format("""
                        INSERT INTO car_master
                        (CAR_ID, CAR_NO, MAKER_CODE, MODEL_GROUP_CODE, MODEL_CODE, TRIM_CODE, GRADE_CODE,
                         YEAR, MILEAGE, COLOR, TRANSMISSiON, FUEL, REGION, DISPLACEMENT, BODY_TYPE, price_new,
                         adv_status, last_seen_date, UPDATED_AT)
                        SELECT
                          cm_map.CAR_ID,
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
                          'ONSALE', NOW(), NOW()
                        FROM (
                            SELECT t_inner.*
                            FROM (
                                SELECT pc.*,
                                       COALESCE(pp.priority, 9) AS pr,
                                       ROW_NUMBER() OVER (
                                         PARTITION BY pc.CAR_NO
                                         ORDER BY COALESCE(pp.priority, 9),
                                                  CASE WHEN TRIM(COALESCE(pc.FUEL, '')) IN ('가솔린', '휘발유')
                                                           OR UPPER(TRIM(COALESCE(pc.FUEL, ''))) = 'GASOLINE' THEN 0 ELSE 1 END,
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
                        LEFT JOIN car_master_id_retain_map cm_map
                          ON cm_map.CAR_NO = t.CAR_NO
                         AND cm_map.RUN_ID = ?
                        """, placeholders);
                    List<Object> params = new ArrayList<>();
                    params.addAll(batch);
                    params.add(runId);
                    return jdbc.update(sql, params.toArray());
                });

                int batchAffected = affected != null ? affected : 0;
                totalAffected += batchAffected;
                long batchTime = System.currentTimeMillis() - batchStart;
                log.info("[master] rebuildCarMasterFromScratchPreserveCarId batch {}/{} done: {} rows ({}ms)",
                        batchCount, (carNos.size() + MASTER_MERGE_BATCH_SIZE - 1) / MASTER_MERGE_BATCH_SIZE,
                        batchAffected, batchTime);
            }

            log.info("[master] rebuildCarMasterFromScratchPreserveCarId: done - {} rows ({} batches)", totalAffected, batchCount);
            return totalAffected;
        } finally {
            try {
                int cleaned = jdbc.update("DELETE FROM " + mapTable + " WHERE run_id = ?", runId);
                log.info("[master] rebuildCarMasterFromScratchPreserveCarId: cleared retain map rows={}, run_id={}", cleaned, runId);
            } catch (Exception e) {
                log.warn("[master] rebuildCarMasterFromScratchPreserveCarId: failed to clean retain map rows run_id={}: {}",
                        runId, e.getMessage());
            }
        }
    }

    private long snapshotNextCarIdByMax() {
        Long nextCarId = jdbc.queryForObject("SELECT COALESCE(MAX(car_id), 0) + 1 FROM car_master", Long.class);
        if (nextCarId == null || nextCarId <= 1L) {
            return 1L;
        }
        return nextCarId;
    }

    /**
     * car_master 증분 동기화(기존 데이터 유지):
     * - platform_car 기준으로 존재하면 유지/생성(ONSALE 업서트)
     * - 생성/업데이트 완료 후, 기준 platform_car에 없는 기존 행은 삭제
     */
    public Map<String, Integer> syncCarMasterFromPlatformCars(LocalDate bizDate) {
        int inserted = ensureCarMasterExistsFromPlatformCars(bizDate);
        int upserted = upsertAliveToCarMaster(bizDate);
        int updated = updateCarMasterFromMapping();
        int deleted = deleteCarMasterNotAliveToday(bizDate);

        return Map.of(
            "insertedCount", inserted,
            "upsertedCount", upserted,
            "updatedCount", updated,
            "deletedCount", deleted
        );
    }

    /**
     * 기존 car_master를 유지한 채 platform_car 기준으로 차이분만 반영:
     * - platform_car에 없는 car_master 삭제
     * - platform_car에는 있고 car_master에는 없는 차량 신규 적재
     */
    public Map<String, Integer> rebuildCarMasterIncremental() {
        int deletedCount = deleteCarMasterNotAliveToday(null);
        int insertedCount = insertMissingCarMasterFromPlatformCars();

        return Map.of(
            "deletedCount", deletedCount,
            "insertedCount", insertedCount
        );
    }

    /**
     * platform_car 기준 신규 car_no만 재생성 (기존 rebuild-car-master 삽입 로직 재사용)
     */
    private int insertMissingCarMasterFromPlatformCars() {
        List<String> missingCarNos = jdbc.query("""
            SELECT DISTINCT p.CAR_NO
            FROM platform_car p
            WHERE p.CAR_NO IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM car_master cm
                  WHERE cm.CAR_NO = p.CAR_NO
              )
            ORDER BY p.CAR_NO
        """, (rs, i) -> rs.getString(1));

        if (missingCarNos.isEmpty()) {
            log.info("[master] insertMissingCarMasterFromPlatformCars: no data");
            return 0;
        }

        int totalInserted = 0;
        int batchCount = 0;
        for (int from = 0; from < missingCarNos.size(); from += MASTER_MERGE_BATCH_SIZE) {
            int to = Math.min(from + MASTER_MERGE_BATCH_SIZE, missingCarNos.size());
            List<String> batch = missingCarNos.subList(from, to);
            batchCount++;

            long batchStart = System.currentTimeMillis();
            Integer inserted = txTemplate.execute(status -> {
                String placeholders = batch.stream().map(c -> "?").collect(Collectors.joining(","));
                String sql = String.format("""
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
                        'ONSALE', NOW(), NOW()
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
                    """, placeholders);
                return jdbc.update(sql, batch.toArray());
            });

            int batchInserted = inserted == null ? 0 : inserted;
            totalInserted += batchInserted;
            log.info("[master] insertMissingCarMasterFromPlatformCars batch {}/{} done: {} rows ({}ms)",
                    batchCount,
                    (missingCarNos.size() + MASTER_MERGE_BATCH_SIZE - 1) / MASTER_MERGE_BATCH_SIZE,
                    batchInserted,
                    System.currentTimeMillis() - batchStart);
        }

        return totalInserted;
    }

    /** platform_car 기준 CAR_NO가 car_master에 없으면 우선 생성 */
    private int ensureCarMasterExistsFromPlatformCars(LocalDate bizDate) {
        boolean fullSync = bizDate == null;
        final java.sql.Date dateStart = fullSync ? null : java.sql.Date.valueOf(bizDate);
        final java.sql.Date dateEnd = fullSync ? null : java.sql.Date.valueOf(bizDate.plusDays(1));

        String sql = fullSync
                ? """
                  INSERT INTO car_master (car_no, ADV_STATUS, LAST_SEEN_DATE, CREATED_AT, UPDATED_AT)
                  SELECT x.CAR_NO, 'ONSALE', x.last_seen_date, NOW(), NOW()
                  FROM (
                      SELECT DISTINCT CAR_NO, MAX(last_seen_date) AS last_seen_date
                      FROM platform_car
                      WHERE CAR_NO IS NOT NULL
                      GROUP BY CAR_NO
                  ) x
                  WHERE NOT EXISTS (
                      SELECT 1
                      FROM car_master cm
                      WHERE cm.CAR_NO = x.CAR_NO
                  )
                  """
                : """
                  INSERT INTO car_master (car_no, ADV_STATUS, LAST_SEEN_DATE, CREATED_AT, UPDATED_AT)
                  SELECT x.CAR_NO, 'ONSALE', x.last_seen_date, NOW(), NOW()
                  FROM (
                      SELECT DISTINCT CAR_NO, MAX(last_seen_date) AS last_seen_date
                      FROM platform_car
                      WHERE CAR_NO IS NOT NULL
                        AND last_seen_date >= ?
                        AND last_seen_date < ?
                      GROUP BY CAR_NO
                  ) x
                  WHERE NOT EXISTS (
                      SELECT 1
                      FROM car_master cm
                      WHERE cm.CAR_NO = x.CAR_NO
                  )
                  """;
        return tx.execute(status -> {
            if (fullSync) {
                logBatchSql("ensureCarMasterExistsFromPlatformCars", sql);
                return jdbc.update(sql);
            }

            logBatchSql("ensureCarMasterExistsFromPlatformCars", sql, dateStart, dateEnd);
            return jdbc.update(sql, dateStart, dateEnd);
        });
    }

    private int deleteCarMasterNotAliveToday(LocalDate bizDate) {
        boolean fullSync = bizDate == null;
        final java.sql.Date dateStart = fullSync ? null : java.sql.Date.valueOf(bizDate);
        final java.sql.Date dateEnd = fullSync ? null : java.sql.Date.valueOf(bizDate.plusDays(1));
        final int BATCH_SIZE = 5000;
        int totalDeleted = 0;

        while (true) {
            String sql = fullSync
                    ? """
                        DELETE FROM car_master
                        WHERE CAR_ID IN (
                            SELECT CAR_ID
                            FROM (
                                SELECT cm.CAR_ID
                                FROM car_master cm
                                LEFT JOIN (
                                    SELECT DISTINCT CAR_NO
                                    FROM platform_car
                                    WHERE CAR_NO IS NOT NULL
                                ) p ON p.CAR_NO = cm.CAR_NO
                                WHERE cm.CAR_NO IS NOT NULL
                                  AND p.CAR_NO IS NULL
                                LIMIT ?
                            ) t
                        )
                        """
                    : """
                DELETE FROM car_master
                WHERE CAR_ID IN (
                    SELECT CAR_ID
                    FROM (
                        SELECT cm.CAR_ID
                        FROM car_master cm
                        LEFT JOIN (
                            SELECT DISTINCT CAR_NO
                            FROM platform_car
                            WHERE CAR_NO IS NOT NULL
                              AND last_seen_date >= ?
                              AND last_seen_date < ?
                            ) p ON p.CAR_NO = cm.CAR_NO
                        WHERE cm.CAR_NO IS NOT NULL
                          AND p.CAR_NO IS NULL
                        LIMIT ?
                    ) t
                )
                        """;
            int deleted = tx.execute(status -> {
                if (fullSync) {
                    logBatchSql("deleteCarMasterNotAliveToday", sql, BATCH_SIZE);
                    return jdbc.update(sql, BATCH_SIZE);
                }
                logBatchSql("deleteCarMasterNotAliveToday", sql, dateStart, dateEnd, BATCH_SIZE);
                return jdbc.update(sql, dateStart, dateEnd, BATCH_SIZE);
            });

            totalDeleted += deleted;
            if (deleted < BATCH_SIZE) break;
        }

        return totalDeleted;
    }

    /** 가격 이력 append (그대로 유지) */
    public int appendPriceHistory(LocalDate bizDate) {
        String closeSql = """
            UPDATE car_price_history h
            JOIN platform_car p
              ON p.PLATFORM_CAR_ID = h.PLATFORM_CAR_ID
             AND DATE(p.last_seen_date)=?
            SET h.is_current = 0
            WHERE h.is_current = 1
              AND (h.PRICE <> p.PRICE OR h.PRICE IS NULL AND p.PRICE IS NOT NULL OR h.PRICE IS NOT NULL AND p.PRICE IS NULL)
        """;
        int closed = tx.execute(s -> {
            logBatchSql("appendPriceHistory.close", closeSql, bizDate);
            return jdbc.update(closeSql, bizDate);
        });

        String insertSql = """
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
        """;
        int inserted = tx.execute(s -> {
            logBatchSql("appendPriceHistory.insert", insertSql, bizDate);
            return jdbc.update(insertSql, bizDate);
        });

        log.info("appendPriceHistory closed={}, inserted={}", closed, inserted);
        return inserted;
    }
}

package com.carizon.mapping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * car_master의 price_new(신차가)를 차량(car_id) 단위로 cz_model_new_price에 쌓음.
 * 같은 등급이라도 차량마다 신차가가 조금씩 다를 수 있어 n건 저장.
 * 블로그 점수 산정 시 (maker, model_group, model, trim, grade)로 그룹해 AVG(price_new) 사용.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelNewPriceService {

    private final JdbcTemplate jdbc;

    /**
     * car_master에서 price_new가 있는 건을 차량당 1행씩 cz_model_new_price에 upsert.
     * car_id 기준 ON DUPLICATE KEY UPDATE로 동일 차량은 갱신.
     * GRADE_CODE는 NULL/'null'/빈문자열 → 테이블에는 ''로 저장.
     */
    @Transactional
    public int aggregateFromCarMaster() {
        String sql = """
            INSERT INTO cz_model_new_price
            (car_id, maker_code, model_group_code, model_code, trim_code, grade_code, price_new, updated_at)
            SELECT
              CAR_ID,
              MAKER_CODE,
              MODEL_GROUP_CODE,
              MODEL_CODE,
              TRIM_CODE,
              COALESCE(NULLIF(TRIM(COALESCE(GRADE_CODE, '')), 'null'), '') AS grade_code,
              price_new,
              NOW()
            FROM car_master
            WHERE price_new IS NOT NULL AND price_new > 0
              AND CAR_ID IS NOT NULL
              AND MAKER_CODE IS NOT NULL AND TRIM(COALESCE(MAKER_CODE,'')) <> ''
              AND MODEL_GROUP_CODE IS NOT NULL AND TRIM(COALESCE(MODEL_GROUP_CODE,'')) <> ''
              AND MODEL_CODE IS NOT NULL AND TRIM(COALESCE(MODEL_CODE,'')) <> ''
              AND TRIM_CODE IS NOT NULL AND TRIM(COALESCE(TRIM_CODE,'')) <> ''
            ON DUPLICATE KEY UPDATE
              maker_code       = VALUES(maker_code),
              model_group_code = VALUES(model_group_code),
              model_code       = VALUES(model_code),
              trim_code        = VALUES(trim_code),
              grade_code       = VALUES(grade_code),
              price_new        = VALUES(price_new),
              updated_at       = NOW()
            """;
        int updated = jdbc.update(sql);
        log.info("[model_new_price] aggregateFromCarMaster done: {} rows upserted", updated);
        return updated;
    }
}

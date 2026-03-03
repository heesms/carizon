-- car_master_id_retain_map: CAR_NO 조인 성능 보강
-- 기존 데이터베이스에서 안전하게 인덱스 추가

SET @index_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE table_schema = DATABASE()
      AND table_name = 'car_master_id_retain_map'
      AND index_name = 'idx_car_no'
);

SET @sql = IF(@index_exists = 0,
    'CREATE INDEX idx_car_no ON car_master_id_retain_map(car_no)',
    'SELECT "Index idx_car_no already exists" AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

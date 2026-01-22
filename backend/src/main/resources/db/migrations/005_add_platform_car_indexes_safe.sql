-- platform_car 테이블 성능 최적화 인덱스 추가 (안전 버전)
-- MySQL 5.7 호환: 인덱스 존재 여부 확인 후 생성

-- car_id와 last_seen_date 복합 인덱스 (closeMissingAds 쿼리 최적화)
SET @index_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE table_schema = DATABASE()
      AND table_name = 'platform_car'
      AND index_name = 'idx_platform_car_car_id_last_seen'
);

SET @sql = IF(@index_exists = 0,
    'CREATE INDEX idx_platform_car_car_id_last_seen ON platform_car(car_id, last_seen_date)',
    'SELECT "Index idx_platform_car_car_id_last_seen already exists" AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- last_seen_date 단일 인덱스
SET @index_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE table_schema = DATABASE()
      AND table_name = 'platform_car'
      AND index_name = 'idx_platform_car_last_seen_date'
);

SET @sql = IF(@index_exists = 0,
    'CREATE INDEX idx_platform_car_last_seen_date ON platform_car(last_seen_date)',
    'SELECT "Index idx_platform_car_last_seen_date already exists" AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- car_id 인덱스
SET @index_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE table_schema = DATABASE()
      AND table_name = 'platform_car'
      AND index_name = 'idx_platform_car_car_id'
);

SET @sql = IF(@index_exists = 0,
    'CREATE INDEX idx_platform_car_car_id ON platform_car(car_id)',
    'SELECT "Index idx_platform_car_car_id already exists" AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- car_master adv_status 인덱스 (SOLD 처리 쿼리 최적화)
SET @index_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE table_schema = DATABASE()
      AND table_name = 'car_master'
      AND index_name = 'idx_car_master_adv_status'
);

SET @sql = IF(@index_exists = 0,
    'CREATE INDEX idx_car_master_adv_status ON car_master(adv_status)',
    'SELECT "Index idx_car_master_adv_status already exists" AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- car_master car_id 인덱스 (JOIN 최적화)
SET @index_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE table_schema = DATABASE()
      AND table_name = 'car_master'
      AND index_name = 'idx_car_master_car_id'
);

SET @sql = IF(@index_exists = 0,
    'CREATE INDEX idx_car_master_car_id ON car_master(car_id)',
    'SELECT "Index idx_car_master_car_id already exists" AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

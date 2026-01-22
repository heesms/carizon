-- car_master 머지 쿼리 성능 최적화 인덱스 추가 (안전 버전)
-- MySQL 5.7 호환: 인덱스 존재 여부 확인 후 생성
-- upsertAliveToCarMaster 쿼리 최적화를 위한 인덱스

-- ============================================================
-- 1. platform_car 테이블 인덱스
-- ============================================================

-- CAR_NO 인덱스 (GROUP BY 최적화)
SET @index_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE table_schema = DATABASE()
      AND table_name = 'platform_car'
      AND index_name = 'idx_platform_car_car_no'
);

SET @sql = IF(@index_exists = 0,
    'CREATE INDEX idx_platform_car_car_no ON platform_car(CAR_NO)',
    'SELECT "Index idx_platform_car_car_no already exists" AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- platform_name + CAR_NO 복합 인덱스 (JOIN 최적화)
SET @index_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE table_schema = DATABASE()
      AND table_name = 'platform_car'
      AND index_name = 'idx_platform_car_platform_car_no'
);

SET @sql = IF(@index_exists = 0,
    'CREATE INDEX idx_platform_car_platform_car_no ON platform_car(PLATFORM_NAME, CAR_NO)',
    'SELECT "Index idx_platform_car_platform_car_no already exists" AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- last_seen_date + CAR_NO 복합 인덱스 (DISTINCT CAR_NO 조회 최적화)
SET @index_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE table_schema = DATABASE()
      AND table_name = 'platform_car'
      AND index_name = 'idx_platform_car_last_seen_car_no'
);

SET @sql = IF(@index_exists = 0,
    'CREATE INDEX idx_platform_car_last_seen_car_no ON platform_car(last_seen_date, CAR_NO)',
    'SELECT "Index idx_platform_car_last_seen_car_no already exists" AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 2. cz_code_map 테이블 인덱스 (JOIN 최적화)
-- ============================================================

-- platform_name + p_maker_code + status 복합 인덱스 (Maker JOIN)
SET @index_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE table_schema = DATABASE()
      AND table_name = 'cz_code_map'
      AND index_name = 'idx_cz_code_map_platform_maker_status'
);

SET @sql = IF(@index_exists = 0,
    'CREATE INDEX idx_cz_code_map_platform_maker_status ON cz_code_map(platform_name, p_maker_code, status)',
    'SELECT "Index idx_cz_code_map_platform_maker_status already exists" AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- platform_name + p_model_group_code + status 복합 인덱스 (Model Group JOIN)
SET @index_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE table_schema = DATABASE()
      AND table_name = 'cz_code_map'
      AND index_name = 'idx_cz_code_map_platform_model_group_status'
);

SET @sql = IF(@index_exists = 0,
    'CREATE INDEX idx_cz_code_map_platform_model_group_status ON cz_code_map(platform_name, p_model_group_code, status)',
    'SELECT "Index idx_cz_code_map_platform_model_group_status already exists" AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- platform_name + p_model_code + status 복합 인덱스 (Model JOIN)
SET @index_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE table_schema = DATABASE()
      AND table_name = 'cz_code_map'
      AND index_name = 'idx_cz_code_map_platform_model_status'
);

SET @sql = IF(@index_exists = 0,
    'CREATE INDEX idx_cz_code_map_platform_model_status ON cz_code_map(platform_name, p_model_code, status)',
    'SELECT "Index idx_cz_code_map_platform_model_status already exists" AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- platform_name + p_trim_code + status 복합 인덱스 (Trim JOIN)
SET @index_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE table_schema = DATABASE()
      AND table_name = 'cz_code_map'
      AND index_name = 'idx_cz_code_map_platform_trim_status'
);

SET @sql = IF(@index_exists = 0,
    'CREATE INDEX idx_cz_code_map_platform_trim_status ON cz_code_map(platform_name, p_trim_code, status)',
    'SELECT "Index idx_cz_code_map_platform_trim_status already exists" AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- platform_name + p_grade_code + status 복합 인덱스 (Grade JOIN)
SET @index_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE table_schema = DATABASE()
      AND table_name = 'cz_code_map'
      AND index_name = 'idx_cz_code_map_platform_grade_status'
);

SET @sql = IF(@index_exists = 0,
    'CREATE INDEX idx_cz_code_map_platform_grade_status ON cz_code_map(platform_name, p_grade_code, status)',
    'SELECT "Index idx_cz_code_map_platform_grade_status already exists" AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 3. car_master 테이블 인덱스
-- ============================================================

-- CAR_NO 인덱스 (ON DUPLICATE KEY UPDATE 최적화)
-- UNIQUE KEY가 이미 있으면 추가 불필요하지만, 조회 성능 향상을 위해 추가 가능
SET @index_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE table_schema = DATABASE()
      AND table_name = 'car_master'
      AND index_name = 'idx_car_master_car_no'
);

SET @sql = IF(@index_exists = 0,
    'CREATE INDEX idx_car_master_car_no ON car_master(CAR_NO)',
    'SELECT "Index idx_car_master_car_no already exists" AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 참고사항
-- ============================================================

-- 성능 개선 팁:
-- 1. DATE() 함수 사용 시 인덱스를 활용할 수 없으므로, 가능하면 범위 검색 사용:
--    WHERE DATE(p.last_seen_date) = ? 
--    → WHERE p.last_seen_date >= ? AND p.last_seen_date < DATE_ADD(?, INTERVAL 1 DAY)
--    (이미 MasterMergeService.upsertAliveToCarMaster에서 수정 완료)
--
-- 2. COALESCE() 함수 사용 시에도 인덱스 활용이 제한될 수 있으나,
--    NULL 값 처리를 위해 필요하므로 유지하는 것이 좋음
--
-- 3. 인덱스 생성 후 ANALYZE TABLE 실행 권장:
--    ANALYZE TABLE platform_car;
--    ANALYZE TABLE cz_code_map;
--    ANALYZE TABLE car_master;

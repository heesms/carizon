-- car_master 머지 쿼리 성능 최적화 인덱스 추가
-- upsertAliveToCarMaster 쿼리 최적화를 위한 인덱스

-- ============================================================
-- 1. platform_car 테이블 인덱스
-- ============================================================

-- CAR_NO 인덱스 (GROUP BY 최적화)
CREATE INDEX IF NOT EXISTS idx_platform_car_car_no 
ON platform_car(CAR_NO);

-- platform_name + CAR_NO 복합 인덱스 (JOIN 최적화)
CREATE INDEX IF NOT EXISTS idx_platform_car_platform_car_no 
ON platform_car(PLATFORM_NAME, CAR_NO);

-- last_seen_date + CAR_NO 복합 인덱스 (DISTINCT CAR_NO 조회 최적화)
CREATE INDEX IF NOT EXISTS idx_platform_car_last_seen_car_no 
ON platform_car(last_seen_date, CAR_NO);

-- last_seen_date 범위 검색 최적화 (DATE() 함수 대신 범위 검색 사용 권장)
-- 참고: DATE(last_seen_date) = ? 대신 last_seen_date >= ? AND last_seen_date < DATE_ADD(?, INTERVAL 1 DAY) 사용 시 인덱스 활용 가능
-- 이미 idx_platform_car_last_seen_date 인덱스가 있으면 추가 불필요

-- ============================================================
-- 2. cz_code_map 테이블 인덱스 (JOIN 최적화)
-- ============================================================

-- platform_name + p_maker_code + status 복합 인덱스 (Maker JOIN)
CREATE INDEX IF NOT EXISTS idx_cz_code_map_platform_maker_status 
ON cz_code_map(platform_name, p_maker_code, status);

-- platform_name + p_model_group_code + status 복합 인덱스 (Model Group JOIN)
CREATE INDEX IF NOT EXISTS idx_cz_code_map_platform_model_group_status 
ON cz_code_map(platform_name, p_model_group_code, status);

-- platform_name + p_model_code + status 복합 인덱스 (Model JOIN)
CREATE INDEX IF NOT EXISTS idx_cz_code_map_platform_model_status 
ON cz_code_map(platform_name, p_model_code, status);

-- platform_name + p_trim_code + status 복합 인덱스 (Trim JOIN)
CREATE INDEX IF NOT EXISTS idx_cz_code_map_platform_trim_status 
ON cz_code_map(platform_name, p_trim_code, status);

-- platform_name + p_grade_code + status 복합 인덱스 (Grade JOIN)
CREATE INDEX IF NOT EXISTS idx_cz_code_map_platform_grade_status 
ON cz_code_map(platform_name, p_grade_code, status);

-- ============================================================
-- 3. car_master 테이블 인덱스
-- ============================================================

-- CAR_NO 인덱스 (ON DUPLICATE KEY UPDATE 최적화)
-- UNIQUE KEY가 이미 있으면 추가 불필요하지만, 조회 성능 향상을 위해 추가 가능
CREATE INDEX IF NOT EXISTS idx_car_master_car_no 
ON car_master(CAR_NO);

-- ============================================================
-- 참고사항
-- ============================================================

-- 성능 개선 팁:
-- 1. DATE() 함수 사용 시 인덱스를 활용할 수 없으므로, 가능하면 범위 검색 사용:
--    WHERE DATE(p.last_seen_date) = ? 
--    → WHERE p.last_seen_date >= ? AND p.last_seen_date < DATE_ADD(?, INTERVAL 1 DAY)
--
-- 2. COALESCE() 함수 사용 시에도 인덱스 활용이 제한될 수 있으나,
--    NULL 값 처리를 위해 필요하므로 유지하는 것이 좋음
--
-- 3. 인덱스 생성 후 ANALYZE TABLE 실행 권장:
--    ANALYZE TABLE platform_car;
--    ANALYZE TABLE cz_code_map;
--    ANALYZE TABLE car_master;

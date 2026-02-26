-- platform_car 테이블 성능 최적화 인덱스 추가
-- MySQL 5.7 호환: IF NOT EXISTS 대신 직접 생성
-- 이미 존재하는 경우 에러가 발생할 수 있으므로, 안전 버전(005_add_platform_car_indexes_safe.sql) 사용 권장

-- car_id와 last_seen_date 복합 인덱스 (closeMissingAds 쿼리 최적화)
CREATE INDEX idx_platform_car_car_id_last_seen 
ON platform_car(car_id, last_seen_date);

-- last_seen_date 단일 인덱스
CREATE INDEX idx_platform_car_last_seen_date 
ON platform_car(last_seen_date);

-- car_id 인덱스
CREATE INDEX idx_platform_car_car_id 
ON platform_car(car_id);

-- car_master adv_status 인덱스 (SOLD 처리 쿼리 최적화)
CREATE INDEX idx_car_master_adv_status 
ON car_master(adv_status);

-- car_master car_id 인덱스 (JOIN 최적화)
CREATE INDEX idx_car_master_car_id 
ON car_master(car_id);

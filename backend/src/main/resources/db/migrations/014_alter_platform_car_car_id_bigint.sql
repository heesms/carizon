-- platform_car.car_id 컬럼을 BIGINT로 변경 (기존 INT 등 대비)
-- 주의: "Commands out of sync" 나오면 이 파일에서 이 한 문장만 골라서 새 쿼리창/세션에서 단독 실행하세요.

ALTER TABLE platform_car MODIFY COLUMN car_id BIGINT NULL;

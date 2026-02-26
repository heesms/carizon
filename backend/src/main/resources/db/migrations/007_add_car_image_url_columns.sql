-- car_image_url 컬럼 추가 마이그레이션
-- 주의: 컬럼이 이미 존재하면 오류가 발생할 수 있습니다. 필요시 수동으로 확인 후 실행하세요.
-- 참고: TCAR는 car_image_url이 이미 존재하므로 제외, CHUTCHA는 fetched_at이 이미 존재하므로 제외

-- 1. raw_chachacha에 car_image_url 컬럼 추가
ALTER TABLE raw_chachacha 
ADD COLUMN car_image_url VARCHAR(500) NULL AFTER payload;

-- 2. raw_encar에 car_image_url 컬럼 추가
ALTER TABLE raw_encar 
ADD COLUMN car_image_url VARCHAR(500) NULL AFTER payload;

-- 3. raw_tcar에 car_image_url2 컬럼 추가 (car_image_url은 GENERATED COLUMN이므로 car_image_url2 사용)
ALTER TABLE raw_tcar 
ADD COLUMN car_image_url2 VARCHAR(500) NULL AFTER payload;

-- 4. raw_chutcha에 car_image_url 컬럼 추가 (fetched_at은 이미 존재)
ALTER TABLE raw_chutcha 
ADD COLUMN car_image_url VARCHAR(500) NULL AFTER payload;

-- 5. raw_charancha에 car_image_url 컬럼 추가
ALTER TABLE raw_charancha 
ADD COLUMN car_image_url VARCHAR(500) NULL AFTER payload;

-- 6. platform_car에 car_image_url 컬럼 추가
ALTER TABLE platform_car 
ADD COLUMN car_image_url VARCHAR(500) NULL AFTER extra;

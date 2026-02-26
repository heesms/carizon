-- 차량 최초가액(신차가격) 컬럼 추가
-- - raw_encar: encar category.originPrice 적재용
-- - platform_car: tcar payload.priceNew / encar raw_encar.price_new 반영
-- - car_master: platform_car.price_new 머지 (0/null이 아닐 때만)

-- 1. raw_encar에 price_new 컬럼 추가 (단위: 만원, encar originPrice)
ALTER TABLE raw_encar
ADD COLUMN price_new INT NULL COMMENT '신차가격(만원), category.originPrice' AFTER car_image_url;

-- 2. platform_car에 price_new 컬럼 추가 (단위: 만원)
ALTER TABLE platform_car
ADD COLUMN price_new INT NULL COMMENT '신차가격(만원)' AFTER car_image_url;

-- 3. car_master에 price_new 컬럼 추가 (단위: 만원)
ALTER TABLE car_master
ADD COLUMN price_new INT NULL COMMENT '신차가격(만원)' AFTER BODY_TYPE;

-- cz_model_new_price를 "모델당 1건" → "차량당 1건(n건)" 구조로 변경
-- 이미 011에서 새 스키마로 생성된 경우 이 스크립트는 무시하거나, 기존 테이블이 구 PK면 변경 적용

-- 기존 테이블이 (maker, model_group, model, trim, grade) PK인 구 스키마일 수 있음 → 재생성
DROP TABLE IF EXISTS cz_model_new_price;

CREATE TABLE cz_model_new_price (
  id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  car_id           BIGINT       NOT NULL COMMENT 'car_master.car_id, 1건당 1행',
  maker_code       VARCHAR(50)  NOT NULL,
  model_group_code VARCHAR(50)  NOT NULL,
  model_code       VARCHAR(50)  NOT NULL,
  trim_code        VARCHAR(50)  NOT NULL,
  grade_code       VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '없으면 빈문자열, 조회 시 null로 해석',
  price_new        INT          NOT NULL COMMENT '신차 출고가(만원)',
  updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_car_id (car_id),
  INDEX idx_model (maker_code, model_group_code, model_code, trim_code, grade_code)
) COMMENT '차량별 신차 출고가 - 같은 모델도 n건 쌓임, 점수 산정 시 모델별 AVG(price_new) 사용';

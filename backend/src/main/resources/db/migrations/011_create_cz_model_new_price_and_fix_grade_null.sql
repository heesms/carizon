-- 1) 모델별 신차 출고가 저장 테이블 (블로그 점수: 신차 대비 가격 비율용)
--    같은 등급이라도 차량마다 신차가가 조금씩 다를 수 있으므로 차량(car_id) 단위로 n건 저장.
--    조회 시 (maker, model_group, model, trim, grade)로 그룹해 AVG(price_new) 등 사용.
CREATE TABLE IF NOT EXISTS cz_model_new_price (
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

-- 2) car_master에 문자열 'null' 또는 빈값으로 들어간 GRADE_CODE를 실제 NULL로 정리
UPDATE car_master
SET GRADE_CODE = NULL
WHERE TRIM(COALESCE(GRADE_CODE, '')) = ''
   OR GRADE_CODE = 'null';

-- 3) 모델별 신차가 집계 배치 잡 정의 (master_merge 이후 수동/스케줄 실행)
INSERT INTO batch_job_definition (job_id, job_name, job_type, description, cron_expression, is_active) VALUES
('model_new_price_aggregate', '모델별 신차가 집계', 'MODEL_NEW_PRICE', 'car_master.price_new → cz_model_new_price 집계', '0 10 5 * * *', TRUE)
ON DUPLICATE KEY UPDATE job_name = VALUES(job_name), description = VALUES(description);

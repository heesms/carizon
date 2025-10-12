-- car_master (normalized master ledger)
CREATE TABLE IF NOT EXISTS car_master (
  car_id            BIGINT       NOT NULL AUTO_INCREMENT,
  car_no            VARCHAR(32)  NULL,
  maker_code        VARCHAR(32)  NULL,
  model_group_code  VARCHAR(32)  NULL,
  model_code        VARCHAR(32)  NULL,
  trim_code         VARCHAR(32)  NULL,
  grade_code        VARCHAR(32)  NULL,

  maker_name        VARCHAR(64)  NULL,
  model_group_name  VARCHAR(64)  NULL,
  model_name        VARCHAR(64)  NULL,
  trim_name         VARCHAR(64)  NULL,

  year              SMALLINT     NULL,
  mileage           INT          NULL,
  color             VARCHAR(64)  NULL,
  transmission      VARCHAR(32)  NULL,
  fuel              VARCHAR(32)  NULL,
  displacement      INT          NULL,
  body_type         VARCHAR(64)  NULL,
  region            VARCHAR(128) NULL,

  adv_status        VARCHAR(32)  NULL,
  last_seen_date    DATE         NULL,
  extra             JSON         NULL,

  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (car_id),
  KEY idx_cm_codes     (maker_code, model_group_code, model_code, trim_code, grade_code),
  KEY idx_cm_year_km   (year, mileage),
  KEY idx_cm_region    (region),
  KEY idx_cm_updated   (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- platform_car (source listing per platform)
CREATE TABLE IF NOT EXISTS platform_car (
  platform_car_id   BIGINT       NOT NULL AUTO_INCREMENT,
  platform_name     VARCHAR(32)  NOT NULL,
  platform_car_key  VARCHAR(128) NOT NULL,
  car_id            BIGINT       NULL,
  car_no            VARCHAR(32)  NULL,

  maker_code        VARCHAR(32)  NULL,
  model_group_code  VARCHAR(32)  NULL,
  model_code        VARCHAR(32)  NULL,
  trim_code         VARCHAR(32)  NULL,
  grade_code        VARCHAR(32)  NULL,

  maker_name        VARCHAR(64)  NULL,
  model_group_name  VARCHAR(64)  NULL,
  model_name        VARCHAR(64)  NULL,
  trim_name         VARCHAR(64)  NULL,

  price             INT          NULL,
  km                INT          NULL,
  displacement      INT          NULL,
  yymm              CHAR(6)      NULL,
  status            VARCHAR(32)  NULL,
  color             VARCHAR(64)  NULL,
  fuel              VARCHAR(32)  NULL,
  transmission      VARCHAR(32)  NULL,
  body_type         VARCHAR(64)  NULL,
  region            VARCHAR(128) NULL,

  m_url             VARCHAR(1024) NULL,
  pc_url            VARCHAR(1024) NULL,

  first_ad_day      DATE         NULL,
  last_seen_date    DATE         NULL,
  extra             JSON         NULL,

  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (platform_car_id),
  UNIQUE KEY uq_platform_key (platform_name, platform_car_key),
  KEY idx_pc_carid          (car_id),
  KEY idx_pc_codes          (maker_code, model_group_code, model_code, trim_code, grade_code),
  KEY idx_pc_price          (price),
  KEY idx_pc_yymm           (yymm),
  KEY idx_pc_region         (region),
  CONSTRAINT fk_pc_car
    FOREIGN KEY (car_id) REFERENCES car_master (car_id)
    ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- car_price_history (time series per platform_car)
CREATE TABLE IF NOT EXISTS car_price_history (
  history_id        BIGINT       NOT NULL AUTO_INCREMENT,
  platform_car_id   BIGINT       NOT NULL,
  price             INT          NOT NULL,
  checked_at        DATETIME     NOT NULL,
  is_current        TINYINT(1)   NOT NULL DEFAULT 0,
  last_seen_at      DATETIME     NULL,

  PRIMARY KEY (history_id),
  KEY idx_cph_pc_ts (platform_car_id, checked_at DESC),
  CONSTRAINT fk_cph_pc
    FOREIGN KEY (platform_car_id) REFERENCES platform_car (platform_car_id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- platform_code_mapping (raw ↔ standard 5-level mapping)
CREATE TABLE IF NOT EXISTS platform_code_mapping (
  mapping_id        BIGINT       NOT NULL AUTO_INCREMENT,
  platform_name     VARCHAR(32)  NOT NULL,
  level             ENUM('MAKER','MODEL_GROUP','MODEL','TRIM','GRADE') NOT NULL,
  raw_code          VARCHAR(128) NULL,
  raw_name          VARCHAR(255) NULL,
  standard_code     VARCHAR(64)  NOT NULL,
  confidence        DECIMAL(4,3) NULL,
  updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (mapping_id),
  UNIQUE KEY uq_pcm (platform_name, level, raw_code),
  KEY idx_pcm_raw_name (raw_name),
  KEY idx_pcm_std (standard_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Model images table for car model images
CREATE TABLE IF NOT EXISTS cz_model_image (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  model_code   VARCHAR(64) NOT NULL,
  image_url    VARCHAR(1024) NOT NULL,
  sort_order   INT NOT NULL DEFAULT 0,
  is_main      TINYINT(1) NOT NULL DEFAULT 0,
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_model_image UNIQUE (model_code, image_url)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE INDEX idx_cz_model_image_modelcode ON cz_model_image (model_code);
CREATE INDEX idx_cz_model_image_main_order ON cz_model_image (model_code, is_main, sort_order);

CREATE TABLE IF NOT EXISTS car_master_id_retain_map (
  run_id     VARCHAR(36) NOT NULL,
  car_no     VARCHAR(255) NOT NULL,
  car_id     BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (run_id, car_no),
  KEY idx_run_id (run_id),
  KEY idx_car_no (car_no),
  KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

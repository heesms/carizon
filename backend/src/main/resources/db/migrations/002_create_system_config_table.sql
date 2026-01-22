-- 시스템 설정 테이블
-- 검색 모드 등 시스템 전반의 설정을 관리하는 테이블

CREATE TABLE IF NOT EXISTS system_config (
  config_key VARCHAR(100) PRIMARY KEY COMMENT '설정 키',
  config_value TEXT COMMENT '설정 값 (JSON 또는 문자열)',
  description VARCHAR(500) COMMENT '설명',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
  INDEX idx_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='시스템 설정';

-- 초기 데이터 삽입
INSERT INTO system_config (config_key, config_value, description) VALUES
('search.use_meilisearch', 'true', 'Meilisearch 사용 여부'),
('search.fallback_to_db', 'true', 'Meilisearch 실패 시 DB 폴백 여부')
ON DUPLICATE KEY UPDATE 
  config_value = VALUES(config_value),
  description = VALUES(description);

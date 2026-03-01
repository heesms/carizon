-- API 호출 추적 테이블 (파이프라인 API 실행 이력)

CREATE TABLE IF NOT EXISTS api_run (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  run_id VARCHAR(36) NOT NULL UNIQUE COMMENT '실행 ID (UUID)',
  api_path VARCHAR(200) NOT NULL COMMENT 'API 경로 (예: /admin/pipeline/full)',
  api_method VARCHAR(10) NOT NULL DEFAULT 'POST' COMMENT 'HTTP 메서드',
  source VARCHAR(100) NOT NULL COMMENT 'API 소스/이름 (예: pipeline-full, merge-only)',
  status VARCHAR(20) NOT NULL DEFAULT 'STARTED' COMMENT '상태 (STARTED, SUCCESS, FAIL)',
  started_at TIMESTAMP NOT NULL COMMENT '시작 시간',
  ended_at TIMESTAMP NULL COMMENT '종료 시간',
  duration_ms BIGINT NULL COMMENT '실행 시간 (밀리초)',
  total_items INT DEFAULT 0 COMMENT '처리된 아이템 수',
  result_json TEXT NULL COMMENT '결과 데이터 (JSON)',
  message TEXT NULL COMMENT '에러 메시지 또는 추가 정보',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '레코드 생성 시간',
  INDEX idx_run_id (run_id),
  INDEX idx_source (source),
  INDEX idx_status (status),
  INDEX idx_started_at (started_at),
  INDEX idx_api_path (api_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='API 호출 실행 이력';

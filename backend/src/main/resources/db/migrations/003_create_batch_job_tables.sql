-- 배치 작업 관리 테이블

-- 배치 작업 정의 테이블
CREATE TABLE IF NOT EXISTS batch_job_definition (
  job_id VARCHAR(100) PRIMARY KEY COMMENT '작업 ID',
  job_name VARCHAR(200) NOT NULL COMMENT '작업 이름',
  job_type VARCHAR(50) NOT NULL COMMENT '작업 타입 (CRAWL, MERGE, EMBEDDING, INDEXING)',
  description VARCHAR(500) COMMENT '작업 설명',
  cron_expression VARCHAR(100) COMMENT '스케줄 표현식 (cron)',
  is_active BOOLEAN DEFAULT TRUE COMMENT '활성화 여부',
  config_json TEXT COMMENT '작업 설정 (JSON)',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_job_type (job_type),
  INDEX idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='배치 작업 정의';

-- 배치 작업 실행 이력 테이블
CREATE TABLE IF NOT EXISTS batch_job_execution (
  execution_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_id VARCHAR(100) NOT NULL COMMENT '작업 ID',
  job_name VARCHAR(200) NOT NULL COMMENT '작업 이름',
  status VARCHAR(20) NOT NULL COMMENT '상태 (PENDING, RUNNING, SUCCESS, FAILED, CANCELLED)',
  started_at TIMESTAMP NULL COMMENT '시작 시간',
  ended_at TIMESTAMP NULL COMMENT '종료 시간',
  duration_ms BIGINT COMMENT '실행 시간 (밀리초)',
  total_items INT DEFAULT 0 COMMENT '처리할 총 아이템 수',
  processed_items INT DEFAULT 0 COMMENT '처리된 아이템 수',
  success_items INT DEFAULT 0 COMMENT '성공한 아이템 수',
  failed_items INT DEFAULT 0 COMMENT '실패한 아이템 수',
  error_message TEXT COMMENT '에러 메시지',
  config_json TEXT COMMENT '실행 시 설정 (JSON)',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_job_id (job_id),
  INDEX idx_status (status),
  INDEX idx_started_at (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='배치 작업 실행 이력';

-- 워크플로우 정의 테이블 (여러 작업을 순차/병렬 실행)
CREATE TABLE IF NOT EXISTS batch_workflow_definition (
  workflow_id VARCHAR(100) PRIMARY KEY COMMENT '워크플로우 ID',
  workflow_name VARCHAR(200) NOT NULL COMMENT '워크플로우 이름',
  description VARCHAR(500) COMMENT '설명',
  job_sequence TEXT NOT NULL COMMENT '작업 순서 (JSON 배열: [{"jobId": "...", "dependsOn": [...]}])',
  is_active BOOLEAN DEFAULT TRUE COMMENT '활성화 여부',
  cron_expression VARCHAR(100) COMMENT '스케줄 표현식',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='워크플로우 정의';

-- 워크플로우 실행 이력 테이블
CREATE TABLE IF NOT EXISTS batch_workflow_execution (
  execution_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workflow_id VARCHAR(100) NOT NULL COMMENT '워크플로우 ID',
  workflow_name VARCHAR(200) NOT NULL COMMENT '워크플로우 이름',
  status VARCHAR(20) NOT NULL COMMENT '상태 (PENDING, RUNNING, SUCCESS, FAILED, CANCELLED)',
  started_at TIMESTAMP NULL COMMENT '시작 시간',
  ended_at TIMESTAMP NULL COMMENT '종료 시간',
  duration_ms BIGINT COMMENT '실행 시간 (밀리초)',
  error_message TEXT COMMENT '에러 메시지',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_workflow_id (workflow_id),
  INDEX idx_status (status),
  INDEX idx_started_at (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='워크플로우 실행 이력';

-- 초기 작업 정의 데이터
INSERT INTO batch_job_definition (job_id, job_name, job_type, description, cron_expression, is_active) VALUES
('crawl_all', '전체 크롤링', 'CRAWL', '모든 플랫폼 크롤링 실행', '0 15 3 * * *', TRUE),
('merge_all', '전체 데이터 머지', 'MERGE', 'platform_car 데이터 머지 및 car_master 생성', '0 30 4 * * *', TRUE),
('indexing_incremental', '증분 인덱싱', 'INDEXING', 'Meilisearch 증분 인덱싱', '0 0 5 * * *', TRUE),
('embedding_incremental', '증분 임베딩', 'EMBEDDING', 'Chroma 벡터 DB 증분 임베딩', '0 0 6 * * *', TRUE),
('indexing_batch', '배치 인덱싱', 'INDEXING', 'Meilisearch 배치 인덱싱 (1000건)', '0 0 * * * *', TRUE)
ON DUPLICATE KEY UPDATE job_name = VALUES(job_name), description = VALUES(description);

-- 초기 워크플로우 정의 데이터
INSERT INTO batch_workflow_definition (workflow_id, workflow_name, description, job_sequence, is_active, cron_expression) VALUES
('daily_pipeline', '일일 데이터 파이프라인', '크롤링 → 머지 → 인덱싱 → 임베딩 순차 실행', 
 '[
   {"jobId": "crawl_all", "dependsOn": []},
   {"jobId": "merge_all", "dependsOn": ["crawl_all"]},
   {"jobId": "indexing_incremental", "dependsOn": ["merge_all"]},
   {"jobId": "embedding_incremental", "dependsOn": ["merge_all"]}
 ]', 
 TRUE, '0 15 3 * * *')
ON DUPLICATE KEY UPDATE workflow_name = VALUES(workflow_name), description = VALUES(description);

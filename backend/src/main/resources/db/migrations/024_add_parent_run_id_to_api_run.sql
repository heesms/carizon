-- api_run에 parent_run_id 추가 (파이프라인 전체 실행과 각 스텝을 parent-child로 연결)
ALTER TABLE api_run
  ADD COLUMN parent_run_id VARCHAR(36) NULL COMMENT '상위 파이프라인 run_id (스텝인 경우)',
  ADD INDEX idx_parent_run_id (parent_run_id);

-- 코드 매핑 및 car_master 머지 작업 추가

-- 코드 매핑 작업 정의 추가
INSERT INTO batch_job_definition (job_id, job_name, job_type, description, cron_expression, is_active) VALUES
('code_mapping_all', '전체 코드 매핑', 'CODE_MAPPING', '모든 플랫폼의 코드 매핑 (platform_car → cz_code_map)', '0 45 4 * * *', TRUE),
('master_merge_all', 'car_master 머지', 'MASTER_MERGE', 'platform_car + cz_code_map → car_master 통합', '0 0 5 * * *', TRUE)
ON DUPLICATE KEY UPDATE job_name = VALUES(job_name), description = VALUES(description);

-- 워크플로우 업데이트: 코드 매핑 및 car_master 머지 단계 추가
UPDATE batch_workflow_definition
SET description = '크롤링 → 머지 → 코드 매핑 → car_master 머지 → 인덱싱 → 임베딩 순차 실행',
    job_sequence = '[
   {"jobId": "crawl_all", "dependsOn": []},
   {"jobId": "merge_all", "dependsOn": ["crawl_all"]},
   {"jobId": "code_mapping_all", "dependsOn": ["merge_all"]},
   {"jobId": "master_merge_all", "dependsOn": ["code_mapping_all"]},
   {"jobId": "indexing_incremental", "dependsOn": ["master_merge_all"]},
   {"jobId": "embedding_incremental", "dependsOn": ["master_merge_all"]}
 ]'
WHERE workflow_id = 'daily_pipeline';

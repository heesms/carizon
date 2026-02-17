-- 모델코드별 임베딩 소스 (DDD.txt / 엑셀 C열 = model_code, 임베딩용 3컬럼)
-- AI 추천 시 모델 전용 Chroma 컬렉션에 저장 후 활용
CREATE TABLE IF NOT EXISTS cz_model_embedding_source (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    model_code VARCHAR(32) NOT NULL COMMENT '모델코드 (cz_model.model_code와 동일)',
    model_name VARCHAR(200) NULL COMMENT '모델명 (참고용)',
    embed_text_1 TEXT NULL COMMENT '임베딩용 요약텍스트 (DDD 3열)',
    embed_text_2 TEXT NULL COMMENT '임베딩용 요약 텍스트2 (DDD 4열)',
    embed_text_3 TEXT NULL COMMENT '임베딩용 요약 텍스트3 (DDD 5열)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_model_code (model_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT '모델코드별 임베딩용 텍스트 (모델 전용 Chroma 컬렉션 저장용)';

-- 샘플 INSERT (실제 데이터는 DDD.txt 탭 구분 임포트 또는 API로 적재)
-- INSERT INTO cz_model_embedding_source (model_code, model_name, embed_text_1, embed_text_2, embed_text_3)
-- VALUES (
--   '1501',
--   '1시리즈',
--   'BMW 1시리즈 | 브랜드국가 독일 | 기타 | ...',
--   '모델=1시리즈 | 브랜드=BMW | 구분=수입 | ...',
--   '제조사=BMW | 모델=1시리즈 | 연식=2004.0~2012 | 한줄=수입차 선호 수요에서 자주 비교되는 라인업.'
-- );

-- cz_model_embedding_source.model_code collation을 car_master.model_code(utf8mb4_general_ci)와 일치시킴
-- JOIN 시 collation 충돌 방지
ALTER TABLE cz_model_embedding_source
    MODIFY model_code VARCHAR(32) NOT NULL COLLATE utf8mb4_general_ci COMMENT '모델코드';

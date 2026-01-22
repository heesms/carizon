-- LLM 설정 관리 테이블

-- LLM 프롬프트 설정 테이블
CREATE TABLE IF NOT EXISTS llm_prompt_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  prompt_type VARCHAR(50) NOT NULL UNIQUE COMMENT '프롬프트 타입 (intro, instruction, car-format 등)',
  prompt_text TEXT NOT NULL COMMENT '프롬프트 내용',
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_prompt_type (prompt_type),
  INDEX idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='LLM 프롬프트 설정';

-- LLM 매칭 가중치 설정 테이블
CREATE TABLE IF NOT EXISTS llm_matching_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  config_key VARCHAR(100) NOT NULL UNIQUE COMMENT '설정 키',
  config_value DECIMAL(10,4) NOT NULL COMMENT '설정 값 (가중치, 임계값 등)',
  description VARCHAR(500) COMMENT '설명',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='LLM 매칭 가중치 설정';

-- 초기 데이터 삽입
INSERT INTO llm_prompt_config (prompt_type, prompt_text, is_active) VALUES
('intro', '사용자가 다음과 같은 요구사항으로 중고차를 찾고 있습니다:', TRUE),
('price-range-format', '가격 범위: ${minPrice}만원 이상 ${maxPrice}만원 이하', TRUE),
('car-list-title', '검색된 차량 목록:', TRUE),
('car-format', '${index}. ${maker} ${model} ${trim} (${year}년식) 주행거리: ${mileage}km 가격: ${price}만원', TRUE),
('instruction', '위 차량 목록을 바탕으로 사용자에게 친절하고 자연스러운 한국어로 추천 설명을 작성해주세요.\n각 차량의 특징과 사용자 요구사항과의 매칭 포인트를 설명해주세요.\n너무 길지 않게 3-5문장 정도로 간결하게 작성해주세요.', TRUE),
('default-recommendation', '검색된 차량 중에서 요구사항에 맞는 차량을 추천드립니다.', TRUE),
('high-similarity-message', '요구사항과 높은 유사도(${score}%)를 보입니다. ', TRUE),
('within-budget-message', '예산 범위 내의 가격입니다. ', TRUE),
('low-mileage-message', '주행거리가 적어 상태가 양호할 가능성이 높습니다. ', TRUE),
('default-message', '검색 조건과 일치합니다.', TRUE)
ON DUPLICATE KEY UPDATE prompt_text = VALUES(prompt_text);

-- 매칭 가중치 초기 데이터
INSERT INTO llm_matching_config (config_key, config_value, description) VALUES
('similarity.high_threshold', 0.7000, '높은 유사도 임계값 (0.0 ~ 1.0)'),
('similarity.price_weight', 0.3000, '가격 일치 가중치'),
('similarity.maker_weight', 0.2500, '제조사 일치 가중치'),
('similarity.model_weight', 0.2500, '모델명 일치 가중치'),
('similarity.body_type_weight', 0.1000, '차종 일치 가중치'),
('similarity.fuel_weight', 0.0500, '연료 일치 가중치'),
('similarity.transmission_weight', 0.0500, '변속기 일치 가중치'),
('mileage.low_threshold', 50000.0000, '저주행거리 임계값 (km)'),
('price.tolerance_percent', 10.0000, '가격 허용 오차율 (%)'),
('search.max_results_multiplier', 3.0000, '검색 결과 배수 (최종 결과의 3배)')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);

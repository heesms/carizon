package com.carizon.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 설정 서비스
 * DB에서 LLM 프롬프트 및 매칭 가중치를 조회
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmConfigService {

    private final JdbcTemplate jdbc;

    /**
     * 프롬프트 조회
     */
    public String getPrompt(String promptType) {
        try {
            List<String> results = jdbc.queryForList("""
                SELECT prompt_text
                FROM llm_prompt_config
                WHERE prompt_type = ? AND is_active = TRUE
                """, String.class, promptType);
            
            if (!results.isEmpty()) {
                return results.get(0);
            }
            
            // DB에 없으면 기본값 반환
            return getDefaultPrompt(promptType);
        } catch (Exception e) {
            log.warn("[LLM config] prompt fetch failed: {}, using default", promptType, e);
            return getDefaultPrompt(promptType);
        }
    }

    /**
     * 매칭 가중치 조회
     */
    public BigDecimal getMatchingWeight(String configKey) {
        try {
            List<BigDecimal> results = jdbc.queryForList("""
                SELECT config_value
                FROM llm_matching_config
                WHERE config_key = ?
                """, BigDecimal.class, configKey);
            
            if (!results.isEmpty()) {
                return results.get(0);
            }
            
            // DB에 없으면 기본값 반환
            return getDefaultWeight(configKey);
        } catch (Exception e) {
            log.warn("[LLM config] weight fetch failed: {}, using default", configKey, e);
            return getDefaultWeight(configKey);
        }
    }

    /**
     * 모든 매칭 가중치 조회
     */
    public Map<String, BigDecimal> getAllMatchingWeights() {
        try {
            List<Map<String, Object>> configs = jdbc.queryForList("""
                SELECT config_key, config_value
                FROM llm_matching_config
                """);
            
            Map<String, BigDecimal> weights = new HashMap<>();
            for (Map<String, Object> config : configs) {
                String key = (String) config.get("config_key");
                BigDecimal value = (BigDecimal) config.get("config_value");
                weights.put(key, value);
            }
            
            // 기본값으로 채우기
            fillDefaultWeights(weights);
            
            return weights;
        } catch (Exception e) {
            log.warn("[LLM config] weight fetch all failed, using default", e);
            return getDefaultWeights();
        }
    }

    /**
     * 유사도 임계값 조회
     */
    public double getHighSimilarityThreshold() {
        return getMatchingWeight("similarity.high_threshold").doubleValue();
    }

    /**
     * 가격 일치 가중치
     */
    public double getPriceWeight() {
        return getMatchingWeight("similarity.price_weight").doubleValue();
    }

    /**
     * 제조사 일치 가중치
     */
    public double getMakerWeight() {
        return getMatchingWeight("similarity.maker_weight").doubleValue();
    }

    /**
     * 모델명 일치 가중치
     */
    public double getModelWeight() {
        return getMatchingWeight("similarity.model_weight").doubleValue();
    }

    /**
     * 차종 일치 가중치
     */
    public double getBodyTypeWeight() {
        return getMatchingWeight("similarity.body_type_weight").doubleValue();
    }

    /**
     * 연료 일치 가중치
     */
    public double getFuelWeight() {
        return getMatchingWeight("similarity.fuel_weight").doubleValue();
    }

    /**
     * 변속기 일치 가중치
     */
    public double getTransmissionWeight() {
        return getMatchingWeight("similarity.transmission_weight").doubleValue();
    }

    /**
     * 저주행거리 임계값
     */
    public int getLowMileageThreshold() {
        return getMatchingWeight("mileage.low_threshold").intValue();
    }

    /**
     * 가격 허용 오차율
     */
    public double getPriceTolerancePercent() {
        return getMatchingWeight("price.tolerance_percent").doubleValue();
    }

    /**
     * 검색 결과 배수
     */
    public int getSearchMaxResultsMultiplier() {
        return getMatchingWeight("search.max_results_multiplier").intValue();
    }

    // -------- 기본값 --------

    private String getDefaultPrompt(String promptType) {
        Map<String, String> defaults = new HashMap<>();
        defaults.put("intro", "사용자가 다음과 같은 요구사항으로 중고차를 찾고 있습니다:");
        defaults.put("price-range-format", "가격 범위: ${minPrice}만원 이상 ${maxPrice}만원 이하");
        defaults.put("car-list-title", "검색된 차량 목록:");
        defaults.put("car-format", "${index}. ${maker} ${model} ${trim} (${year}년식) 주행거리: ${mileage}km 가격: ${price}만원");
        defaults.put("instruction",
            "아래 형식으로 한국어로 작성해주세요. 반드시 위 차량 목록만 언급하세요.\n"
            + "1) 질문 이해: 사용자 요구사항을 어떻게 이해했는지 1~2문장으로 적어주세요.\n"
            + "2) 선택 이유: 왜 이 차량들을 추천했는지(전체적인 선정 기준) 1~2문장으로 적어주세요.\n"
            + "3) 5대 차량 선정 이유: 위 목록의 1번~5번 차량에 대해, 각 차량을 왜 추천했는지 한 대씩 1문장씩 적어주세요. (예: 1번 볼보 XC60 - ... 2번 ...)\n"
            + "친절하고 자연스럽게, 너무 길지 않게 작성해주세요.");
        defaults.put("default-recommendation", "검색된 차량 중에서 요구사항에 맞는 차량을 추천드립니다.");
        defaults.put("interpret-search-query",
            "사용자가 중고차 추천을 요청했습니다. 아래 요청을 '중고차 검색에 쓸 한 줄 키워드'로만 바꿔주세요.\n"
                + "예: 7명 가족 큰차 필요해 → 7인승 미니밴 SUV 대형 가족용. 메이커·모델·연료·연식을 사용자가 안 말했으면 추론해서 보충하되, 검색어만 한 줄로 출력하세요. 다른 설명 없이 검색어 한 줄만 한국어로.\n\n사용자 요청:\n${query}");
        defaults.put("high-similarity-message", "Carizon 점수 ${score}점으로 요구사항과 잘 맞습니다. ");
        defaults.put("within-budget-message", "예산 범위 내의 가격입니다. ");
        defaults.put("low-mileage-message", "주행거리가 적어 상태가 양호할 가능성이 높습니다. ");
        defaults.put("default-message", "검색 조건과 일치합니다.");
        return defaults.getOrDefault(promptType, "");
    }

    private BigDecimal getDefaultWeight(String configKey) {
        Map<String, BigDecimal> defaults = new HashMap<>();
        defaults.put("similarity.high_threshold", BigDecimal.valueOf(0.7));
        defaults.put("similarity.price_weight", BigDecimal.valueOf(0.3));
        defaults.put("similarity.maker_weight", BigDecimal.valueOf(0.25));
        defaults.put("similarity.model_weight", BigDecimal.valueOf(0.25));
        defaults.put("similarity.body_type_weight", BigDecimal.valueOf(0.1));
        defaults.put("similarity.fuel_weight", BigDecimal.valueOf(0.05));
        defaults.put("similarity.transmission_weight", BigDecimal.valueOf(0.05));
        defaults.put("mileage.low_threshold", BigDecimal.valueOf(50000));
        defaults.put("price.tolerance_percent", BigDecimal.valueOf(10.0));
        defaults.put("search.max_results_multiplier", BigDecimal.valueOf(3));
        return defaults.getOrDefault(configKey, BigDecimal.ZERO);
    }

    private Map<String, BigDecimal> getDefaultWeights() {
        Map<String, BigDecimal> weights = new HashMap<>();
        weights.put("similarity.high_threshold", BigDecimal.valueOf(0.7));
        weights.put("similarity.price_weight", BigDecimal.valueOf(0.3));
        weights.put("similarity.maker_weight", BigDecimal.valueOf(0.25));
        weights.put("similarity.model_weight", BigDecimal.valueOf(0.25));
        weights.put("similarity.body_type_weight", BigDecimal.valueOf(0.1));
        weights.put("similarity.fuel_weight", BigDecimal.valueOf(0.05));
        weights.put("similarity.transmission_weight", BigDecimal.valueOf(0.05));
        weights.put("mileage.low_threshold", BigDecimal.valueOf(50000));
        weights.put("price.tolerance_percent", BigDecimal.valueOf(10.0));
        weights.put("search.max_results_multiplier", BigDecimal.valueOf(3));
        return weights;
    }

    private void fillDefaultWeights(Map<String, BigDecimal> weights) {
        Map<String, BigDecimal> defaults = getDefaultWeights();
        for (Map.Entry<String, BigDecimal> entry : defaults.entrySet()) {
            weights.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }
}

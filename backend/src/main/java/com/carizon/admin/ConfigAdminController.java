package com.carizon.admin;

import com.carizon.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 설정 관리 컨트롤러
 * 검색 설정, 코드 관리, LLM 문구 관리 등
 */
@Slf4j
@RestController
@RequestMapping("/admin/config")
@RequiredArgsConstructor
@Tag(name = "설정 관리", description = "시스템 설정 관리")
public class ConfigAdminController {

    private static final String CFG_V2_USE_OLLAMA_PARSER = "recommendation.v2.use_ollama_parser";
    private static final String CFG_V2_USE_OLLAMA_EXPLAIN = "recommendation.v2.use_ollama_explain";
    private static final String CFG_V2_OLLAMA_TIMEOUT_MS = "recommendation.v2.ollama_timeout_ms";
    private static final String CFG_V2_OLLAMA_FAIL_OPEN = "recommendation.v2.ollama_fail_open";
    private static final String CFG_V2_WEIGHT_MODEL_FIT = "recommendation.v2.weight.model_fit";
    private static final String CFG_V2_WEIGHT_BUSINESS = "recommendation.v2.weight.business";
    private static final String CFG_V2_WEIGHT_VECTOR = "recommendation.v2.weight.vector";
    private static final String CFG_V2_WEIGHT_KEYWORD = "recommendation.v2.weight.keyword";
    private static final String CFG_V2_DIVERSIFY_MAX_PER_MODEL = "recommendation.v2.diversify.max_per_model";
    private static final String CFG_V2_RETRIEVE_MODEL_TOP_M = "recommendation.v2.retrieve.model_top_m";
    private static final String CFG_V2_RETRIEVE_VECTOR_TOP_K = "recommendation.v2.retrieve.vector_top_k";
    private static final String CFG_V2_RETRIEVE_KEYWORD_TOP_K = "recommendation.v2.retrieve.keyword_top_k";
    private static final String CFG_V2_RETRIEVE_MODEL_LISTING_LIMIT = "recommendation.v2.retrieve.model_listing_limit";
    private static final String CFG_V2_RETRIEVE_MODEL_LISTING_CODES = "recommendation.v2.retrieve.model_listing_codes";

    private final JdbcTemplate jdbc;

    @GetMapping("/search-mode")
    @Operation(summary = "검색 모드 조회", description = "Meilisearch 사용 여부 조회")
    public ApiResponse<Map<String, Object>> getSearchMode() {
        try {
            Map<String, Object> config = new HashMap<>();
            
            // system_config 테이블에서 조회 시도
            try {
                String useMeilisearch = jdbc.queryForObject(
                    "SELECT config_value FROM system_config WHERE config_key = ?",
                    String.class,
                    "search.use_meilisearch"
                );
                String fallbackToDb = jdbc.queryForObject(
                    "SELECT config_value FROM system_config WHERE config_key = ?",
                    String.class,
                    "search.fallback_to_db"
                );
                
                config.put("useMeilisearch", Boolean.parseBoolean(useMeilisearch));
                config.put("fallbackToDb", Boolean.parseBoolean(fallbackToDb));
            } catch (Exception e) {
                // 테이블이 없거나 데이터가 없으면 기본값 반환
                log.warn("[config] system_config fetch failed, using default: {}", e.getMessage());
                config.put("useMeilisearch", true);
                config.put("fallbackToDb", true);
            }
            
            return ApiResponse.success(config);
        } catch (Exception e) {
            log.error("[config] search mode fetch failed", e);
            return ApiResponse.error("조회 실패: " + e.getMessage());
        }
    }

    @PostMapping("/search-mode")
    @Operation(summary = "검색 모드 설정", description = "Meilisearch 사용 여부 설정")
    public ApiResponse<String> setSearchMode(@RequestBody Map<String, Object> config) {
        try {
            Boolean useMeilisearch = (Boolean) config.get("useMeilisearch");
            Boolean fallbackToDb = (Boolean) config.get("fallbackToDb");
            
            if (useMeilisearch == null || fallbackToDb == null) {
                return ApiResponse.error("useMeilisearch와 fallbackToDb 값이 필요합니다");
            }
            
            // system_config 테이블에 저장
            jdbc.update("""
                INSERT INTO system_config (config_key, config_value, description)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    config_value = VALUES(config_value),
                    description = VALUES(description),
                    updated_at = CURRENT_TIMESTAMP
                """,
                "search.use_meilisearch",
                String.valueOf(useMeilisearch),
                "Meilisearch 사용 여부"
            );
            
            jdbc.update("""
                INSERT INTO system_config (config_key, config_value, description)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    config_value = VALUES(config_value),
                    description = VALUES(description),
                    updated_at = CURRENT_TIMESTAMP
                """,
                "search.fallback_to_db",
                String.valueOf(fallbackToDb),
                "Meilisearch 실패 시 DB 폴백 여부"
            );
            
            log.info("[config] search mode changed: useMeilisearch={}, fallbackToDb={}", useMeilisearch, fallbackToDb);
            return ApiResponse.success("설정 저장 완료");
        } catch (Exception e) {
            log.error("[config] search mode set failed", e);
            return ApiResponse.error("설정 실패: " + e.getMessage());
        }
    }

    @GetMapping("/codes")
    @Operation(summary = "코드 목록 조회", description = "공통 코드 및 차량 코드 조회")
    public ApiResponse<Map<String, Object>> getCodes(
            @RequestParam(required = false) String type) {
        try {
            Map<String, Object> result = new HashMap<>();
            
            if (type == null || "maker".equals(type)) {
                List<Map<String, Object>> makers = jdbc.queryForList(
                    "SELECT maker_code as code, maker_name as name FROM cz_maker ORDER BY maker_name");
                result.put("makers", makers);
            }
            
            if (type == null || "model".equals(type)) {
                List<Map<String, Object>> models = jdbc.queryForList("""
                    SELECT model_code as code, model_name as name, maker_code
                    FROM cz_model
                    ORDER BY maker_code, model_name
                    LIMIT 1000
                    """);
                result.put("models", models);
            }
            
            if (type == null || "code-map".equals(type)) {
                List<Map<String, Object>> codeMaps = jdbc.queryForList("""
                    SELECT id, platform_name, level, platform_code, maker_code, 
                           model_code, status, created_at
                    FROM cz_code_map
                    ORDER BY platform_name, level, created_at DESC
                    LIMIT 100
                    """);
                result.put("codeMaps", codeMaps);
            }
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[config] code fetch failed", e);
            return ApiResponse.error("조회 실패: " + e.getMessage());
        }
    }

    @GetMapping("/llm-prompts")
    @Operation(summary = "LLM 프롬프트 조회", description = "추천 LLM 문구 조회 (레거시, /admin/config/llm/prompts 사용 권장)")
    public ApiResponse<Map<String, Object>> getLlmPrompts() {
        try {
            // 레거시 엔드포인트, 새로운 LlmConfigAdminController 사용 권장
            List<Map<String, Object>> prompts = jdbc.queryForList("""
                SELECT prompt_type, prompt_text, is_active
                FROM llm_prompt_config
                ORDER BY prompt_type
                """);
            
            Map<String, Object> result = new HashMap<>();
            result.put("prompts", prompts);
            result.put("note", "새로운 엔드포인트 /admin/config/llm/prompts 사용을 권장합니다.");
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[config] LLM prompt fetch failed", e);
            return ApiResponse.error("조회 실패: " + e.getMessage());
        }
    }

    @GetMapping("/recommendation-v2")
    @Operation(summary = "추천 v2 설정 조회", description = "추천 v2 Ollama 파서/설명 토글, timeout, fail-open 설정 조회")
    public ApiResponse<Map<String, Object>> getRecommendationV2Config() {
        try {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("useOllamaParser", readBooleanConfig(CFG_V2_USE_OLLAMA_PARSER, true));
            out.put("useOllamaExplain", readBooleanConfig(CFG_V2_USE_OLLAMA_EXPLAIN, true));
            out.put("ollamaTimeoutMs", readIntConfig(CFG_V2_OLLAMA_TIMEOUT_MS, 1200));
            out.put("ollamaFailOpen", readBooleanConfig(CFG_V2_OLLAMA_FAIL_OPEN, true));
            out.put("modelFitWeight", readDoubleConfig(CFG_V2_WEIGHT_MODEL_FIT, 0.40));
            out.put("businessWeight", readDoubleConfig(CFG_V2_WEIGHT_BUSINESS, 0.30));
            out.put("vectorWeight", readDoubleConfig(CFG_V2_WEIGHT_VECTOR, 0.20));
            out.put("keywordWeight", readDoubleConfig(CFG_V2_WEIGHT_KEYWORD, 0.10));
            out.put("diversifyMaxPerModel", readIntConfig(CFG_V2_DIVERSIFY_MAX_PER_MODEL, 2));
            out.put("modelTopM", readIntConfig(CFG_V2_RETRIEVE_MODEL_TOP_M, 20));
            out.put("vectorTopK", readIntConfig(CFG_V2_RETRIEVE_VECTOR_TOP_K, 80));
            out.put("keywordTopK", readIntConfig(CFG_V2_RETRIEVE_KEYWORD_TOP_K, 80));
            out.put("modelListingLimit", readIntConfig(CFG_V2_RETRIEVE_MODEL_LISTING_LIMIT, 120));
            out.put("modelListingCodes", readIntConfig(CFG_V2_RETRIEVE_MODEL_LISTING_CODES, 12));
            return ApiResponse.success(out);
        } catch (Exception e) {
            log.error("[config] recommendation-v2 fetch failed", e);
            return ApiResponse.error("조회 실패: " + e.getMessage());
        }
    }

    @PostMapping("/recommendation-v2")
    @Operation(summary = "추천 v2 설정 저장", description = "추천 v2 Ollama 파서/설명 토글, timeout, fail-open 설정 저장")
    public ApiResponse<String> setRecommendationV2Config(@RequestBody Map<String, Object> config) {
        try {
            boolean useParser = parseBoolean(config.get("useOllamaParser"), true);
            boolean useExplain = parseBoolean(config.get("useOllamaExplain"), true);
            int timeoutMs = parseInt(config.get("ollamaTimeoutMs"), 1200);
            if (timeoutMs < 300) timeoutMs = 300;
            if (timeoutMs > 10000) timeoutMs = 10000;
            boolean failOpen = parseBoolean(config.get("ollamaFailOpen"), true);
            double modelFitWeight = clampDouble(parseDouble(config.get("modelFitWeight"), 0.40), 0.0, 1.5);
            double businessWeight = clampDouble(parseDouble(config.get("businessWeight"), 0.30), 0.0, 1.5);
            double vectorWeight = clampDouble(parseDouble(config.get("vectorWeight"), 0.20), 0.0, 1.5);
            double keywordWeight = clampDouble(parseDouble(config.get("keywordWeight"), 0.10), 0.0, 1.5);
            int diversifyMaxPerModel = parseInt(config.get("diversifyMaxPerModel"), 2);
            if (diversifyMaxPerModel < 1) diversifyMaxPerModel = 1;
            if (diversifyMaxPerModel > 10) diversifyMaxPerModel = 10;
            int modelTopM = parseInt(config.get("modelTopM"), 20);
            if (modelTopM < 1) modelTopM = 1;
            if (modelTopM > 100) modelTopM = 100;
            int vectorTopK = parseInt(config.get("vectorTopK"), 80);
            if (vectorTopK < 10) vectorTopK = 10;
            if (vectorTopK > 400) vectorTopK = 400;
            int keywordTopK = parseInt(config.get("keywordTopK"), 80);
            if (keywordTopK < 10) keywordTopK = 10;
            if (keywordTopK > 400) keywordTopK = 400;
            int modelListingLimit = parseInt(config.get("modelListingLimit"), 120);
            if (modelListingLimit < 10) modelListingLimit = 10;
            if (modelListingLimit > 500) modelListingLimit = 500;
            int modelListingCodes = parseInt(config.get("modelListingCodes"), 12);
            if (modelListingCodes < 1) modelListingCodes = 1;
            if (modelListingCodes > 50) modelListingCodes = 50;

            upsertConfig(CFG_V2_USE_OLLAMA_PARSER, String.valueOf(useParser), "추천 v2 Query Parser에 Ollama 사용 여부");
            upsertConfig(CFG_V2_USE_OLLAMA_EXPLAIN, String.valueOf(useExplain), "추천 v2 설명 생성에 Ollama 사용 여부");
            upsertConfig(CFG_V2_OLLAMA_TIMEOUT_MS, String.valueOf(timeoutMs), "추천 v2 Ollama 타임아웃(ms)");
            upsertConfig(CFG_V2_OLLAMA_FAIL_OPEN, String.valueOf(failOpen), "추천 v2 Ollama 실패 시 fail-open 여부");
            upsertConfig(CFG_V2_WEIGHT_MODEL_FIT, String.valueOf(modelFitWeight), "추천 v2 최종 스코어 가중치(model fit)");
            upsertConfig(CFG_V2_WEIGHT_BUSINESS, String.valueOf(businessWeight), "추천 v2 최종 스코어 가중치(business)");
            upsertConfig(CFG_V2_WEIGHT_VECTOR, String.valueOf(vectorWeight), "추천 v2 최종 스코어 가중치(vector)");
            upsertConfig(CFG_V2_WEIGHT_KEYWORD, String.valueOf(keywordWeight), "추천 v2 최종 스코어 가중치(keyword)");
            upsertConfig(CFG_V2_DIVERSIFY_MAX_PER_MODEL, String.valueOf(diversifyMaxPerModel), "추천 v2 다양성: 모델당 최대 노출 수");
            upsertConfig(CFG_V2_RETRIEVE_MODEL_TOP_M, String.valueOf(modelTopM), "추천 v2 모델 임베딩 후보 Top-M");
            upsertConfig(CFG_V2_RETRIEVE_VECTOR_TOP_K, String.valueOf(vectorTopK), "추천 v2 벡터 후보 Top-K");
            upsertConfig(CFG_V2_RETRIEVE_KEYWORD_TOP_K, String.valueOf(keywordTopK), "추천 v2 키워드 후보 Top-K");
            upsertConfig(CFG_V2_RETRIEVE_MODEL_LISTING_LIMIT, String.valueOf(modelListingLimit), "추천 v2 모델기반 DB 후보 조회 limit");
            upsertConfig(CFG_V2_RETRIEVE_MODEL_LISTING_CODES, String.valueOf(modelListingCodes), "추천 v2 모델기반 조회 modelCode 최대 개수");

            log.info("[config] recommendation-v2 updated: parser={}, explain={}, timeoutMs={}, failOpen={}",
                    useParser, useExplain, timeoutMs, failOpen);
            return ApiResponse.success("설정 저장 완료");
        } catch (Exception e) {
            log.error("[config] recommendation-v2 set failed", e);
            return ApiResponse.error("설정 실패: " + e.getMessage());
        }
    }

    private void upsertConfig(String key, String value, String desc) {
        jdbc.update("""
            INSERT INTO system_config (config_key, config_value, description)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE
                config_value = VALUES(config_value),
                description = VALUES(description),
                updated_at = CURRENT_TIMESTAMP
            """, key, value, desc);
    }

    private boolean readBooleanConfig(String key, boolean defaultValue) {
        try {
            String value = jdbc.queryForObject(
                    "SELECT config_value FROM system_config WHERE config_key = ?",
                    String.class,
                    key
            );
            if (value == null || value.isBlank()) return defaultValue;
            return Boolean.parseBoolean(value.trim());
        } catch (Exception ignore) {
            return defaultValue;
        }
    }

    private int readIntConfig(String key, int defaultValue) {
        try {
            String value = jdbc.queryForObject(
                    "SELECT config_value FROM system_config WHERE config_key = ?",
                    String.class,
                    key
            );
            if (value == null || value.isBlank()) return defaultValue;
            return Integer.parseInt(value.trim());
        } catch (Exception ignore) {
            return defaultValue;
        }
    }

    private double readDoubleConfig(String key, double defaultValue) {
        try {
            String value = jdbc.queryForObject(
                    "SELECT config_value FROM system_config WHERE config_key = ?",
                    String.class,
                    key
            );
            if (value == null || value.isBlank()) return defaultValue;
            return Double.parseDouble(value.trim());
        } catch (Exception ignore) {
            return defaultValue;
        }
    }

    private static boolean parseBoolean(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(value).trim());
    }

    private static int parseInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        try {
            if (value instanceof Number n) return n.intValue();
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static double parseDouble(Object value, double defaultValue) {
        if (value == null) return defaultValue;
        try {
            if (value instanceof Number n) return n.doubleValue();
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

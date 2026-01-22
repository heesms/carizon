package com.carizon.admin;

import com.carizon.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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
                log.warn("[설정] system_config 테이블 조회 실패, 기본값 사용: {}", e.getMessage());
                config.put("useMeilisearch", true);
                config.put("fallbackToDb", true);
            }
            
            return ApiResponse.success(config);
        } catch (Exception e) {
            log.error("[설정] 검색 모드 조회 실패", e);
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
            
            log.info("[설정] 검색 모드 변경: useMeilisearch={}, fallbackToDb={}", useMeilisearch, fallbackToDb);
            return ApiResponse.success("설정 저장 완료");
        } catch (Exception e) {
            log.error("[설정] 검색 모드 설정 실패", e);
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
            log.error("[설정] 코드 조회 실패", e);
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
            log.error("[설정] LLM 프롬프트 조회 실패", e);
            return ApiResponse.error("조회 실패: " + e.getMessage());
        }
    }
}

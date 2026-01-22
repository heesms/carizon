package com.carizon.admin;

import com.carizon.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 설정 관리 컨트롤러
 * 프롬프트 및 매칭 가중치 관리
 */
@Slf4j
@RestController
@RequestMapping("/admin/config/llm")
@RequiredArgsConstructor
@Tag(name = "LLM 설정 관리", description = "LLM 프롬프트 및 매칭 가중치 관리")
public class LlmConfigAdminController {

    private final JdbcTemplate jdbc;

    @GetMapping("/prompts")
    @Operation(summary = "LLM 프롬프트 조회", description = "모든 LLM 프롬프트 설정 조회")
    public ApiResponse<List<Map<String, Object>>> getPrompts() {
        try {
            List<Map<String, Object>> prompts = jdbc.queryForList("""
                SELECT id, prompt_type, prompt_text, is_active, created_at, updated_at
                FROM llm_prompt_config
                ORDER BY prompt_type
                """);
            return ApiResponse.success(prompts);
        } catch (Exception e) {
            log.error("[LLM 설정] 프롬프트 조회 실패", e);
            return ApiResponse.error("조회 실패: " + e.getMessage());
        }
    }

    @GetMapping("/prompts/{type}")
    @Operation(summary = "특정 프롬프트 조회", description = "프롬프트 타입으로 조회")
    public ApiResponse<Map<String, Object>> getPrompt(@PathVariable String type) {
        try {
            List<Map<String, Object>> results = jdbc.queryForList("""
                SELECT id, prompt_type, prompt_text, is_active, created_at, updated_at
                FROM llm_prompt_config
                WHERE prompt_type = ?
                """, type);
            
            if (results.isEmpty()) {
                return ApiResponse.error("프롬프트를 찾을 수 없습니다: " + type);
            }
            
            return ApiResponse.success(results.get(0));
        } catch (Exception e) {
            log.error("[LLM 설정] 프롬프트 조회 실패", e);
            return ApiResponse.error("조회 실패: " + e.getMessage());
        }
    }

    @PostMapping("/prompts/{type}")
    @Operation(summary = "프롬프트 수정", description = "프롬프트 내용 수정")
    public ApiResponse<String> updatePrompt(
            @PathVariable String type,
            @RequestBody Map<String, Object> request) {
        try {
            String promptText = (String) request.get("prompt_text");
            Boolean isActive = request.get("is_active") != null ? 
                (Boolean) request.get("is_active") : true;
            
            int updated = jdbc.update("""
                UPDATE llm_prompt_config
                SET prompt_text = ?, is_active = ?, updated_at = NOW()
                WHERE prompt_type = ?
                """, promptText, isActive, type);
            
            if (updated == 0) {
                // 없으면 생성
                jdbc.update("""
                    INSERT INTO llm_prompt_config (prompt_type, prompt_text, is_active)
                    VALUES (?, ?, ?)
                    """, type, promptText, isActive);
            }
            
            return ApiResponse.success("프롬프트 저장 완료");
        } catch (Exception e) {
            log.error("[LLM 설정] 프롬프트 수정 실패", e);
            return ApiResponse.error("수정 실패: " + e.getMessage());
        }
    }

    @GetMapping("/matching")
    @Operation(summary = "매칭 가중치 조회", description = "모든 매칭 가중치 설정 조회")
    public ApiResponse<Map<String, Object>> getMatchingConfig() {
        try {
            List<Map<String, Object>> configs = jdbc.queryForList("""
                SELECT config_key, config_value, description, updated_at
                FROM llm_matching_config
                ORDER BY config_key
                """);
            
            Map<String, Object> result = new HashMap<>();
            for (Map<String, Object> config : configs) {
                result.put((String) config.get("config_key"), config);
            }
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[LLM 설정] 매칭 설정 조회 실패", e);
            return ApiResponse.error("조회 실패: " + e.getMessage());
        }
    }

    @PostMapping("/matching/{key}")
    @Operation(summary = "매칭 가중치 수정", description = "특정 매칭 가중치 수정")
    public ApiResponse<String> updateMatchingConfig(
            @PathVariable String key,
            @RequestBody Map<String, Object> request) {
        try {
            BigDecimal value = new BigDecimal(request.get("config_value").toString());
            String description = request.containsKey("description") ? 
                (String) request.get("description") : null;
            
            int updated = jdbc.update("""
                UPDATE llm_matching_config
                SET config_value = ?, description = COALESCE(?, description), updated_at = NOW()
                WHERE config_key = ?
                """, value, description, key);
            
            if (updated == 0) {
                // 없으면 생성
                jdbc.update("""
                    INSERT INTO llm_matching_config (config_key, config_value, description)
                    VALUES (?, ?, ?)
                    """, key, value, description);
            }
            
            log.info("[LLM 설정] 매칭 설정 변경: {} = {}", key, value);
            return ApiResponse.success("설정 저장 완료");
        } catch (Exception e) {
            log.error("[LLM 설정] 매칭 설정 수정 실패", e);
            return ApiResponse.error("수정 실패: " + e.getMessage());
        }
    }

    @PostMapping("/matching/batch")
    @Operation(summary = "매칭 가중치 일괄 수정", description = "여러 매칭 가중치를 한번에 수정")
    public ApiResponse<String> updateMatchingConfigBatch(
            @RequestBody Map<String, Object> configs) {
        try {
            int updated = 0;
            for (Map.Entry<String, Object> entry : configs.entrySet()) {
                String key = entry.getKey();
                BigDecimal value = new BigDecimal(entry.getValue().toString());
                
                int count = jdbc.update("""
                    UPDATE llm_matching_config
                    SET config_value = ?, updated_at = NOW()
                    WHERE config_key = ?
                    """, value, key);
                
                if (count == 0) {
                    jdbc.update("""
                        INSERT INTO llm_matching_config (config_key, config_value)
                        VALUES (?, ?)
                        """, key, value);
                }
                updated++;
            }
            
            log.info("[LLM 설정] 매칭 설정 일괄 변경: {}개", updated);
            return ApiResponse.success("일괄 저장 완료: " + updated + "개");
        } catch (Exception e) {
            log.error("[LLM 설정] 매칭 설정 일괄 수정 실패", e);
            return ApiResponse.error("수정 실패: " + e.getMessage());
        }
    }

    @GetMapping("/matching/preset")
    @Operation(summary = "매칭 가중치 프리셋 조회", description = "미리 정의된 가중치 프리셋 조회")
    public ApiResponse<Map<String, Map<String, Object>>> getPresets() {
        try {
            Map<String, Map<String, Object>> presets = new HashMap<>();
            
            // 프리셋 1: 가격 중심
            presets.put("price_focused", Map.of(
                "similarity.price_weight", 0.5,
                "similarity.maker_weight", 0.2,
                "similarity.model_weight", 0.2,
                "similarity.body_type_weight", 0.05,
                "similarity.fuel_weight", 0.025,
                "similarity.transmission_weight", 0.025
            ));
            
            // 프리셋 2: 차량명 중심
            presets.put("name_focused", Map.of(
                "similarity.price_weight", 0.2,
                "similarity.maker_weight", 0.3,
                "similarity.model_weight", 0.3,
                "similarity.body_type_weight", 0.1,
                "similarity.fuel_weight", 0.05,
                "similarity.transmission_weight", 0.05
            ));
            
            // 프리셋 3: 균형
            presets.put("balanced", Map.of(
                "similarity.price_weight", 0.3,
                "similarity.maker_weight", 0.25,
                "similarity.model_weight", 0.25,
                "similarity.body_type_weight", 0.1,
                "similarity.fuel_weight", 0.05,
                "similarity.transmission_weight", 0.05
            ));
            
            return ApiResponse.success(presets);
        } catch (Exception e) {
            log.error("[LLM 설정] 프리셋 조회 실패", e);
            return ApiResponse.error("조회 실패: " + e.getMessage());
        }
    }

    @PostMapping("/matching/preset/{presetName}")
    @Operation(summary = "프리셋 적용", description = "미리 정의된 가중치 프리셋 적용")
    public ApiResponse<String> applyPreset(@PathVariable String presetName) {
        try {
            Map<String, Map<String, Object>> presets = getPresets().getData();
            if (presets == null) {
                return ApiResponse.error("프리셋 조회 실패");
            }
            
            Map<String, Object> preset = presets.get(presetName);
            if (preset == null) {
                return ApiResponse.error("프리셋을 찾을 수 없습니다: " + presetName);
            }
            
            return updateMatchingConfigBatch(preset);
        } catch (Exception e) {
            log.error("[LLM 설정] 프리셋 적용 실패", e);
            return ApiResponse.error("적용 실패: " + e.getMessage());
        }
    }
}


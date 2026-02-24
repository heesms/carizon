package com.carizon.admin;

import com.carizon.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 카리즌 코드집 관리 컨트롤러
 * cz_maker, cz_model_group, cz_model, cz_trim, cz_grade 조회 및 매핑 정보 확인
 */
@Slf4j
@RestController
@RequestMapping("/admin/carizon-codes")
@RequiredArgsConstructor
@Tag(name = "카리즌 코드집", description = "카리즌 표준 코드 조회 및 플랫폼 매핑 정보 확인")
public class CarizonCodeAdminController {

    private final JdbcTemplate jdbc;

    @GetMapping("/makers")
    @Operation(summary = "제조사 목록 조회", description = "모든 제조사 코드 목록을 조회합니다.")
    public ApiResponse<List<Map<String, Object>>> getMakers() {
        try {
            List<Map<String, Object>> makers = jdbc.queryForList("""
                SELECT maker_code, maker_name, country_code, maker_order
                FROM cz_maker
                ORDER BY maker_order, maker_name
            """);
            return ApiResponse.success(makers);
        } catch (Exception e) {
            log.error("[carizon code] maker fetch failed", e);
            return ApiResponse.error("조회 실패: " + e.getMessage());
        }
    }

    @GetMapping("/model-groups")
    @Operation(summary = "모델 그룹 목록 조회", description = "특정 제조사의 모델 그룹 목록을 조회합니다.")
    public ApiResponse<List<Map<String, Object>>> getModelGroups(
            @RequestParam String makerCode) {
        try {
            List<Map<String, Object>> groups = jdbc.queryForList("""
                SELECT maker_code, model_group_code, model_group_name, class_order, use_code, use_name
                FROM cz_model_group
                WHERE maker_code = ?
                ORDER BY class_order, model_group_name
            """, makerCode);
            return ApiResponse.success(groups);
        } catch (Exception e) {
            log.error("[carizon code] model group fetch failed", e);
            return ApiResponse.error("조회 실패: " + e.getMessage());
        }
    }

    @GetMapping("/models")
    @Operation(summary = "모델 목록 조회", description = "특정 제조사/모델 그룹의 모델 목록을 조회합니다.")
    public ApiResponse<List<Map<String, Object>>> getModels(
            @RequestParam String makerCode,
            @RequestParam String modelGroupCode) {
        try {
            List<Map<String, Object>> models = jdbc.queryForList("""
                SELECT maker_code, model_group_code, model_code, model_name, car_order, from_year, to_year
                FROM cz_model
                WHERE maker_code = ? AND model_group_code = ?
                ORDER BY car_order, model_name
            """, makerCode, modelGroupCode);
            return ApiResponse.success(models);
        } catch (Exception e) {
            log.error("[carizon code] model fetch failed", e);
            return ApiResponse.error("조회 실패: " + e.getMessage());
        }
    }

    @GetMapping("/trims")
    @Operation(summary = "트림 목록 조회", description = "특정 모델의 트림 목록을 조회합니다.")
    public ApiResponse<List<Map<String, Object>>> getTrims(
            @RequestParam String makerCode,
            @RequestParam String modelGroupCode,
            @RequestParam String modelCode) {
        try {
            List<Map<String, Object>> trims = jdbc.queryForList("""
                SELECT maker_code, model_group_code, model_code, trim_code, trim_name, model_order
                FROM cz_trim
                WHERE maker_code = ? AND model_group_code = ? AND model_code = ?
                ORDER BY model_order, trim_name
            """, makerCode, modelGroupCode, modelCode);
            return ApiResponse.success(trims);
        } catch (Exception e) {
            log.error("[carizon code] trim fetch failed", e);
            return ApiResponse.error("조회 실패: " + e.getMessage());
        }
    }

    @GetMapping("/grades")
    @Operation(summary = "등급 목록 조회", description = "특정 트림의 등급 목록을 조회합니다.")
    public ApiResponse<List<Map<String, Object>>> getGrades(
            @RequestParam String makerCode,
            @RequestParam String modelGroupCode,
            @RequestParam String modelCode,
            @RequestParam String trimCode) {
        try {
            List<Map<String, Object>> grades = jdbc.queryForList("""
                SELECT maker_code, model_group_code, model_code, trim_code, grade_code, grade_name, grade_order, model_grade_code
                FROM cz_grade
                WHERE maker_code = ? AND model_group_code = ? AND model_code = ? AND trim_code = ?
                ORDER BY grade_order, grade_name
            """, makerCode, modelGroupCode, modelCode, trimCode);
            return ApiResponse.success(grades);
        } catch (Exception e) {
            log.error("[carizon code] grade fetch failed", e);
            return ApiResponse.error("조회 실패: " + e.getMessage());
        }
    }

    @GetMapping("/mappings")
    @Operation(summary = "코드 매핑 정보 조회", 
               description = "특정 카리즌 코드에 매핑된 플랫폼 코드 목록을 조회합니다.")
    public ApiResponse<List<Map<String, Object>>> getMappings(
            @RequestParam(required = false) String makerCode,
            @RequestParam(required = false) String modelGroupCode,
            @RequestParam(required = false) String modelCode,
            @RequestParam(required = false) String trimCode,
            @RequestParam(required = false) String gradeCode) {
        try {
            StringBuilder sql = new StringBuilder("""
                SELECT 
                    cm.platform_name,
                    cm.p_maker_code, cm.p_model_group_code, cm.p_model_code, cm.p_trim_code, cm.p_grade_code,
                    cm.p_maker_name_norm, cm.p_model_group_name_norm, cm.p_model_name_norm,
                    cm.p_trim_name_norm, cm.p_grade_name_norm,
                    cm.confidence_score, cm.match_reason, cm.status,
                    cm.first_seen, cm.last_seen,
                    fm.depth as forced_depth
                FROM cz_code_map cm
                LEFT JOIN cz_forced_map fm
                  ON fm.platform_name = cm.platform_name
                 AND COALESCE(fm.p_maker_code, '') = COALESCE(cm.p_maker_code, '')
                 AND COALESCE(fm.p_model_group_code, '') = COALESCE(cm.p_model_group_code, '')
                 AND COALESCE(fm.p_model_code, '') = COALESCE(cm.p_model_code, '')
                 AND COALESCE(fm.p_trim_code, '') = COALESCE(cm.p_trim_code, '')
                 AND COALESCE(fm.p_grade_code, '') = COALESCE(cm.p_grade_code, '')
                WHERE 1=1
            """);

            List<Object> params = new java.util.ArrayList<>();
            
            if (makerCode != null) {
                sql.append(" AND cm.maker_code = ?");
                params.add(makerCode);
            }
            if (modelGroupCode != null) {
                sql.append(" AND cm.model_group_code = ?");
                params.add(modelGroupCode);
            }
            if (modelCode != null) {
                sql.append(" AND cm.model_code = ?");
                params.add(modelCode);
            }
            if (trimCode != null) {
                sql.append(" AND cm.trim_code = ?");
                params.add(trimCode);
            }
            if (gradeCode != null) {
                sql.append(" AND cm.grade_code = ?");
                params.add(gradeCode);
            }

            sql.append(" ORDER BY cm.platform_name, cm.confidence_score DESC");

            List<Map<String, Object>> mappings = jdbc.queryForList(sql.toString(), params.toArray());
            return ApiResponse.success(mappings);
        } catch (Exception e) {
            log.error("[carizon code] mapping fetch failed", e);
            return ApiResponse.error("조회 실패: " + e.getMessage());
        }
    }
}

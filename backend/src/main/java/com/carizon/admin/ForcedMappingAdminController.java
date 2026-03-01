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
 * 강제 매핑 관리 컨트롤러
 * cz_forced_map 테이블의 CRUD 관리
 */
@Slf4j
@RestController
@RequestMapping("/admin/forced-mapping")
@RequiredArgsConstructor
@Tag(name = "강제 매핑 관리", description = "플랫폼 코드 → 카리즌 코드 강제 매핑 관리")
public class ForcedMappingAdminController {

    private final JdbcTemplate jdbc;

    @GetMapping
    @Operation(summary = "강제 매핑 목록 조회", description = "모든 강제 매핑 목록을 조회합니다.")
    public ApiResponse<List<Map<String, Object>>> getForcedMappings(
            @RequestParam(required = false) String platformName) {
        try {
            StringBuilder sql = new StringBuilder("""
                SELECT 
                    platform_name,
                    depth,
                    p_maker_code, p_model_group_code, p_model_code, p_trim_code, p_grade_code,
                    maker_code, model_group_code, model_code, trim_code, grade_code,
                    created_at, updated_at
                FROM cz_forced_map
                WHERE 1=1
            """);

            List<Object> params = new java.util.ArrayList<>();
            if (platformName != null && !platformName.isEmpty()) {
                sql.append(" AND platform_name = ?");
                params.add(platformName.toUpperCase());
            }

            sql.append(" ORDER BY platform_name, depth, p_maker_code, p_model_code");

            List<Map<String, Object>> mappings = jdbc.queryForList(sql.toString(), params.toArray());
            return ApiResponse.success(mappings);
        } catch (Exception e) {
            log.error("[forced mapping] fetch failed", e);
            return ApiResponse.error("조회 실패: " + e.getMessage());
        }
    }

    @PostMapping
    @Operation(summary = "강제 매핑 추가", description = "새로운 강제 매핑을 추가합니다.")
    public ApiResponse<String> createForcedMapping(@RequestBody Map<String, Object> request) {
        try {
            String platformName = (String) request.get("platform_name");
            String pMakerCode = (String) request.get("p_maker_code");
            String pModelGroupCode = (String) request.get("p_model_group_code");
            String pModelCode = (String) request.get("p_model_code");
            String pTrimCode = (String) request.get("p_trim_code");
            String pGradeCode = (String) request.get("p_grade_code");
            
            String makerCode = (String) request.get("maker_code");
            String modelGroupCode = (String) request.get("model_group_code");
            String modelCode = (String) request.get("model_code");
            String trimCode = (String) request.get("trim_code");
            String gradeCode = (String) request.get("grade_code");

            if (platformName == null || makerCode == null || modelCode == null) {
                return ApiResponse.error("platform_name, maker_code, model_code는 필수입니다.");
            }

            // depth 계산 (1=maker만, 2=maker+group, 3=maker+group+model, 4=+trim, 5=+grade)
            int depth = 1;
            if (pModelGroupCode != null) depth = 2;
            if (pModelCode != null && modelCode != null) depth = 3;
            if (pTrimCode != null && trimCode != null) depth = 4;
            if (pGradeCode != null && gradeCode != null) depth = 5;

            jdbc.update("""
                INSERT INTO cz_forced_map
                  (platform_name, depth,
                   p_maker_code, p_model_group_code, p_model_code, p_trim_code, p_grade_code,
                   maker_code, model_group_code, model_code, trim_code, grade_code,
                   created_at, updated_at)
                VALUES (?, ?,
                        ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?,
                        NOW(), NOW())
                ON DUPLICATE KEY UPDATE
                  maker_code = VALUES(maker_code),
                  model_group_code = VALUES(model_group_code),
                  model_code = VALUES(model_code),
                  trim_code = VALUES(trim_code),
                  grade_code = VALUES(grade_code),
                  depth = VALUES(depth),
                  updated_at = NOW()
            """, platformName.toUpperCase(), depth,
                pMakerCode, pModelGroupCode, pModelCode, pTrimCode, pGradeCode,
                makerCode, modelGroupCode, modelCode, trimCode, gradeCode);

            log.info("[forced mapping] add done: platform={}, depth={}", platformName, depth);
            return ApiResponse.success("강제 매핑이 추가되었습니다.");
        } catch (Exception e) {
            log.error("[forced mapping] add failed", e);
            return ApiResponse.error("추가 실패: " + e.getMessage());
        }
    }

    @PutMapping("/{platformName}")
    @Operation(summary = "강제 매핑 수정", description = "기존 강제 매핑을 수정합니다.")
    public ApiResponse<String> updateForcedMapping(
            @PathVariable String platformName,
            @RequestBody Map<String, Object> request) {
        try {
            String pMakerCode = (String) request.get("p_maker_code");
            String pModelGroupCode = (String) request.get("p_model_group_code");
            String pModelCode = (String) request.get("p_model_code");
            String pTrimCode = (String) request.get("p_trim_code");
            String pGradeCode = (String) request.get("p_grade_code");
            
            String makerCode = (String) request.get("maker_code");
            String modelGroupCode = (String) request.get("model_group_code");
            String modelCode = (String) request.get("model_code");
            String trimCode = (String) request.get("trim_code");
            String gradeCode = (String) request.get("grade_code");

            int depth = 1;
            if (pModelGroupCode != null) depth = 2;
            if (pModelCode != null && modelCode != null) depth = 3;
            if (pTrimCode != null && trimCode != null) depth = 4;
            if (pGradeCode != null && gradeCode != null) depth = 5;

            int updated = jdbc.update("""
                UPDATE cz_forced_map
                SET maker_code = ?,
                    model_group_code = ?,
                    model_code = ?,
                    trim_code = ?,
                    grade_code = ?,
                    depth = ?,
                    updated_at = NOW()
                WHERE platform_name = ?
                  AND COALESCE(p_maker_code, '') = COALESCE(?, '')
                  AND COALESCE(p_model_group_code, '') = COALESCE(?, '')
                  AND COALESCE(p_model_code, '') = COALESCE(?, '')
                  AND COALESCE(p_trim_code, '') = COALESCE(?, '')
                  AND COALESCE(p_grade_code, '') = COALESCE(?, '')
            """, makerCode, modelGroupCode, modelCode, trimCode, gradeCode, depth,
                platformName.toUpperCase(), pMakerCode, pModelGroupCode, pModelCode, pTrimCode, pGradeCode);

            if (updated > 0) {
                return ApiResponse.success("강제 매핑이 수정되었습니다.");
            } else {
                return ApiResponse.error("매핑을 찾을 수 없습니다.");
            }
        } catch (Exception e) {
            log.error("[forced mapping] update failed", e);
            return ApiResponse.error("수정 실패: " + e.getMessage());
        }
    }

    @DeleteMapping("/{platformName}")
    @Operation(summary = "강제 매핑 삭제", description = "강제 매핑을 삭제합니다.")
    public ApiResponse<String> deleteForcedMapping(
            @PathVariable String platformName,
            @RequestParam(required = false) String pMakerCode,
            @RequestParam(required = false) String pModelGroupCode,
            @RequestParam(required = false) String pModelCode,
            @RequestParam(required = false) String pTrimCode,
            @RequestParam(required = false) String pGradeCode) {
        try {
            StringBuilder sql = new StringBuilder("""
                DELETE FROM cz_forced_map
                WHERE platform_name = ?
            """);

            List<Object> params = new java.util.ArrayList<>();
            params.add(platformName.toUpperCase());

            if (pMakerCode != null) {
                sql.append(" AND COALESCE(p_maker_code, '') = COALESCE(?, '')");
                params.add(pMakerCode);
            }
            if (pModelGroupCode != null) {
                sql.append(" AND COALESCE(p_model_group_code, '') = COALESCE(?, '')");
                params.add(pModelGroupCode);
            }
            if (pModelCode != null) {
                sql.append(" AND COALESCE(p_model_code, '') = COALESCE(?, '')");
                params.add(pModelCode);
            }
            if (pTrimCode != null) {
                sql.append(" AND COALESCE(p_trim_code, '') = COALESCE(?, '')");
                params.add(pTrimCode);
            }
            if (pGradeCode != null) {
                sql.append(" AND COALESCE(p_grade_code, '') = COALESCE(?, '')");
                params.add(pGradeCode);
            }

            int deleted = jdbc.update(sql.toString(), params.toArray());
            
            if (deleted > 0) {
                return ApiResponse.success("강제 매핑이 삭제되었습니다.");
            } else {
                return ApiResponse.error("매핑을 찾을 수 없습니다.");
            }
        } catch (Exception e) {
            log.error("[forced mapping] delete failed", e);
            return ApiResponse.error("삭제 실패: " + e.getMessage());
        }
    }
}

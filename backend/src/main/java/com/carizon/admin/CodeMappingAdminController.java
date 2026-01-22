package com.carizon.admin;

import com.carizon.common.dto.ApiResponse;
import com.carizon.mapping.CodeMappingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 코드 매핑 관리자 컨트롤러
 * cz_code_map의 REVIEW 상태 매핑을 수동으로 검토 및 수정
 */
@Slf4j
@RestController
@RequestMapping("/admin/code-mapping")
@RequiredArgsConstructor
@Tag(name = "코드 매핑 관리", description = "코드 매핑 리뷰 및 수동 매핑 관리")
public class CodeMappingAdminController {

    private final JdbcTemplate jdbc;
    private final CodeMappingService codeMappingService;

    @GetMapping("/review")
    @Operation(summary = "REVIEW 상태 코드 매핑 조회", 
               description = "수동 검토가 필요한 코드 매핑 목록을 조회합니다.")
    public ApiResponse<Map<String, Object>> getReviewMappings(
            @RequestParam(required = false) String platformName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            StringBuilder sql = new StringBuilder("""
                SELECT 
                    platform_name,
                    p_maker_code, p_model_group_code, p_model_code, p_trim_code, p_grade_code,
                    p_maker_name_norm, p_model_group_name_norm, p_model_name_norm, 
                    p_trim_name_norm, p_grade_name_norm,
                    maker_code, model_group_code, model_code, trim_code, grade_code,
                    confidence_score, match_reason, status,
                    first_seen, last_seen
                FROM cz_code_map
                WHERE status = 'REVIEW'
            """);

            List<Object> params = new ArrayList<>();
            if (platformName != null && !platformName.isEmpty()) {
                sql.append(" AND platform_name = ?");
                params.add(platformName.toUpperCase());
            }

            sql.append(" ORDER BY confidence_score DESC, last_seen DESC");
            sql.append(" LIMIT ? OFFSET ?");
            params.add(size);
            params.add(page * size);

            List<Map<String, Object>> mappings = jdbc.queryForList(sql.toString(), params.toArray());
            
            // 총 개수 조회
            StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM cz_code_map WHERE status = 'REVIEW'");
            List<Object> countParams = new ArrayList<>();
            if (platformName != null && !platformName.isEmpty()) {
                countSql.append(" AND platform_name = ?");
                countParams.add(platformName.toUpperCase());
            }
            Long total = jdbc.queryForObject(countSql.toString(), Long.class, countParams.toArray());

            return ApiResponse.success(Map.of(
                    "items", mappings,
                    "total", total,
                    "page", page,
                    "size", size
            ));
        } catch (Exception e) {
            log.error("[코드 매핑] REVIEW 조회 실패", e);
            return ApiResponse.error("조회 실패: " + e.getMessage());
        }
    }

    @GetMapping("/stats")
    @Operation(summary = "코드 매핑 통계", 
               description = "코드 매핑 상태별 통계를 조회합니다.")
    public ApiResponse<Map<String, Object>> getMappingStats() {
        try {
            // 상태별 통계
            List<Map<String, Object>> statusStats = jdbc.queryForList("""
                SELECT status, COUNT(*) as count
                FROM cz_code_map
                GROUP BY status
            """);

            // 플랫폼별 REVIEW 개수
            List<Map<String, Object>> platformReview = jdbc.queryForList("""
                SELECT platform_name, COUNT(*) as count
                FROM cz_code_map
                WHERE status = 'REVIEW'
                GROUP BY platform_name
                ORDER BY count DESC
            """);

            // 신뢰도 구간별 통계
            List<Map<String, Object>> scoreStats = jdbc.queryForList("""
                SELECT 
                    CASE 
                        WHEN confidence_score >= 0.93 THEN 'AUTO (93%+)'
                        WHEN confidence_score >= 0.85 THEN 'HIGH (85-93%)'
                        WHEN confidence_score >= 0.70 THEN 'MEDIUM (70-85%)'
                        ELSE 'LOW (<70%)'
                    END as score_range,
                    COUNT(*) as count
                FROM cz_code_map
                WHERE status = 'REVIEW'
                GROUP BY score_range
                ORDER BY MIN(confidence_score) DESC
            """);

            return ApiResponse.success(Map.of(
                    "statusStats", statusStats,
                    "platformReview", platformReview,
                    "scoreStats", scoreStats
            ));
        } catch (Exception e) {
            log.error("[코드 매핑] 통계 조회 실패", e);
            return ApiResponse.error("조회 실패: " + e.getMessage());
        }
    }

    @PutMapping("/review/{platformName}/update")
    @Operation(summary = "코드 매핑 수동 수정", 
               description = "REVIEW 상태의 코드 매핑을 수동으로 수정합니다.")
    public ApiResponse<String> updateMapping(
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
            String status = (String) request.get("status"); // AUTO, LOCKED

            if (status == null || (!status.equals("AUTO") && !status.equals("LOCKED"))) {
                return ApiResponse.error("status는 AUTO 또는 LOCKED여야 합니다.");
            }

            // p_key는 GENERATED 컬럼이므로 WHERE 조건에서 모든 p_* 코드로 직접 매칭
            int updated = jdbc.update("""
                UPDATE cz_code_map
                SET maker_code = ?,
                    model_group_code = ?,
                    model_code = ?,
                    trim_code = ?,
                    grade_code = ?,
                    confidence_score = 1.0,
                    match_reason = 'MANUAL',
                    status = ?,
                    last_seen = CURRENT_DATE
                WHERE platform_name = ?
                  AND COALESCE(p_maker_code, '') = COALESCE(?, '')
                  AND COALESCE(p_model_group_code, '') = COALESCE(?, '')
                  AND COALESCE(p_model_code, '') = COALESCE(?, '')
                  AND COALESCE(p_trim_code, '') = COALESCE(?, '')
                  AND COALESCE(p_grade_code, '') = COALESCE(?, '')
            """, makerCode, modelGroupCode, modelCode, trimCode, gradeCode, status, 
                platformName.toUpperCase(), pMakerCode, pModelGroupCode, pModelCode, pTrimCode, pGradeCode);

            if (updated > 0) {
                log.info("[코드 매핑] 수동 수정 완료: platform={}, status={}", 
                        platformName, status);
                return ApiResponse.success("코드 매핑이 수정되었습니다.");
            } else {
                return ApiResponse.error("매핑을 찾을 수 없습니다.");
            }
        } catch (Exception e) {
            log.error("[코드 매핑] 수정 실패", e);
            return ApiResponse.error("수정 실패: " + e.getMessage());
        }
    }

    @PostMapping("/auto-mapping/{platformName}")
    @Operation(summary = "코드 매핑 자동 실행", 
               description = "특정 플랫폼의 코드 매핑을 자동으로 실행합니다.")
    public ApiResponse<Map<String, Object>> runAutoMapping(
            @PathVariable String platformName,
            @RequestParam(defaultValue = "TODAY") String scope) {
        try {
            CodeMappingService.Scope mappingScope = "FULL".equalsIgnoreCase(scope) 
                    ? CodeMappingService.Scope.FULL 
                    : CodeMappingService.Scope.TODAY;
            
            int mapped = codeMappingService.runAutoMapping(platformName, mappingScope);
            
            return ApiResponse.success(Map.of(
                    "platform", platformName,
                    "scope", mappingScope.toString(),
                    "mappedCount", mapped
            ));
        } catch (Exception e) {
            log.error("[코드 매핑] 자동 매핑 실패", e);
            return ApiResponse.error("자동 매핑 실패: " + e.getMessage());
        }
    }

}

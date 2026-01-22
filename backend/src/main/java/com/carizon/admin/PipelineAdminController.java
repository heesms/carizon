package com.carizon.admin;

import com.carizon.batch.BatchWorkflowService;
import com.carizon.common.dto.ApiResponse;
import com.carizon.mapping.CodeMappingService;
import com.carizon.mapping.MasterMergeService;
import com.carizon.merge.MergeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 통합 파이프라인 관리 컨트롤러
 * 크롤링 → 머지 → 코드 매핑 → car_master까지 한 번에 실행
 */
@Slf4j
@RestController
@RequestMapping("/admin/pipeline")
@RequiredArgsConstructor
@Tag(name = "파이프라인 관리", description = "전체 데이터 파이프라인 실행")
public class PipelineAdminController {

    private final MergeService mergeService;
    private final CodeMappingService codeMappingService;
    private final MasterMergeService masterMergeService;
    private final BatchWorkflowService workflowService;

    @PostMapping("/full")
    @Operation(summary = "전체 파이프라인 실행", 
               description = "크롤링 → 머지 → 코드 매핑 → car_master까지 전체 파이프라인을 실행합니다.")
    public ApiResponse<Map<String, Object>> runFullPipeline(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate,
            @RequestParam(defaultValue = "false") boolean skipCrawl) {
        try {
            LocalDate date = bizDate != null ? bizDate : LocalDate.now();
            Map<String, Object> result = new HashMap<>();
            
            // 1. 크롤링 (선택적)
            if (!skipCrawl) {
                log.info("[파이프라인] 크롤링 시작...");
                // 크롤링은 별도 스케줄러에서 실행되므로 여기서는 스킵
                // 필요시 CrawlJobService.runDaily() 호출 가능
                result.put("crawl", "skipped (별도 실행 필요)");
            } else {
                result.put("crawl", "skipped");
            }

            // 2. 머지 (raw_* → platform_car)
            log.info("[파이프라인] 머지 시작...");
            long mergeStart = System.currentTimeMillis();
            int merged = mergeService.mergeAllPlatforms(date);
            long mergeTime = System.currentTimeMillis() - mergeStart;
            result.put("merge", Map.of(
                    "mergedCount", merged,
                    "durationMs", mergeTime
            ));

            // 3. 코드 매핑 (platform_car → cz_code_map)
            log.info("[파이프라인] 코드 매핑 시작...");
            long mappingStart = System.currentTimeMillis();
            int totalMapped = 0;
            String[] platforms = {"ENCAR", "KCAR", "CHACHACHA", "CHUTCHA", "CHARANCHA", "TCAR"};
            for (String platform : platforms) {
                try {
                    int mapped = codeMappingService.runAutoMapping(platform, CodeMappingService.Scope.TODAY);
                    totalMapped += mapped;
                    log.info("[파이프라인] {} 매핑: {}건", platform, mapped);
                } catch (Exception e) {
                    log.error("[파이프라인] {} 매핑 실패", platform, e);
                }
            }
            long mappingTime = System.currentTimeMillis() - mappingStart;
            result.put("codeMapping", Map.of(
                    "mappedCount", totalMapped,
                    "durationMs", mappingTime
            ));

            // 4. car_master 머지 (platform_car + cz_code_map → car_master)
            log.info("[파이프라인] car_master 머지 시작...");
            long masterStart = System.currentTimeMillis();
            int masterMerged = masterMergeService.upsertAliveToCarMaster(date);
            masterMergeService.updateCarMasterFromMapping();
            long masterTime = System.currentTimeMillis() - masterStart;
            result.put("masterMerge", Map.of(
                    "mergedCount", masterMerged,
                    "durationMs", masterTime
            ));

            long totalTime = System.currentTimeMillis() - mergeStart;
            result.put("totalDurationMs", totalTime);
            result.put("bizDate", date.toString());

            log.info("[파이프라인] 전체 파이프라인 완료: {}ms", totalTime);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[파이프라인] 실행 실패", e);
            return ApiResponse.error("파이프라인 실행 실패: " + e.getMessage());
        }
    }

    @PostMapping("/workflow")
    @Operation(summary = "워크플로우 실행", 
               description = "daily_pipeline 워크플로우를 실행합니다 (크롤링 포함).")
    public ApiResponse<String> runWorkflow(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        try {
            Map<String, Object> config = new HashMap<>();
            if (bizDate != null) {
                config.put("bizDate", bizDate.toString());
            }
            
            Long executionId = workflowService.executeWorkflow("daily_pipeline", config);
            return ApiResponse.success("워크플로우 실행 시작됨 (executionId: " + executionId + ")");
        } catch (Exception e) {
            log.error("[파이프라인] 워크플로우 실행 실패", e);
            return ApiResponse.error("워크플로우 실행 실패: " + e.getMessage());
        }
    }

    @PostMapping("/merge-only")
    @Operation(summary = "머지만 실행", 
               description = "머지 단계만 실행합니다 (raw_* → platform_car).")
    public ApiResponse<Map<String, Object>> runMergeOnly(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        try {
            LocalDate date = bizDate != null ? bizDate : LocalDate.now();
            long start = System.currentTimeMillis();
            int merged = mergeService.mergeAllPlatforms(date);
            long duration = System.currentTimeMillis() - start;
            
            return ApiResponse.success(Map.of(
                    "mergedCount", merged,
                    "durationMs", duration,
                    "bizDate", date.toString()
            ));
        } catch (Exception e) {
            log.error("[파이프라인] 머지 실행 실패", e);
            return ApiResponse.error("머지 실행 실패: " + e.getMessage());
        }
    }

    @PostMapping("/code-mapping-only")
    @Operation(summary = "코드 매핑만 실행", 
               description = "코드 매핑 단계만 실행합니다 (platform_car → cz_code_map).")
    public ApiResponse<Map<String, Object>> runCodeMappingOnly(
            @RequestParam(defaultValue = "TODAY") String scope) {
        try {
            CodeMappingService.Scope mappingScope = "FULL".equalsIgnoreCase(scope) 
                    ? CodeMappingService.Scope.FULL 
                    : CodeMappingService.Scope.TODAY;
            
            long start = System.currentTimeMillis();
            int totalMapped = 0;
            String[] platforms = {"ENCAR", "KCAR", "CHACHACHA", "CHUTCHA", "CHARANCHA", "TCAR"};
            for (String platform : platforms) {
                try {
                    int mapped = codeMappingService.runAutoMapping(platform, mappingScope);
                    totalMapped += mapped;
                } catch (Exception e) {
                    log.error("[파이프라인] {} 매핑 실패", platform, e);
                }
            }
            long duration = System.currentTimeMillis() - start;
            
            return ApiResponse.success(Map.of(
                    "mappedCount", totalMapped,
                    "durationMs", duration,
                    "scope", mappingScope.toString()
            ));
        } catch (Exception e) {
            log.error("[파이프라인] 코드 매핑 실행 실패", e);
            return ApiResponse.error("코드 매핑 실행 실패: " + e.getMessage());
        }
    }

    @PostMapping("/master-merge-only")
    @Operation(summary = "car_master 머지만 실행", 
               description = "car_master 머지 단계만 실행합니다 (platform_car + cz_code_map → car_master).")
    public ApiResponse<Map<String, Object>> runMasterMergeOnly(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        try {
            LocalDate date = bizDate != null ? bizDate : LocalDate.now();
            long start = System.currentTimeMillis();
            int merged = masterMergeService.upsertAliveToCarMaster(date);
            masterMergeService.updateCarMasterFromMapping();
            long duration = System.currentTimeMillis() - start;
            
            return ApiResponse.success(Map.of(
                    "mergedCount", merged,
                    "durationMs", duration,
                    "bizDate", date.toString()
            ));
        } catch (Exception e) {
            log.error("[파이프라인] car_master 머지 실행 실패", e);
            return ApiResponse.error("car_master 머지 실행 실패: " + e.getMessage());
        }
    }
}

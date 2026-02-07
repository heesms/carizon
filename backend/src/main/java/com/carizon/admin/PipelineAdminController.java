package com.carizon.admin;

import com.carizon.batch.ApiRunRecorder;
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
    private final ApiRunRecorder apiRunRecorder;

    @PostMapping("/full")
    @Operation(summary = "전체 파이프라인 실행", 
               description = "크롤링 → 머지 → 코드 매핑 → car_master까지 전체 파이프라인을 실행합니다.")
    public ApiResponse<Map<String, Object>> runFullPipeline(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate,
            @RequestParam(defaultValue = "false") boolean skipCrawl) {
        String runId = apiRunRecorder.recordStart("/admin/pipeline/full", "POST", "pipeline-full");
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
            
            @SuppressWarnings("unchecked")
            Map<String, Object> masterMerge = (Map<String, Object>) result.getOrDefault("masterMerge", Map.of());
            int totalItems = (Integer) masterMerge.getOrDefault("mergedCount", 0);
            apiRunRecorder.recordSuccess(runId, totalItems, result);
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[파이프라인] 실행 실패", e);
            apiRunRecorder.recordFail(runId, 0, e);
            return ApiResponse.error("파이프라인 실행 실패: " + e.getMessage());
        }
    }

    @PostMapping("/workflow")
    @Operation(summary = "워크플로우 실행", 
               description = "daily_pipeline 워크플로우를 실행합니다 (크롤링 포함).")
    public ApiResponse<String> runWorkflow(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        String runId = apiRunRecorder.recordStart("/admin/pipeline/workflow", "POST", "pipeline-workflow");
        try {
            Map<String, Object> config = new HashMap<>();
            if (bizDate != null) {
                config.put("bizDate", bizDate.toString());
            }
            
            Long executionId = workflowService.executeWorkflow("daily_pipeline", config);
            String message = "워크플로우 실행 시작됨 (executionId: " + executionId + ")";
            
            apiRunRecorder.recordSuccess(runId, 0, Map.of("executionId", executionId, "message", message));
            return ApiResponse.success(message);
        } catch (Exception e) {
            log.error("[파이프라인] 워크플로우 실행 실패", e);
            apiRunRecorder.recordFail(runId, 0, e);
            return ApiResponse.error("워크플로우 실행 실패: " + e.getMessage());
        }
    }

    @PostMapping("/merge-only")
    @Operation(summary = "머지만 실행", 
               description = "머지 단계만 실행합니다 (raw_* → platform_car).")
    public ApiResponse<Map<String, Object>> runMergeOnly(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        String runId = apiRunRecorder.recordStart("/admin/pipeline/merge-only", "POST", "merge-only");
        try {
            LocalDate date = bizDate != null ? bizDate : LocalDate.now();
            long start = System.currentTimeMillis();
            int merged = mergeService.mergeAllPlatforms(date);
            long duration = System.currentTimeMillis() - start;
            
            Map<String, Object> result = Map.of(
                    "mergedCount", merged,
                    "durationMs", duration,
                    "bizDate", date.toString()
            );
            
            apiRunRecorder.recordSuccess(runId, merged, result);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[파이프라인] 머지 실행 실패", e);
            apiRunRecorder.recordFail(runId, 0, e);
            return ApiResponse.error("머지 실행 실패: " + e.getMessage());
        }
    }

    @PostMapping("/code-mapping-only")
    @Operation(summary = "코드 매핑만 실행", 
               description = "코드 매핑 단계만 실행합니다 (platform_car → cz_code_map).")
    public ApiResponse<Map<String, Object>> runCodeMappingOnly(
            @RequestParam(defaultValue = "TODAY") String scope) {
        String runId = apiRunRecorder.recordStart("/admin/pipeline/code-mapping-only", "POST", "code-mapping-only");
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
            
            Map<String, Object> result = Map.of(
                    "mappedCount", totalMapped,
                    "durationMs", duration,
                    "scope", mappingScope.toString()
            );
            
            apiRunRecorder.recordSuccess(runId, totalMapped, result);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[파이프라인] 코드 매핑 실행 실패", e);
            apiRunRecorder.recordFail(runId, 0, e);
            return ApiResponse.error("코드 매핑 실행 실패: " + e.getMessage());
        }
    }

    @PostMapping("/master-merge-only")
    @Operation(summary = "car_master 머지만 실행", 
               description = "car_master 머지 단계만 실행합니다 (platform_car + cz_code_map → car_master).")
    public ApiResponse<Map<String, Object>> runMasterMergeOnly(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        String runId = apiRunRecorder.recordStart("/admin/pipeline/master-merge-only", "POST", "master-merge-only");
        try {
            LocalDate date = bizDate != null ? bizDate : LocalDate.now();
            long start = System.currentTimeMillis();
            int merged = masterMergeService.upsertAliveToCarMaster(date);
            masterMergeService.updateCarMasterFromMapping();
            long duration = System.currentTimeMillis() - start;
            
            Map<String, Object> result = Map.of(
                    "mergedCount", merged,
                    "durationMs", duration,
                    "bizDate", date.toString()
            );
            
            apiRunRecorder.recordSuccess(runId, merged, result);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[파이프라인] car_master 머지 실행 실패", e);
            apiRunRecorder.recordFail(runId, 0, e);
            return ApiResponse.error("car_master 머지 실행 실패: " + e.getMessage());
        }
    }

    @PostMapping("/rebuild-platform-car")
    @Operation(summary = "platform_car TRUNCATE 후 재생성", 
               description = "platform_car를 TRUNCATE하고 raw_*에서 재생성합니다. 순수 INSERT만 사용하여 더 빠릅니다.")
    public ApiResponse<Map<String, Object>> rebuildPlatformCar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        String runId = apiRunRecorder.recordStart("/admin/pipeline/rebuild-platform-car", "POST", "rebuild-platform-car");
        try {
            LocalDate date = bizDate != null ? bizDate : LocalDate.now();
            long start = System.currentTimeMillis();
            
            log.warn("[파이프라인] rebuildPlatformCar: platform_car를 TRUNCATE하고 재생성합니다!");
            
            Map<String, Object> result = mergeService.rebuildFromScratch(date);
            long duration = System.currentTimeMillis() - start;
            
            int platformCarCount = (Integer) result.get("platformCarCount");
            Map<String, Object> response = Map.of(
                    "platformCarCount", platformCarCount,
                    "durationMs", duration,
                    "bizDate", date.toString()
            );
            
            apiRunRecorder.recordSuccess(runId, platformCarCount, response);
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("[파이프라인] platform_car 재생성 실패", e);
            apiRunRecorder.recordFail(runId, 0, e);
            return ApiResponse.error("platform_car 재생성 실패: " + e.getMessage());
        }
    }

    @PostMapping("/rebuild-car-master")
    @Operation(summary = "car_master TRUNCATE 후 재생성", 
               description = "car_master를 TRUNCATE하고 platform_car에서 재생성합니다. 순수 INSERT만 사용하여 더 빠릅니다.")
    public ApiResponse<Map<String, Object>> rebuildCarMaster(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        String runId = apiRunRecorder.recordStart("/admin/pipeline/rebuild-car-master", "POST", "rebuild-car-master");
        try {
            LocalDate date = bizDate != null ? bizDate : LocalDate.now();
            long start = System.currentTimeMillis();
            
            log.warn("[파이프라인] rebuildCarMaster: car_master를 TRUNCATE하고 재생성합니다!");
            
            int carMasterCount = masterMergeService.rebuildCarMasterFromScratch(date);
            masterMergeService.updateCarMasterFromMapping();
            int linkedCount = mergeService.linkToMaster();
            long duration = System.currentTimeMillis() - start;
            
            Map<String, Object> result = Map.of(
                    "carMasterCount", carMasterCount,
                    "linkedCount", linkedCount,
                    "durationMs", duration,
                    "bizDate", date.toString()
            );
            
            apiRunRecorder.recordSuccess(runId, carMasterCount, result);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[파이프라인] car_master 재생성 실패", e);
            apiRunRecorder.recordFail(runId, 0, e);
            return ApiResponse.error("car_master 재생성 실패: " + e.getMessage());
        }
    }

    @PostMapping("/rebuild-from-scratch")
    @Operation(summary = "TRUNCATE 후 재생성 (빠른 통합 머지)", 
               description = "platform_car와 car_master를 TRUNCATE하고 raw_*에서 재생성합니다. 순수 INSERT만 사용하여 더 빠릅니다. 주의: car_price_history도 함께 TRUNCATE됩니다.")
    public ApiResponse<Map<String, Object>> rebuildFromScratch(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        String runId = apiRunRecorder.recordStart("/admin/pipeline/rebuild-from-scratch", "POST", "rebuild-from-scratch");
        try {
            LocalDate date = bizDate != null ? bizDate : LocalDate.now();
            long start = System.currentTimeMillis();
            
            log.warn("[파이프라인] rebuildFromScratch 시작: platform_car, car_master, car_price_history를 TRUNCATE합니다!");
            
            // 1단계: TRUNCATE 및 재생성 (통합 처리)
            log.info("[파이프라인] rebuildFromScratch 시작...");
            long mergeStart = System.currentTimeMillis();
            Map<String, Object> mergeResult = mergeService.rebuildFromScratch(date);
            long mergeTime = System.currentTimeMillis() - mergeStart;
            log.info("[파이프라인] platform_car 재생성 완료: {}건 ({}ms)", mergeResult.get("platformCarCount"), mergeTime);
            
            int platformCarCount = (Integer) mergeResult.get("platformCarCount");
            
            // 3단계: car_master 재생성 (순수 INSERT만)
            log.info("[파이프라인] car_master 재생성 시작...");
            long masterStart = System.currentTimeMillis();
            int carMasterCount = masterMergeService.rebuildCarMasterFromScratch(date);
            masterMergeService.updateCarMasterFromMapping();
            long masterTime = System.currentTimeMillis() - masterStart;
            log.info("[파이프라인] car_master 재생성 완료: {}건 ({}ms)", carMasterCount, masterTime);
            
            // 4단계: platform_car.car_id 매핑
            log.info("[파이프라인] platform_car.car_id 매핑 시작...");
            long linkStart = System.currentTimeMillis();
            int linkedCount = mergeService.linkToMaster();
            long linkTime = System.currentTimeMillis() - linkStart;
            log.info("[파이프라인] platform_car.car_id 매핑 완료: {}건 ({}ms)", linkedCount, linkTime);
            
            long totalTime = System.currentTimeMillis() - start;
            
            Map<String, Object> response = Map.of(
                    "platformCarCount", platformCarCount,
                    "carMasterCount", carMasterCount,
                    "linkedCount", linkedCount,
                    "totalDurationMs", totalTime,
                    "mergeDurationMs", mergeTime,
                    "masterDurationMs", masterTime,
                    "linkDurationMs", linkTime,
                    "bizDate", date.toString()
            );
            
            apiRunRecorder.recordSuccess(runId, platformCarCount + carMasterCount, response);
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("[파이프라인] rebuildFromScratch 실행 실패", e);
            apiRunRecorder.recordFail(runId, 0, e);
            return ApiResponse.error("rebuildFromScratch 실행 실패: " + e.getMessage());
        }
    }
}

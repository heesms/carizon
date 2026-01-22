package com.carizon.admin;

import com.carizon.batch.BatchJobService;
import com.carizon.batch.BatchWorkflowService;
import com.carizon.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 배치 작업 관리 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/admin/batch")
@RequiredArgsConstructor
@Tag(name = "배치 관리", description = "배치 작업 및 워크플로우 관리")
public class BatchAdminController {

    private final BatchJobService batchJobService;
    private final BatchWorkflowService workflowService;

    /**
     * 모든 작업 정의 조회
     */
    @GetMapping("/jobs")
    @Operation(summary = "작업 정의 조회", description = "모든 배치 작업 정의 조회")
    public ApiResponse<List<Map<String, Object>>> getJobDefinitions() {
        try {
            List<Map<String, Object>> jobs = batchJobService.getAllJobDefinitions();
            return ApiResponse.success(jobs);
        } catch (Exception e) {
            log.error("[배치] 작업 정의 조회 실패", e);
            return ApiResponse.error("조회 실패: " + e.getMessage());
        }
    }

    /**
     * 작업 실행
     */
    @PostMapping("/jobs/{jobId}/execute")
    @Operation(summary = "작업 실행", description = "특정 배치 작업 실행")
    public ApiResponse<String> executeJob(
            @PathVariable String jobId,
            @RequestBody(required = false) Map<String, Object> config) {
        try {
            String executionId = batchJobService.executeJob(jobId, config != null ? config : new HashMap<>());
            return ApiResponse.success("작업 실행 시작됨 (executionId: " + executionId + ")");
        } catch (Exception e) {
            log.error("[배치] 작업 실행 실패: {}", jobId, e);
            return ApiResponse.error("작업 실행 실패: " + e.getMessage());
        }
    }

    /**
     * 작업 실행 이력 조회
     */
    @GetMapping("/jobs/{jobId}/executions")
    @Operation(summary = "작업 실행 이력 조회", description = "특정 작업의 실행 이력 조회")
    public ApiResponse<List<Map<String, Object>>> getJobExecutions(
            @PathVariable String jobId,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            List<Map<String, Object>> executions = batchJobService.getJobExecutions(jobId, limit);
            return ApiResponse.success(executions);
        } catch (Exception e) {
            log.error("[배치] 실행 이력 조회 실패", e);
            return ApiResponse.error("조회 실패: " + e.getMessage());
        }
    }

    /**
     * 모든 워크플로우 정의 조회
     */
    @GetMapping("/workflows")
    @Operation(summary = "워크플로우 정의 조회", description = "모든 워크플로우 정의 조회")
    public ApiResponse<List<Map<String, Object>>> getWorkflowDefinitions() {
        try {
            List<Map<String, Object>> workflows = workflowService.getAllWorkflowDefinitions();
            return ApiResponse.success(workflows);
        } catch (Exception e) {
            log.error("[배치] 워크플로우 정의 조회 실패", e);
            return ApiResponse.error("조회 실패: " + e.getMessage());
        }
    }

    /**
     * 워크플로우 실행
     */
    @PostMapping("/workflows/{workflowId}/execute")
    @Operation(summary = "워크플로우 실행", description = "특정 워크플로우 실행")
    public ApiResponse<String> executeWorkflow(
            @PathVariable String workflowId,
            @RequestBody(required = false) Map<String, Object> config) {
        try {
            Long executionId = workflowService.executeWorkflow(workflowId, config != null ? config : new HashMap<>());
            return ApiResponse.success("워크플로우 실행 시작됨 (executionId: " + executionId + ")");
        } catch (Exception e) {
            log.error("[배치] 워크플로우 실행 실패: {}", workflowId, e);
            return ApiResponse.error("워크플로우 실행 실패: " + e.getMessage());
        }
    }

    /**
     * 워크플로우 실행 이력 조회
     */
    @GetMapping("/workflows/{workflowId}/executions")
    @Operation(summary = "워크플로우 실행 이력 조회", description = "특정 워크플로우의 실행 이력 조회")
    public ApiResponse<List<Map<String, Object>>> getWorkflowExecutions(
            @PathVariable String workflowId,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            List<Map<String, Object>> executions = workflowService.getWorkflowExecutions(workflowId, limit);
            return ApiResponse.success(executions);
        } catch (Exception e) {
            log.error("[배치] 워크플로우 실행 이력 조회 실패", e);
            return ApiResponse.error("조회 실패: " + e.getMessage());
        }
    }
}

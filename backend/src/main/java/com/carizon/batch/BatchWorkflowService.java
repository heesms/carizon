package com.carizon.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 워크플로우 관리 서비스
 * 여러 배치 작업을 순차/병렬로 실행
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchWorkflowService {

    private final JdbcTemplate jdbc;
    private final BatchJobService batchJobService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 워크플로우 실행
     */
    @Transactional
    public Long executeWorkflow(String workflowId, Map<String, Object> config) {
        // 워크플로우 정의 조회
        Map<String, Object> workflowDef = jdbc.queryForMap(
            "SELECT * FROM batch_workflow_definition WHERE workflow_id = ?", workflowId);
        
        String workflowName = (String) workflowDef.get("workflow_name");
        String jobSequenceJson = (String) workflowDef.get("job_sequence");
        
        // 실행 이력 생성
        Long executionId = createWorkflowExecution(workflowId, workflowName);
        
        try {
            updateWorkflowExecutionStatus(executionId, "RUNNING");
            
            // 작업 순서 파싱
            List<Map<String, Object>> jobSequence = objectMapper.readValue(
                jobSequenceJson, new TypeReference<List<Map<String, Object>>>() {});
            
            // 순차 실행
            for (Map<String, Object> jobStep : jobSequence) {
                String jobId = (String) jobStep.get("jobId");
                @SuppressWarnings("unchecked")
                List<String> dependsOn = (List<String>) jobStep.get("dependsOn");
                
                // 의존성 확인 (간단한 구현)
                if (dependsOn != null && !dependsOn.isEmpty()) {
                    log.info("[워크플로우] 작업 {} 의존성 대기: {}", jobId, dependsOn);
                }
                
                log.info("[워크플로우] 작업 실행: {}", jobId);
                batchJobService.executeJob(jobId, config);
            }
            
            updateWorkflowExecutionStatus(executionId, "SUCCESS");
            log.info("[워크플로우] 완료: {} (executionId: {})", workflowId, executionId);
            
            return executionId;
        } catch (Exception e) {
            updateWorkflowExecutionFailure(executionId, e.getMessage());
            log.error("[워크플로우] 실패: {} (executionId: {})", workflowId, executionId, e);
            throw new RuntimeException("워크플로우 실행 실패: " + workflowId, e);
        }
    }

    /**
     * 워크플로우 실행 이력 생성
     */
    private Long createWorkflowExecution(String workflowId, String workflowName) {
        jdbc.update("""
            INSERT INTO batch_workflow_execution (workflow_id, workflow_name, status, started_at)
            VALUES (?, ?, 'PENDING', NOW())
            """, workflowId, workflowName);
        
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    /**
     * 워크플로우 실행 상태 업데이트
     */
    private void updateWorkflowExecutionStatus(Long executionId, String status) {
        if ("SUCCESS".equals(status) || "FAILED".equals(status)) {
            jdbc.update("""
                UPDATE batch_workflow_execution
                SET status = ?,
                    ended_at = NOW(),
                    duration_ms = TIMESTAMPDIFF(MICROSECOND, started_at, NOW()) / 1000
                WHERE execution_id = ?
                """, status, executionId);
        } else {
            jdbc.update("""
                UPDATE batch_workflow_execution
                SET status = ?
                WHERE execution_id = ?
                """, status, executionId);
        }
    }

    /**
     * 워크플로우 실행 실패 처리
     */
    private void updateWorkflowExecutionFailure(Long executionId, String errorMessage) {
        jdbc.update("""
            UPDATE batch_workflow_execution
            SET status = 'FAILED',
                ended_at = NOW(),
                duration_ms = TIMESTAMPDIFF(MICROSECOND, started_at, NOW()) / 1000,
                error_message = ?
            WHERE execution_id = ?
            """, errorMessage, executionId);
    }

    /**
     * 모든 워크플로우 정의 조회
     */
    public List<Map<String, Object>> getAllWorkflowDefinitions() {
        return jdbc.queryForList("SELECT * FROM batch_workflow_definition ORDER BY workflow_name");
    }

    /**
     * 워크플로우 실행 이력 조회
     */
    public List<Map<String, Object>> getWorkflowExecutions(String workflowId, int limit) {
        return jdbc.queryForList("""
            SELECT * FROM batch_workflow_execution
            WHERE workflow_id = ?
            ORDER BY started_at DESC
            LIMIT ?
            """, workflowId, limit);
    }
}

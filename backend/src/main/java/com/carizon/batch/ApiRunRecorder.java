package com.carizon.batch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * API 호출 추적 서비스
 * 파이프라인 API 실행 이력을 기록합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiRunRecorder {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * API 호출 시작 기록
     * @param apiPath API 경로 (예: /admin/pipeline/full)
     * @param apiMethod HTTP 메서드 (예: POST)
     * @param source API 소스/이름 (예: pipeline-full)
     * @return runId 실행 ID
     */
    public String recordStart(String apiPath, String apiMethod, String source) {
        return recordStart(apiPath, apiMethod, source, null);
    }

    /**
     * 스텝 시작 기록 (parent_run_id 포함)
     */
    public String recordStart(String apiPath, String apiMethod, String source, String parentRunId) {
        String runId = UUID.randomUUID().toString();
        Instant startedAt = Instant.now();

        jdbc.update(
                "INSERT INTO api_run(run_id, api_path, api_method, source, status, started_at, parent_run_id) VALUES (?,?,?,?,?,?,?)",
                runId, apiPath, apiMethod, source, "STARTED", Timestamp.from(startedAt), parentRunId
        );

        log.info("[API-RUN] start runId={} source={} apiPath={} started={}", runId, source, apiPath, startedAt);
        return runId;
    }

    /**
     * API 호출 성공 기록
     * @param runId 실행 ID
     * @param totalItems 처리된 아이템 수
     * @param result 결과 데이터 (Map 또는 Object)
     */
    public void recordSuccess(String runId, int totalItems, Object result) {
        Instant endedAt = Instant.now();
        String resultJson = null;
        
        if (result != null) {
            try {
                resultJson = objectMapper.writeValueAsString(result);
            } catch (JsonProcessingException e) {
                log.warn("[API-RUN] Failed to serialize result: {}", e.getMessage());
            }
        }
        
        // started_at으로부터 duration 계산
        Long durationMs = jdbc.queryForObject(
                "SELECT TIMESTAMPDIFF(MICROSECOND, started_at, ?) / 1000 FROM api_run WHERE run_id = ?",
                Long.class, Timestamp.from(endedAt), runId
        );
        
        jdbc.update(
                "UPDATE api_run SET ended_at=?, duration_ms=?, total_items=?, status='SUCCESS', result_json=? WHERE run_id=?",
                Timestamp.from(endedAt), durationMs, totalItems, resultJson, runId
        );
        
        log.info("[API-RUN] success runId={} totalItems={} duration={}ms ended={}", 
                runId, totalItems, durationMs, endedAt);
    }

    /**
     * API 호출 실패 기록
     * @param runId 실행 ID
     * @param totalItems 처리된 아이템 수 (실패 전까지)
     * @param errorMessage 에러 메시지
     */
    public void recordFail(String runId, int totalItems, String errorMessage) {
        Instant endedAt = Instant.now();
        String safe = errorMessage;
        if (safe != null && safe.length() > 2000) {
            safe = safe.substring(0, 2000) + "... (truncated)";
        }
        
        // started_at으로부터 duration 계산
        Long durationMs = jdbc.queryForObject(
                "SELECT TIMESTAMPDIFF(MICROSECOND, started_at, ?) / 1000 FROM api_run WHERE run_id = ?",
                Long.class, Timestamp.from(endedAt), runId
        );
        
        jdbc.update(
                "UPDATE api_run SET ended_at=?, duration_ms=?, total_items=?, status='FAIL', message=? WHERE run_id=?",
                Timestamp.from(endedAt), durationMs, totalItems, safe, runId
        );
        
        log.warn("[API-RUN] fail runId={} totalItems={} duration={}ms msg={}", 
                runId, totalItems, durationMs, safe);
    }

    /**
     * API 호출 실패 기록 (Exception 포함)
     * @param runId 실행 ID
     * @param totalItems 처리된 아이템 수
     * @param exception 예외 객체
     */
    public void recordFail(String runId, int totalItems, Exception exception) {
        String errorMessage = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        if (exception.getCause() != null) {
            errorMessage += " (caused by: " + exception.getCause().getMessage() + ")";
        }
        recordFail(runId, totalItems, errorMessage);
    }
}

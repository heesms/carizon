package com.carizon.batch;

import com.carizon.mapping.CodeMappingService;
import com.carizon.mapping.MasterMergeService;
import com.carizon.mapping.ModelNewPriceService;
import com.carizon.merge.MergeService;
import com.carizon.rag.service.CarEmbeddingBatchService;
import com.carizon.search.service.CarIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 배치 작업 관리 서비스
 * 크롤링, 머지, 인덱싱, 임베딩 등의 작업을 통합 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchJobService {

    private final JdbcTemplate jdbc;
    private final CrawlJobService crawlJobService;
    private final MergeService mergeService;
    private final CodeMappingService codeMappingService;
    private final MasterMergeService masterMergeService;
    private final ModelNewPriceService modelNewPriceService;
    private final CarIndexingService indexingService;
    private final CarEmbeddingBatchService embeddingBatchService;
    private final ApiRunRecorder apiRunRecorder;

    /**
     * 배치 작업 실행
     */
    @Transactional
    public String executeJob(String jobId, Map<String, Object> config) {
        long jobStart = System.currentTimeMillis();
        // 작업 정의 조회
        Map<String, Object> jobDef = jdbc.queryForMap(
            "SELECT * FROM batch_job_definition WHERE job_id = ?", jobId);
        
        String jobName = (String) jobDef.get("job_name");
        String jobType = (String) jobDef.get("job_type");
        
        // 실행 이력 생성
        Long executionId = createJobExecution(jobId, jobName, config);
        log.info("[batch] job start: {} type={} executionId={} config={}", jobId, jobType, executionId, config);
        
        try {
            // 작업 실행
            updateJobExecutionStatus(executionId, "RUNNING", null);
            
            Map<String, Object> result;
            switch (jobType) {
                case "CRAWL" -> result = executeCrawlJob(jobId, config);
                case "MERGE" -> result = executeMergeJob(jobId, config);
                case "CODE_MAPPING" -> result = executeCodeMappingJob(jobId, config);
                case "MASTER_MERGE" -> result = executeMasterMergeJob(jobId, config);
                case "MODEL_NEW_PRICE" -> result = executeModelNewPriceJob(jobId, config);
                case "INDEXING" -> result = executeIndexingJob(jobId, config);
                case "EMBEDDING" -> result = executeEmbeddingJob(jobId, config);
                default -> throw new IllegalArgumentException("Unknown job type: " + jobType);
            }
            
            // 성공 처리
            updateJobExecutionSuccess(executionId, result);
            log.info("[batch] job done: {} (executionId: {}, elapsedMs={}, result={})",
                    jobId, executionId, System.currentTimeMillis() - jobStart, result);
            
            return String.valueOf(executionId);
        } catch (Exception e) {
            // 실패 처리
            updateJobExecutionFailure(executionId, e.getMessage());
            log.error("[batch] job failed: {} (executionId: {}, elapsedMs={})",
                    jobId, executionId, System.currentTimeMillis() - jobStart, e);
            throw new RuntimeException("배치 작업 실패: " + jobId, e);
        }
    }

    /**
     * 크롤링 작업 실행
     */
    private Map<String, Object> executeCrawlJob(String jobId, Map<String, Object> config) throws Exception {
        if ("crawl_all".equals(jobId)) {
            crawlJobService.runDaily();
            return Map.of("message", "전체 크롤링 완료");
        } else if (jobId.startsWith("crawl_")) {
            String platform = jobId.replace("crawl_", "");
            switch (platform) {
                case "encar" -> crawlJobService.runNowEncar();
                case "encar_truck", "encar-truck" -> crawlJobService.runNowEncarTruck();
                case "kcar" -> crawlJobService.runNowKcar();
                case "cha" -> crawlJobService.runNowCha();
                case "chutcha" -> crawlJobService.runNowChutcha();
                case "charancha" -> crawlJobService.runNowCharancha();
                case "tcar" -> crawlJobService.runNowTcar();
            }
            return Map.of("message", platform + " 크롤링 완료");
        }
        throw new IllegalArgumentException("Unknown crawl job: " + jobId);
    }

    /**
     * 머지 작업 실행 (raw_* → platform_car)
     */
    private Map<String, Object> executeMergeJob(String jobId, Map<String, Object> config) throws Exception {
        LocalDate bizDate = config != null && config.containsKey("bizDate") 
            ? LocalDate.parse((String) config.get("bizDate"))
            : LocalDate.now();
        
        long stageStart = System.currentTimeMillis();
        log.info("[batch][MERGE] stage 1/1 start (0%) raw_* -> platform_car bizDate={}", bizDate);
        int merged = mergeService.mergeAllPlatforms(bizDate);
        log.info("[batch][MERGE] stage 1/1 done (100%) mergedCount={} durationMs={}",
                merged, System.currentTimeMillis() - stageStart);
        return Map.of("mergedCount", merged, "bizDate", bizDate.toString());
    }

    /**
     * 코드 매핑 작업 실행 (platform_car → cz_code_map)
     */
    private Map<String, Object> executeCodeMappingJob(String jobId, Map<String, Object> config) throws Exception {
        CodeMappingService.Scope scope = config != null && "FULL".equals(config.get("scope"))
            ? CodeMappingService.Scope.FULL
            : CodeMappingService.Scope.TODAY;
        
        String platform = config != null && config.containsKey("platform")
            ? (String) config.get("platform")
            : null;
        
        if (platform != null) {
            // 특정 플랫폼만
            long start = System.currentTimeMillis();
            log.info("[batch][CODE_MAPPING] stage 1/1 start (0%) platform={} scope={}", platform, scope);
            int mapped = codeMappingService.runAutoMapping(platform, scope);
            log.info("[batch][CODE_MAPPING] stage 1/1 done (100%) platform={} mapped={} durationMs={}",
                    platform, mapped, System.currentTimeMillis() - start);
            return Map.of("mappedCount", mapped, "platform", platform, "scope", scope.toString());
        } else {
            // 모든 플랫폼
            int total = 0;
            String[] platforms = {"ENCAR", "KCAR", "CHACHACHA", "CHUTCHA", "CHARANCHA", "TCAR"};
            for (int i = 0; i < platforms.length; i++) {
                String p = platforms[i];
                int stepNo = i + 1;
                int percent = (int) Math.round(stepNo * 100.0 / platforms.length);
                long stepStart = System.currentTimeMillis();
                log.info("[batch][CODE_MAPPING] stage {}/{} start ({}%) platform={} scope={}",
                        stepNo, platforms.length, percent, p, scope);
                try {
                    int mapped = codeMappingService.runAutoMapping(p, scope);
                    total += mapped;
                    log.info("[batch][CODE_MAPPING] stage {}/{} done ({}%) platform={} mapped={} accumulated={} durationMs={}",
                            stepNo, platforms.length, percent, p, mapped, total, System.currentTimeMillis() - stepStart);
                } catch (Exception e) {
                    log.error("[code mapping] {} failed", p, e);
                }
            }
            return Map.of("mappedCount", total, "scope", scope.toString());
        }
    }

    /**
     * car_master 머지 작업 실행 (platform_car + cz_code_map → car_master)
     */
    private Map<String, Object> executeMasterMergeJob(String jobId, Map<String, Object> config) throws Exception {
        LocalDate bizDate = config != null && config.containsKey("bizDate") 
            ? LocalDate.parse((String) config.get("bizDate"))
            : LocalDate.now();
        
        // 1. car_master 생성/업데이트
        long stage1Start = System.currentTimeMillis();
        log.info("[batch][MASTER] stage 1/2 start (0%) upsertAliveToCarMaster bizDate={}", bizDate);
        int upserted = masterMergeService.upsertAliveToCarMaster(bizDate);
        log.info("[batch][MASTER] stage 1/2 done (50%) upsertedCount={} durationMs={}",
                upserted, System.currentTimeMillis() - stage1Start);
        
        // 2. 우선순위 기반 최종 업데이트
        long stage2Start = System.currentTimeMillis();
        log.info("[batch][MASTER] stage 2/2 start (50%) updateCarMasterFromMapping");
        int updated = masterMergeService.updateCarMasterFromMapping();
        log.info("[batch][MASTER] stage 2/2 done (100%) updatedCount={} durationMs={}",
                updated, System.currentTimeMillis() - stage2Start);
        
        return Map.of(
            "upsertedCount", upserted,
            "updatedCount", updated,
            "bizDate", bizDate.toString()
        );
    }

    /**
     * 모델별 신차 출고가 집계 (car_master → cz_model_new_price)
     */
    private Map<String, Object> executeModelNewPriceJob(String jobId, Map<String, Object> config) throws Exception {
        int rows = modelNewPriceService.aggregateFromCarMaster();
        return Map.of("upsertedCount", rows);
    }

    /**
     * 인덱싱 작업 실행
     */
    private Map<String, Object> executeIndexingJob(String jobId, Map<String, Object> config) throws Exception {
        long start = System.currentTimeMillis();
        String runId = apiRunRecorder.recordStart("/batch/jobs/" + jobId, "BATCH", jobId);
        int processed = 0;
        log.info("[batch][SEARCH_INDEX] start jobId={} config={}", jobId, config);
        try {
            Map<String, Object> result;
            if ("indexing_incremental".equals(jobId)) {
                LocalDateTime since = config != null && config.containsKey("since")
                    ? LocalDateTime.parse((String) config.get("since"))
                    : LocalDateTime.now().minusHours(1);

                int indexed = indexingService.incrementalIndex(since);
                processed = indexed;
                log.info("[batch][SEARCH_INDEX] done jobId={} indexedCount={} elapsedMs={}",
                        jobId, indexed, System.currentTimeMillis() - start);
                result = Map.of("message", "증분 인덱싱 완료", "since", since.toString(), "indexedCount", indexed);
            } else if ("indexing_batch".equals(jobId)) {
                int limit = config != null && config.containsKey("limit")
                    ? (Integer) config.get("limit")
                    : 1000;

                int count = indexingService.batchIndex(limit);
                processed = count;
                log.info("[batch][SEARCH_INDEX] done jobId={} indexedCount={} elapsedMs={}",
                        jobId, count, System.currentTimeMillis() - start);
                result = Map.of("indexedCount", count);
            } else if ("indexing_reindex".equals(jobId)) {
                int indexed = indexingService.reindexAllCars();
                processed = indexed;
                log.info("[batch][SEARCH_INDEX] done jobId={} indexedCount={} elapsedMs={}",
                        jobId, indexed, System.currentTimeMillis() - start);
                result = Map.of("message", "전체 재인덱싱 완료", "indexedCount", indexed);
            } else {
                throw new IllegalArgumentException("Unknown indexing job: " + jobId);
            }
            apiRunRecorder.recordSuccess(runId, processed, result);
            return result;
        } catch (Exception e) {
            apiRunRecorder.recordFail(runId, processed, e);
            throw e;
        }
    }

    /**
     * 임베딩 작업 실행
     */
    private Map<String, Object> executeEmbeddingJob(String jobId, Map<String, Object> config) throws Exception {
        long start = System.currentTimeMillis();
        String runId = apiRunRecorder.recordStart("/batch/jobs/" + jobId, "BATCH", jobId);
        int processed = 0;
        log.info("[batch][EMBEDDING] start jobId={} config={}", jobId, config);
        try {
            Map<String, Object> result;
            if ("embedding_incremental".equals(jobId)) {
                LocalDateTime since = config != null && config.containsKey("since")
                    ? LocalDateTime.parse((String) config.get("since"))
                    : LocalDateTime.now().minusHours(1);

                int count = embeddingBatchService.incrementalEmbed(since);
                processed = count;
                log.info("[batch][EMBEDDING] done jobId={} embeddedCount={} elapsedMs={}",
                        jobId, count, System.currentTimeMillis() - start);
                result = Map.of("embeddedCount", count, "since", since.toString());
            } else if ("embedding_all".equals(jobId)) {
                int count = embeddingBatchService.embedAllCars();
                processed = count;
                log.info("[batch][EMBEDDING] done jobId={} embeddedCount={} elapsedMs={}",
                        jobId, count, System.currentTimeMillis() - start);
                result = Map.of("message", "전체 임베딩 완료", "embeddedCount", count);
            } else if (jobId.startsWith("embedding_car_")) {
                Long carId = Long.parseLong(jobId.replace("embedding_car_", ""));
                embeddingBatchService.embedCar(carId);
                processed = 1;
                log.info("[batch][EMBEDDING] done jobId={} carId={} elapsedMs={}",
                        jobId, carId, System.currentTimeMillis() - start);
                result = Map.of("carId", carId);
            } else {
                throw new IllegalArgumentException("Unknown embedding job: " + jobId);
            }
            apiRunRecorder.recordSuccess(runId, processed, result);
            return result;
        } catch (Exception e) {
            apiRunRecorder.recordFail(runId, processed, e);
            throw e;
        }
    }

    /**
     * 작업 실행 이력 생성
     */
    private Long createJobExecution(String jobId, String jobName, Map<String, Object> config) {
        jdbc.update("""
            INSERT INTO batch_job_execution (job_id, job_name, status, started_at, config_json)
            VALUES (?, ?, 'PENDING', NOW(), ?)
            """, jobId, jobName, config != null ? config.toString() : null);
        
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    /**
     * 작업 실행 상태 업데이트
     */
    private void updateJobExecutionStatus(Long executionId, String status, LocalDateTime startedAt) {
        jdbc.update("""
            UPDATE batch_job_execution
            SET status = ?, started_at = COALESCE(?, started_at, NOW())
            WHERE execution_id = ?
            """, status, startedAt, executionId);
    }

    /**
     * 작업 실행 성공 처리
     */
    private void updateJobExecutionSuccess(Long executionId, Map<String, Object> result) {
        Integer processedCount = result.containsKey("processedCount")
            ? toInteger(result.get("processedCount"))
            : (result.containsKey("indexedCount")
                ? toInteger(result.get("indexedCount"))
                : (result.containsKey("embeddedCount")
                    ? toInteger(result.get("embeddedCount"))
                    : null));
        Integer successCount = result.containsKey("successCount")
            ? toInteger(result.get("successCount"))
            : processedCount;
        
        jdbc.update("""
            UPDATE batch_job_execution
            SET status = 'SUCCESS',
                ended_at = NOW(),
                duration_ms = TIMESTAMPDIFF(MICROSECOND, started_at, NOW()) / 1000,
                processed_items = COALESCE(?, processed_items),
                success_items = COALESCE(?, success_items)
            WHERE execution_id = ?
            """,
            processedCount,
            successCount,
            executionId);
    }

    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 작업 실행 실패 처리
     */
    private void updateJobExecutionFailure(Long executionId, String errorMessage) {
        jdbc.update("""
            UPDATE batch_job_execution
            SET status = 'FAILED',
                ended_at = NOW(),
                duration_ms = TIMESTAMPDIFF(MICROSECOND, started_at, NOW()) / 1000,
                error_message = ?
            WHERE execution_id = ?
            """, errorMessage, executionId);
    }

    /**
     * 작업 실행 이력 조회
     */
    public List<Map<String, Object>> getJobExecutions(String jobId, int limit) {
        return jdbc.queryForList("""
            SELECT * FROM batch_job_execution
            WHERE job_id = ?
            ORDER BY started_at DESC
            LIMIT ?
            """, jobId, limit);
    }

    /**
     * 모든 작업 정의 조회
     */
    public List<Map<String, Object>> getAllJobDefinitions() {
        return jdbc.queryForList("SELECT * FROM batch_job_definition ORDER BY job_type, job_name");
    }
}

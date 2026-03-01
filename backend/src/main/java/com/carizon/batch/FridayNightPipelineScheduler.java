package com.carizon.batch;

import com.carizon.mapping.CodeMappingService;
import com.carizon.mapping.MasterMergeService;
import com.carizon.merge.MergeService;
import com.carizon.rag.service.ChromaVectorStoreService;
import com.carizon.search.service.CarIndexingService;
import com.carizon.recommendation.service.WeeklyBestHomeSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 금요일 22:00(KST) 주간 파이프라인 스케줄러.
 *
 * 순차:
 * 1) /admin/crawl/runAll
 * 2) /admin/pipeline/rebuild-platform-car
 * 3) /admin/pipeline/code-mapping-only?scope=FULL
 * 4) /admin/pipeline/rebuild-car-master-preserve-car-id
 *
 * 순차 종료 후 병렬:
 * - /admin/search/reindex
 * - /admin/embedding/reset-and-all
 * - /admin/recommendation/weekly-best/home/refresh
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FridayNightPipelineScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String[] CODE_MAPPING_PLATFORMS = {
            "ENCAR", "ENCAR_TRUCK", "KCAR", "CHACHACHA", "CHUTCHA", "CHARANCHA", "TCAR"
    };

    private final CrawlJobService crawlJobService;
    private final MergeService mergeService;
    private final CodeMappingService codeMappingService;
    private final MasterMergeService masterMergeService;
    private final CarIndexingService indexingService;
    private final ChromaVectorStoreService vectorStoreService;
    private final com.carizon.rag.service.CarEmbeddingBatchService embeddingBatchService;
    private final WeeklyBestHomeSnapshotService weeklyBestHomeSnapshotService;
    private final ApiRunRecorder apiRunRecorder;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(cron = "0 0 22 * * FRI", zone = "Asia/Seoul")
    public void runFridayNightPipeline() {
        runFridayNightPipelineInternal();
    }

    @Scheduled(cron = "0 0 22 * * MON", zone = "Asia/Seoul")
    public void runMondayEveningPipeline() {
        runFridayNightPipelineInternal();
    }

    private void runFridayNightPipelineInternal() {
        if (!running.compareAndSet(false, true)) {
            log.warn("[friday-night] already running, skip this trigger");
            return;
        }

        String runId = apiRunRecorder.recordStart(
                "/admin/batch/schedule/friday-night",
                "SCHEDULED",
                "friday-night-pipeline"
        );
        long totalStart = System.currentTimeMillis();
        LocalDate bizDate = LocalDate.now(KST);
        String currentStep = "INIT";
        int totalItems = 0;
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            log.info("[friday-night] start: bizDate={}", bizDate);

            // STEP 1: 크롤링
            currentStep = "1:/admin/crawl/runAll";
            String stepId1 = apiRunRecorder.recordStart("/admin/crawl/runAll", "SCHEDULED", "friday-crawl", runId);
            long crawlStart = System.currentTimeMillis();
            try {
                crawlJobService.runDaily();
                long crawlMs = System.currentTimeMillis() - crawlStart;
                result.put("crawl", Map.of("path", "/admin/crawl/runAll", "durationMs", crawlMs));
                apiRunRecorder.recordSuccess(stepId1, 0, result.get("crawl"));
            } catch (Exception e) {
                apiRunRecorder.recordFail(stepId1, 0, e);
                throw e;
            }

            // STEP 2: platform_car 재생성
            currentStep = "2:/admin/pipeline/rebuild-platform-car";
            String stepId2 = apiRunRecorder.recordStart("/admin/pipeline/rebuild-platform-car", "SCHEDULED", "friday-rebuild-platform-car", runId);
            long rebuildPlatformStart = System.currentTimeMillis();
            try {
                Map<String, Object> platformResult = mergeService.rebuildFromScratch(bizDate);
                int platformCarCount = toInt(platformResult.get("platformCarCount"));
                totalItems += Math.max(platformCarCount, 0);
                long rebuildPlatformMs = System.currentTimeMillis() - rebuildPlatformStart;
                result.put("rebuildPlatformCar", Map.of("path", "/admin/pipeline/rebuild-platform-car", "platformCarCount", platformCarCount, "durationMs", rebuildPlatformMs));
                apiRunRecorder.recordSuccess(stepId2, platformCarCount, result.get("rebuildPlatformCar"));
            } catch (Exception e) {
                apiRunRecorder.recordFail(stepId2, 0, e);
                throw e;
            }

            // STEP 3: 코드 매핑 (실패해도 계속)
            currentStep = "3:/admin/pipeline/code-mapping-only?scope=FULL";
            String stepId3 = apiRunRecorder.recordStart("/admin/pipeline/code-mapping-only", "SCHEDULED", "friday-code-mapping", runId);
            long mappingStart = System.currentTimeMillis();
            int mappedCount = 0;
            try {
                mappedCount = runCodeMappingFull();
                totalItems += Math.max(mappedCount, 0);
                long mappingMs = System.currentTimeMillis() - mappingStart;
                result.put("codeMapping", Map.of("path", "/admin/pipeline/code-mapping-only?scope=FULL", "mappedCount", mappedCount, "scope", CodeMappingService.Scope.FULL.toString(), "durationMs", mappingMs));
                apiRunRecorder.recordSuccess(stepId3, mappedCount, result.get("codeMapping"));
            } catch (Exception e) {
                log.warn("[friday-night] code-mapping step failed, continuing to rebuild-car-master: {}", e.getMessage(), e);
                result.put("codeMappingError", e.getMessage());
                apiRunRecorder.recordFail(stepId3, mappedCount, e);
            }

            // STEP 4: car_master 재생성 (기존 car_no는 car_id 유지)
            currentStep = "4:/admin/pipeline/rebuild-car-master-preserve-car-id";
            String stepId4 = apiRunRecorder.recordStart("/admin/pipeline/rebuild-car-master-preserve-car-id", "SCHEDULED", "friday-rebuild-car-master-preserve", runId);
            long rebuildMasterStart = System.currentTimeMillis();
            try {
                int carMasterCount = masterMergeService.rebuildCarMasterFromScratchPreserveCarId(bizDate);
                int masterUpdatedCount = masterMergeService.updateCarMasterFromMapping();
                int linkedCount = mergeService.linkToMaster();
                totalItems += Math.max(carMasterCount, 0);
                totalItems += Math.max(linkedCount, 0);
                long rebuildMasterMs = System.currentTimeMillis() - rebuildMasterStart;
                result.put("rebuildCarMaster", Map.of("path", "/admin/pipeline/rebuild-car-master-preserve-car-id", "carMasterCount", carMasterCount, "updatedCount", masterUpdatedCount, "linkedCount", linkedCount, "durationMs", rebuildMasterMs));
                apiRunRecorder.recordSuccess(stepId4, carMasterCount, result.get("rebuildCarMaster"));
            } catch (Exception e) {
                apiRunRecorder.recordFail(stepId4, 0, e);
                throw e;
            }

            // STEP 5: ES 재인덱스 + 임베딩 병렬
            currentStep = "PARALLEL:/admin/search/reindex + /admin/embedding/reset-and-all";
            String stepId5 = apiRunRecorder.recordStart("/admin/search/reindex+embedding", "SCHEDULED", "friday-reindex-embedding", runId);
            try {
                Map<String, Object> parallelResult = runReindexAndEmbeddingInParallel();
                totalItems += toInt(parallelResult.get("indexedCount"));
                totalItems += toInt(parallelResult.get("embeddedCount"));
                result.put("parallel", parallelResult);
                apiRunRecorder.recordSuccess(stepId5, toInt(parallelResult.get("indexedCount")) + toInt(parallelResult.get("embeddedCount")), parallelResult);
            } catch (Exception e) {
                apiRunRecorder.recordFail(stepId5, 0, e);
                throw e;
            }

            // STEP 6: 메인 주간 베스트 스냅샷 즉시 갱신
            currentStep = "6:/admin/recommendation/weekly-best/home/refresh";
            String stepId6 = apiRunRecorder.recordStart("/admin/recommendation/weekly-best/home/refresh", "SCHEDULED", "friday-home-weekly-best-refresh", runId);
            try {
                int homeWeeklyBestCount = weeklyBestHomeSnapshotService.refreshSnapshot(20);
                result.put("homeWeeklyBestRefresh", Map.of(
                        "path", "/admin/recommendation/weekly-best/home/refresh",
                        "count", homeWeeklyBestCount
                ));
                apiRunRecorder.recordSuccess(stepId6, Math.max(homeWeeklyBestCount, 0), result.get("homeWeeklyBestRefresh"));
            } catch (Exception e) {
                log.warn("[friday-night] home weekly best snapshot refresh failed: {}", e.getMessage(), e);
                result.put("homeWeeklyBestRefreshError", e.getMessage());
                apiRunRecorder.recordFail(stepId6, 0, e);
            }

            long totalDurationMs = System.currentTimeMillis() - totalStart;
            result.put("bizDate", bizDate.toString());
            result.put("totalDurationMs", totalDurationMs);
            apiRunRecorder.recordSuccess(runId, Math.max(totalItems, 0), result);

            log.info("[friday-night] done: totalItems={}, totalDurationMs={}", totalItems, totalDurationMs);
        } catch (Exception e) {
            result.put("bizDate", bizDate.toString());
            result.put("failedStep", currentStep);
            result.put("elapsedMs", System.currentTimeMillis() - totalStart);
            apiRunRecorder.recordFail(runId, Math.max(totalItems, 0), e);
            log.error("[friday-night] failed at {}", currentStep, e);
        } finally {
            running.set(false);
        }
    }

    private int runCodeMappingFull() {
        int totalMapped = 0;
        for (String platform : CODE_MAPPING_PLATFORMS) {
            try {
                int mapped = codeMappingService.runAutoMapping(platform, CodeMappingService.Scope.FULL);
                totalMapped += mapped;
                log.info("[friday-night] code-mapping platform={} mapped={} total={}", platform, mapped, totalMapped);
            } catch (Exception e) {
                // 기존 /admin/pipeline/code-mapping-only 동작과 동일하게 개별 실패는 계속 진행
                log.error("[friday-night] code-mapping failed: platform={}", platform, e);
            }
        }
        return totalMapped;
    }

    private Map<String, Object> runReindexAndEmbeddingInParallel() {
        long start = System.currentTimeMillis();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Integer> reindexFuture = CompletableFuture.supplyAsync(
                    indexingService::reindexAllCars,
                    executor
            );
            CompletableFuture<Integer> embeddingFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    vectorStoreService.deleteCollection();
                    return embeddingBatchService.embedAllCars();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, executor);

            CompletableFuture.allOf(reindexFuture, embeddingFuture).join();

            int indexedCount = reindexFuture.join();
            int embeddedCount = embeddingFuture.join();

            return Map.of(
                    "paths", "/admin/search/reindex, /admin/embedding/reset-and-all",
                    "indexedCount", indexedCount,
                    "embeddedCount", embeddedCount,
                    "durationMs", System.currentTimeMillis() - start
            );
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("parallel jobs failed: " + cause.getMessage(), cause);
        } finally {
            executor.shutdown();
        }
    }

    private int toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }
}

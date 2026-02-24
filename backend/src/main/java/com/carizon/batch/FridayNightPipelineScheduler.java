package com.carizon.batch;

import com.carizon.mapping.CodeMappingService;
import com.carizon.mapping.MasterMergeService;
import com.carizon.merge.MergeService;
import com.carizon.rag.service.CarEmbeddingBatchService;
import com.carizon.rag.service.ChromaVectorStoreService;
import com.carizon.rag.service.LikeService;
import com.carizon.search.service.CarIndexingService;
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
 * 4) /admin/pipeline/rebuild-car-master
 *
 * 순차 종료 후 병렬:
 * - /admin/search/reindex
 * - /admin/embedding/reset-and-all
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FridayNightPipelineScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String[] CODE_MAPPING_PLATFORMS = {
            "ENCAR", "KCAR", "CHACHACHA", "CHUTCHA", "CHARANCHA", "TCAR"
    };

    private final CrawlJobService crawlJobService;
    private final MergeService mergeService;
    private final CodeMappingService codeMappingService;
    private final MasterMergeService masterMergeService;
    private final LikeService likeService;
    private final CarIndexingService indexingService;
    private final ChromaVectorStoreService vectorStoreService;
    private final CarEmbeddingBatchService embeddingBatchService;
    private final ApiRunRecorder apiRunRecorder;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(cron = "0 0 22 * * FRI", zone = "Asia/Seoul")
    public void runFridayNightPipeline() {
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

            currentStep = "1:/admin/crawl/runAll";
            long crawlStart = System.currentTimeMillis();
            crawlJobService.runDaily();
            result.put("crawl", Map.of(
                    "path", "/admin/crawl/runAll",
                    "durationMs", System.currentTimeMillis() - crawlStart
            ));

            currentStep = "2:/admin/pipeline/rebuild-platform-car";
            long rebuildPlatformStart = System.currentTimeMillis();
            Map<String, Object> platformResult = mergeService.rebuildFromScratch(bizDate);
            int platformCarCount = toInt(platformResult.get("platformCarCount"));
            long likesClearedAfterPlatform = clearLikesForRebuild();
            totalItems += Math.max(platformCarCount, 0);
            result.put("rebuildPlatformCar", Map.of(
                    "path", "/admin/pipeline/rebuild-platform-car",
                    "platformCarCount", platformCarCount,
                    "likesCleared", likesClearedAfterPlatform,
                    "durationMs", System.currentTimeMillis() - rebuildPlatformStart
            ));

            currentStep = "3:/admin/pipeline/code-mapping-only?scope=FULL";
            long mappingStart = System.currentTimeMillis();
            int mappedCount = runCodeMappingFull();
            totalItems += Math.max(mappedCount, 0);
            result.put("codeMapping", Map.of(
                    "path", "/admin/pipeline/code-mapping-only?scope=FULL",
                    "mappedCount", mappedCount,
                    "scope", CodeMappingService.Scope.FULL.toString(),
                    "durationMs", System.currentTimeMillis() - mappingStart
            ));

            currentStep = "4:/admin/pipeline/rebuild-car-master";
            long rebuildMasterStart = System.currentTimeMillis();
            int carMasterCount = masterMergeService.rebuildCarMasterFromScratch(bizDate);
            int masterUpdatedCount = masterMergeService.updateCarMasterFromMapping();
            int linkedCount = mergeService.linkToMaster();
            long likesClearedAfterMaster = clearLikesForRebuild();
            totalItems += Math.max(carMasterCount, 0);
            totalItems += Math.max(linkedCount, 0);
            result.put("rebuildCarMaster", Map.of(
                    "path", "/admin/pipeline/rebuild-car-master",
                    "carMasterCount", carMasterCount,
                    "updatedCount", masterUpdatedCount,
                    "linkedCount", linkedCount,
                    "likesCleared", likesClearedAfterMaster,
                    "durationMs", System.currentTimeMillis() - rebuildMasterStart
            ));

            currentStep = "PARALLEL:/admin/search/reindex + /admin/embedding/reset-and-all";
            Map<String, Object> parallelResult = runReindexAndEmbeddingInParallel();
            totalItems += toInt(parallelResult.get("indexedCount"));
            totalItems += toInt(parallelResult.get("embeddedCount"));
            result.put("parallel", parallelResult);

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

    private long clearLikesForRebuild() {
        try {
            Map<String, Long> cleared = likeService.clearAllLikes();
            if (cleared == null) return 0L;
            Long totalDeleted = cleared.get("totalDeleted");
            return totalDeleted != null ? totalDeleted : 0L;
        } catch (Exception e) {
            log.warn("[friday-night] like clear failed: {}", e.getMessage());
            return -1L;
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

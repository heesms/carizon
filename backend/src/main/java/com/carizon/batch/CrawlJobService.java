package com.carizon.batch;

import com.carizon.crawler.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlJobService {
    private final ChachachaCrawler chachacha;
    private final EncarCrawler encar;
    private final EncarTruckCrawler encarTruck;
    private final KcarCrawler kcar;
    private final ChutchaCrawler chutcha;
    private final CharanchaCrawler charancha;
    private final TcarCrawler tcar;

    // 매일 새벽 03:15 KST (순차 실행 - 기존 방식 유지)
    @Scheduled(cron = "0 15 3 * * *", zone = "Asia/Seoul")
    public void runDaily() {
        log.info("[CRAWL] daily schedule start (sequential)");
        // 순서: 차차차 -> kcar -> tcar -> 차란차 -> 첫차 -> 엔카
        chachacha.runOnce();
        kcar.runOnceFull();
        tcar.runOnceFull();
        charancha.runOnceFull();
        chutcha.runOnceFull();
        encar.runOnce();  // 엔카를 마지막으로
        log.info("[CRAWL] daily schedule end");
    }

    /**
     * 비동기로 모든 플랫폼 크롤링 실행 (병렬 처리)
     * 모든 플랫폼을 동시에 실행하여 전체 크롤링 시간 단축
     */
    public void runDailyAsync() {
        log.info("[CRAWL] daily schedule start (async)");
        
        var executor = Executors.newFixedThreadPool(6);
        try {
            long startTime = System.currentTimeMillis();
            
            CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> {
                    log.info("[CRAWL] CHACHACHA start");
                    try {
                        chachacha.runOnce();
                        log.info("[CRAWL] CHACHACHA done");
                    } catch (Exception e) {
                        log.error("[CRAWL] CHACHACHA failed", e);
                    }
                }, executor),
                CompletableFuture.runAsync(() -> {
                    log.info("[CRAWL] KCAR start");
                    try {
                        kcar.runOnceFull();
                        log.info("[CRAWL] KCAR done");
                    } catch (Exception e) {
                        log.error("[CRAWL] KCAR failed", e);
                    }
                }, executor),
                CompletableFuture.runAsync(() -> {
                    log.info("[CRAWL] TCAR start");
                    try {
                        tcar.runOnceFull();
                        log.info("[CRAWL] TCAR done");
                    } catch (Exception e) {
                        log.error("[CRAWL] TCAR failed", e);
                    }
                }, executor),
                CompletableFuture.runAsync(() -> {
                    log.info("[CRAWL] CHARANCHA start");
                    try {
                        charancha.runOnceFull();
                        log.info("[CRAWL] CHARANCHA done");
                    } catch (Exception e) {
                        log.error("[CRAWL] CHARANCHA failed", e);
                    }
                }, executor),
                CompletableFuture.runAsync(() -> {
                    log.info("[CRAWL] CHUTCHA start");
                    try {
                        chutcha.runOnceFull();
                        log.info("[CRAWL] CHUTCHA done");
                    } catch (Exception e) {
                        log.error("[CRAWL] CHUTCHA failed", e);
                    }
                }, executor),
                CompletableFuture.runAsync(() -> {
                    log.info("[CRAWL] ENCAR start");
                    try {
                        encar.runOnce();
                        log.info("[CRAWL] ENCAR done");
                    } catch (Exception e) {
                        log.error("[CRAWL] ENCAR failed", e);
                    }
                }, executor)
            ).join(); // 모든 크롤링 완료 대기
            
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[CRAWL] daily schedule end (async, elapsed: {}s)", elapsed / 1000);
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }


    public void runNowKcar() {
        log.info("[CRAWL] manual KCAR run start");
        kcar.runOnceFull();
        log.info("[CRAWL] manual KCAR run end");
    }

    public void runNowEncar() {
        log.info("[CRAWL] manual runNowEncar run start");
        encar.runOnce();
        log.info("[CRAWL] manual runNowEncar run end");
    }

    public void runNowEncarTruck() {
        log.info("[CRAWL] manual runNowEncarTruck run start");
        encarTruck.runOnce();
        log.info("[CRAWL] manual runNowEncarTruck run end");
    }

    public Map<String, Object> runNowEncarPagingTest(Integer maxPages) {
        log.info("[CRAWL] manual runNowEncarPagingTest start maxPages={}", maxPages);
        Map<String, Object> result = encar.runPagingCountOnly(maxPages);
        log.info("[CRAWL] manual runNowEncarPagingTest end result={}", result);
        return result;
    }

    public Map<String, Object> runNowEncarTruckPagingTest(Integer maxPages) {
        log.info("[CRAWL] manual runNowEncarTruckPagingTest start maxPages={}", maxPages);
        Map<String, Object> result = encarTruck.runPagingCountOnly(maxPages);
        log.info("[CRAWL] manual runNowEncarTruckPagingTest end result={}", result);
        return result;
    }

    public Map<String, Object> runNowEncarUseYnOnly() {
        log.info("[CRAWL] manual runNowEncarUseYnOnly start");
        Map<String, Object> result = encar.refreshUseYnOnly();
        log.info("[CRAWL] manual runNowEncarUseYnOnly end result={}", result);
        return result;
    }

    public Map<String, Object> runNowEncarTruckUseYnOnly() {
        log.info("[CRAWL] manual runNowEncarTruckUseYnOnly start");
        Map<String, Object> result = encarTruck.refreshUseYnOnly();
        log.info("[CRAWL] manual runNowEncarTruckUseYnOnly end result={}", result);
        return result;
    }

    public void runNowCha() {
        log.info("[CRAWL] manual chachacha run start");
        chachacha.runOnce();
        log.info("[CRAWL] manual chachacha run end");
    }

    public void runNowChutcha() {
        log.info("[CRAWL] manual runNowChutcha run start");
        chutcha.runOnceFull();
        log.info("[CRAWL] manual runNowChutcha run end");}

    public void runNowCharancha() {
        log.info("[CRAWL] manual runNowChutcha run start");
        charancha.runOnceFull();
        log.info("[CRAWL] manual runNowChutcha run end");}

    public void runNowTcar() {
        log.info("[CRAWL] manual runNowChutcha run start");
        tcar.runOnceFull();
        log.info("[CRAWL] manual runNowChutcha run end");}

}

// com.carizon.batch.CrawlJobService.java
package com.carizon.batch;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CrawlJobService {
    private final ChachachaCrawler crawler;

    // 매일 새벽 03:15 (초 분 시 일 월 요일)
    @Scheduled(cron = "0 15 3 * * *", zone = "Asia/Seoul")
    public void runDaily() {
        crawler.runOnce();
    }

    public void runNow() { crawler.runOnce(); }
}

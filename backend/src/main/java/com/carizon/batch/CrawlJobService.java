package com.carizon.batch;

import com.carizon.crawler.ChachachaCrawler;
import com.carizon.crawler.ChutchaCrawler;
import com.carizon.crawler.EncarCrawler;
import com.carizon.crawler.KcarCrawler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlJobService {
    private final ChachachaCrawler chachacha;
    private final EncarCrawler encar;
    private final KcarCrawler kcar;         // ✅ 추가
    // CrawlJobService
    private final ChutchaCrawler chutcha;


    // 매일 새벽 03:15 KST
    @Scheduled(cron = "0 15 3 * * *", zone = "Asia/Seoul")
    public void runDaily() {
        log.info("[CRAWL] daily schedule start");
        chachacha.runOnce();
        encar.runOnce();
        kcar.runOnceFull();                 // ✅ 추가
        chutcha.runOnceFull();
        log.info("[CRAWL] daily schedule end");
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

    public void runNowCha() {
        log.info("[CRAWL] manual chachacha run start");
        chachacha.runOnce();
        log.info("[CRAWL] manual chachacha run end");
    }

    public void runNowChutcha() {
        log.info("[CRAWL] manual runNowChutcha run start");
        chutcha.runOnceFull();
        log.info("[CRAWL] manual runNowChutcha run end");}

}

package com.carizon.jobs.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PriceSnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(PriceSnapshotScheduler.class);

    @Scheduled(cron = "0 0 2 * * *")
    public void capturePriceSnapshots() {
        log.info("Running nightly price snapshot job");
    }
}

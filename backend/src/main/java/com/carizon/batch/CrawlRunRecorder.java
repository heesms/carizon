package com.carizon.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlRunRecorder {

    private final JdbcTemplate jdbc;

    public String recordStart(String source, Instant startedAt) {
        String runId = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO crawl_run(run_id, source, started_at, status, total_items) VALUES (?,?,?,?,?)",
                runId, source, Timestamp.from(startedAt), "STARTED", 0
        );
        log.info("[CRAWL-RUN] start runId={} source={} started={}", runId, source, startedAt);
        return runId;
    }

    public void recordEnd(String runId, int totalItems, Instant endedAt) {
        jdbc.update(
                "UPDATE crawl_run SET ended_at=?, total_items=?, status='SUCCESS' WHERE run_id=?",
                Timestamp.from(endedAt), totalItems, runId
        );
        log.info("[CRAWL-RUN] end   runId={} totalItems={} ended={}", runId, totalItems, endedAt);
    }

    public void recordFail(String runId, int totalSoFar, Instant endedAt, String msg) {
        String safe = msg;
        if (safe != null && safe.length() > 480) safe = safe.substring(0, 480);
        jdbc.update(
                "UPDATE crawl_run SET ended_at=?, total_items=?, status='FAIL', message=? WHERE run_id=?",
                Timestamp.from(endedAt), totalSoFar, safe, runId
        );
        log.warn("[CRAWL-RUN] fail  runId={} totalSoFar={} msg={}", runId, totalSoFar, safe);
    }
}

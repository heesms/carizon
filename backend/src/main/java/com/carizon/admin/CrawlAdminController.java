package com.carizon.admin;


import com.carizon.batch.CrawlJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/admin/crawl")
@RequiredArgsConstructor
public class CrawlAdminController {
    private final CrawlJobService job;
    private final JdbcTemplate jdbc;

    @PostMapping("/runAll")     public Map<String, Object> runNowBoth() { job.runDaily(); return Map.of("ok", true); }
    @PostMapping("/encar")   public Map<String, Object> runNowEncar() { job.runNowEncar(); return Map.of("ok", true); }
    @PostMapping("/kcar")    public Map<String, Object> runNowKcar()  { job.runNowKcar(); return Map.of("ok", true); }
    @PostMapping("/cha")    public Map<String, Object> runNowCha()  { job.runNowCha(); return Map.of("ok", true); }
    @PostMapping("/chutcha") public Map<String,Object> runChutcha(){ job.runNowChutcha(); return Map.of("ok", true); }


    @GetMapping("/runs")
    public List<Map<String,Object>> recentRuns(@RequestParam(defaultValue = "20") int limit) {
        return jdbc.queryForList(
                "SELECT run_id, source, status, total_items, started_at, ended_at, message " +
                        "FROM crawl_run ORDER BY started_at DESC LIMIT ?", limit
        );
    }
}

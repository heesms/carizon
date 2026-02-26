package com.carizon.admin;


import com.carizon.batch.CrawlJobService;
import com.carizon.common.dto.ApiResponse;
import com.carizon.merge.MergeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/admin/crawl")
@RequiredArgsConstructor
public class CrawlAdminController {
    private final CrawlJobService job;
    private final JdbcTemplate jdbc;
    private final MergeService mergeService;

    @PostMapping("/runAll")
    public Map<String, Object> runNowBoth() {
        job.runDaily();
        return Map.of("ok", true, "message", "순차 크롤링 시작");
    }

    @PostMapping("/runAllAsync")
    public Map<String, Object> runNowBothAsync() {
        job.runDailyAsync();
        return Map.of("ok", true, "message", "비동기 크롤링 시작");
    }
    @PostMapping("/encar")   public Map<String, Object> runNowEncar() { job.runNowEncar(); return Map.of("ok", true); }
    @PostMapping("/encar/paging-test")
    public Map<String, Object> runEncarPagingTest(@RequestParam(required = false) Integer maxPages) {
        return Map.of("ok", true, "result", job.runNowEncarPagingTest(maxPages));
    }
    @PostMapping("/encar/use-yn")
    public Map<String, Object> runEncarUseYnOnly() {
        return Map.of("ok", true, "result", job.runNowEncarUseYnOnly());
    }
    @PostMapping("/encar-truck") public Map<String, Object> runNowEncarTruck() { job.runNowEncarTruck(); return Map.of("ok", true); }
    @PostMapping("/encar-truck/paging-test")
    public Map<String, Object> runEncarTruckPagingTest(@RequestParam(required = false) Integer maxPages) {
        return Map.of("ok", true, "result", job.runNowEncarTruckPagingTest(maxPages));
    }
    @PostMapping("/encar-truck/use-yn")
    public Map<String, Object> runEncarTruckUseYnOnly() {
        return Map.of("ok", true, "result", job.runNowEncarTruckUseYnOnly());
    }
    @PostMapping("/kcar")    public Map<String, Object> runNowKcar()  { job.runNowKcar(); return Map.of("ok", true); }
    @PostMapping("/cha")    public Map<String, Object> runNowCha()  { job.runNowCha(); return Map.of("ok", true); }
    @PostMapping("/chutcha") public Map<String,Object> runChutcha(){ job.runNowChutcha(); return Map.of("ok", true); }
    @PostMapping("/charancha") public Map<String,Object> runCharancha(){ job.runNowCharancha(); return Map.of("ok", true); }
    @PostMapping("/tcar") public Map<String,Object> runTcar(){ job.runNowTcar(); return Map.of("ok", true); }


    @PostMapping("/merge")
    public ApiResponse<Map<String, Object>> runMerge(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        try {
            LocalDate date = bizDate != null ? bizDate : LocalDate.now();
            int merged = mergeService.mergeAllPlatforms(date);
            return ApiResponse.success(Map.of(
                "message", "머지 완료",
                "mergedCount", merged,
                "bizDate", date.toString()
            ));
        } catch (Exception e) {
            log.error("[crawl] merge run failed", e);
            return ApiResponse.error("머지 실패: " + e.getMessage());
        }
    }

    @PostMapping("/merge/{platform}")
    public ApiResponse<String> runMergePlatform(
            @PathVariable String platform,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate) {
        try {
            LocalDate date = bizDate != null ? bizDate : LocalDate.now();
            int merged = 0;
            switch (platform.toUpperCase()) {
                case "CHACHACHA", "CHA" -> merged = mergeService.mergeChachacha(date);
                case "ENCAR" -> merged = mergeService.mergeEncar(date);
                case "KCAR" -> merged = mergeService.mergeKcar(date);
                case "CHUTCHA" -> merged = mergeService.mergeChutcha(date);
                case "CHARANCHA" -> merged = mergeService.mergeCharancha(date);
                case "TCAR" -> merged = mergeService.mergeTcar(date);
                default -> throw new IllegalArgumentException("Unknown platform: " + platform);
            }
            return ApiResponse.success(platform + " 머지 완료: " + merged + "건");
        } catch (Exception e) {
            log.error("[crawl] platform merge failed: {}", platform, e);
            return ApiResponse.error("머지 실패: " + e.getMessage());
        }
    }

    @GetMapping("/runs")
    public ApiResponse<List<Map<String,Object>>> recentRuns(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String source) {
        try {
            StringBuilder sql = new StringBuilder(
                    "SELECT run_id, source, status, total_items, started_at, ended_at, message FROM crawl_run WHERE 1=1"
            );
            List<Object> params = new java.util.ArrayList<>();
            if (source != null && !source.isBlank()) {
                sql.append(" AND source = ?");
                params.add(source);
            }
            sql.append(" ORDER BY started_at DESC LIMIT ?");
            params.add(limit);
            List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());
            return ApiResponse.success(rows);
        } catch (Exception e) {
            log.error("[crawl] recent runs fetch failed", e);
            return ApiResponse.error("크롤링 이력 조회 실패: " + e.getMessage());
        }
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> crawlStatus(@RequestParam(defaultValue = "50") int limit) {
        try {
            List<Map<String, Object>> recent = jdbc.queryForList(
                    "SELECT run_id, source, status, total_items, started_at, ended_at, message " +
                            "FROM crawl_run ORDER BY started_at DESC LIMIT ?",
                    limit
            );
            long running = recent.stream().filter(r -> "RUNNING".equals(String.valueOf(r.get("status")))).count();
            long success = recent.stream().filter(r -> "SUCCESS".equals(String.valueOf(r.get("status")))).count();
            long fail = recent.stream().filter(r -> "FAIL".equals(String.valueOf(r.get("status")))).count();

            List<Map<String, Object>> latestBySource = jdbc.queryForList("""
                SELECT c1.source, c1.status, c1.total_items, c1.started_at, c1.ended_at, c1.message
                FROM crawl_run c1
                JOIN (
                  SELECT source, MAX(started_at) AS max_started_at
                  FROM crawl_run
                  GROUP BY source
                ) c2 ON c1.source = c2.source AND c1.started_at = c2.max_started_at
                ORDER BY c1.source
                """);

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("recent", recent);
            result.put("runningCount", running);
            result.put("successCount", success);
            result.put("failCount", fail);
            result.put("latestBySource", latestBySource);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[crawl] status fetch failed", e);
            return ApiResponse.error("크롤링 현황 조회 실패: " + e.getMessage());
        }
    }
}

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
    public List<Map<String,Object>> recentRuns(@RequestParam(defaultValue = "20") int limit) {
        return jdbc.queryForList(
                "SELECT run_id, source, status, total_items, started_at, ended_at, message " +
                        "FROM crawl_run ORDER BY started_at DESC LIMIT ?", limit
        );
    }
}

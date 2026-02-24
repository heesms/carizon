package com.carizon.admin;

import com.carizon.batch.ApiRunRecorder;
import com.carizon.common.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.carizon.search.kafka.CarIndexSyncProducer;
import com.carizon.search.service.CarIndexingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 검색 인덱스 관리 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/admin/search")
@RequiredArgsConstructor
@Tag(name = "검색 관리", description = "Elasticsearch 인덱스 관리 API")
public class SearchAdminController {

    private final CarIndexingService indexingService;
    private final JdbcTemplate jdbc;
    private final ApiRunRecorder apiRunRecorder;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private CarIndexSyncProducer carIndexSyncProducer;

    @PostMapping("/reindex")
    @Operation(summary = "전체 차량 재인덱싱", description = "모든 차량 데이터를 Elasticsearch에 재인덱싱합니다. (기존 인덱스 삭제 후 재생성)")
    public ApiResponse<Map<String, Object>> reindexAll() {
        String runId = apiRunRecorder.recordStart("/admin/search/reindex", "POST", "search-reindex");
        log.info("[search admin] full reindex start");
        try {
            int count = indexingService.reindexAllCars();
            Map<String, Object> result = Map.of(
                "message", "재인덱싱 완료",
                "indexedCount", count
            );
            apiRunRecorder.recordSuccess(runId, count, result);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[search admin] reindex failed", e);
            apiRunRecorder.recordFail(runId, 0, e);
            return ApiResponse.error("재인덱싱 실패: " + e.getMessage());
        }
    }

    @PostMapping("/incremental")
    @Operation(summary = "증분 인덱싱", description = "지정된 시간 이후에 업데이트된 차량만 인덱싱합니다.")
    public ApiResponse<Map<String, Object>> incrementalIndex(
            @RequestParam(required = false) String since) {
        String runId = apiRunRecorder.recordStart("/admin/search/incremental", "POST", "search-incremental");
        log.info("[search admin] incremental index start: since={}", since);
        try {
            LocalDateTime sinceTime = since != null && !since.isEmpty() 
                ? LocalDateTime.parse(since) 
                : LocalDateTime.now().minusHours(1); // 기본값: 1시간 전
            
            int count = indexingService.incrementalIndex(sinceTime);
            Map<String, Object> result = Map.of(
                "message", "증분 인덱싱 완료",
                "indexedCount", count,
                "since", sinceTime.toString()
            );
            apiRunRecorder.recordSuccess(runId, count, result);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[search admin] incremental index failed", e);
            apiRunRecorder.recordFail(runId, 0, e);
            return ApiResponse.error("증분 인덱싱 실패: " + e.getMessage());
        }
    }

    @PostMapping("/batch")
    @Operation(summary = "배치 인덱싱", description = "지정된 개수만큼 차량을 인덱싱합니다. (스케줄러용)")
    public ApiResponse<Map<String, Object>> batchIndex(
            @RequestParam(defaultValue = "1000") int limit) {
        String runId = apiRunRecorder.recordStart("/admin/search/batch", "POST", "search-batch");
        log.info("[search admin] batch index start: limit={}", limit);
        try {
            int count = indexingService.batchIndex(limit);
            Map<String, Object> result = Map.of(
                "message", "배치 인덱싱 완료",
                "indexedCount", count,
                "requestedLimit", limit
            );
            apiRunRecorder.recordSuccess(runId, count, result);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[search admin] batch index failed", e);
            apiRunRecorder.recordFail(runId, 0, e);
            return ApiResponse.error("배치 인덱싱 실패: " + e.getMessage());
        }
    }

    @PostMapping("/sync")
    @Operation(summary = "차량 인덱스 동기화 (Kafka)", description = "지정한 차량 ID들을 Kafka로 보내 검색 인덱스에 비동기 반영합니다. app.kafka.enabled=true 필요.")
    public ApiResponse<Map<String, Object>> syncCarIds(@RequestBody Map<String, List<Long>> body) {
        String runId = apiRunRecorder.recordStart("/admin/search/sync", "POST", "search-sync");
        List<Long> carIds = body != null ? body.get("carIds") : null;
        if (carIds == null || carIds.isEmpty()) {
            apiRunRecorder.recordFail(runId, 0, "carIds empty");
            return ApiResponse.error("carIds 필드에 ID 목록을 넣어주세요.");
        }
        if (carIndexSyncProducer == null) {
            apiRunRecorder.recordFail(runId, 0, "Kafka disabled");
            return ApiResponse.error("Kafka가 비활성화되어 있습니다. 단건/배치 동기화를 쓰려면 app.kafka.enabled=true 로 설정하고 Kafka를 실행하세요.");
        }
        try {
            carIndexSyncProducer.sendSyncCarIds(carIds);
            Map<String, Object> result = Map.of(
                "message", "동기화 요청 전송됨 (Kafka)",
                "carIdsCount", carIds.size()
            );
            apiRunRecorder.recordSuccess(runId, carIds.size(), result);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[search admin] sync send failed", e);
            apiRunRecorder.recordFail(runId, carIds.size(), e);
            return ApiResponse.error("동기화 요청 전송 실패: " + e.getMessage());
        }
    }

    @PostMapping("/test")
    @Operation(summary = "Elasticsearch 검색 테스트", description = "Elasticsearch 검색 API를 테스트합니다.")
    public ApiResponse<Map<String, Object>> testSearch(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String makerCode,
            @RequestParam(required = false) Integer priceMin,
            @RequestParam(required = false) Integer priceMax,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("[search admin] Meilisearch test: q={}, makerCode={}, priceMin={}, priceMax={}", 
            q, makerCode, priceMin, priceMax);
        try {
            Map<String, Object> queryParams = new HashMap<>();
            if (q != null) queryParams.put("q", q);
            if (makerCode != null) queryParams.put("makerCode", makerCode);
            if (priceMin != null) queryParams.put("priceMin", priceMin);
            if (priceMax != null) queryParams.put("priceMax", priceMax);
            if (sort != null) queryParams.put("sort", sort);
            queryParams.put("page", page);
            queryParams.put("size", size);
            
            Map<String, Object> result = indexingService.getCarSearchService().search(queryParams);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[search admin] search test failed", e);
            return ApiResponse.error("검색 테스트 실패: " + e.getMessage());
        }
    }

    @PostMapping("/dsl")
    @Operation(summary = "Elasticsearch DSL 직접 실행", description = "Admin에서 Raw DSL(JSON)을 그대로 전달해 cars 인덱스에 검색 요청합니다.")
    public ApiResponse<Map<String, Object>> runDsl(@RequestBody Map<String, Object> dsl) {
        String runId = apiRunRecorder.recordStart("/admin/search/dsl", "POST", "search-dsl");
        try {
            if (dsl == null || dsl.isEmpty()) {
                apiRunRecorder.recordFail(runId, 0, "dsl body empty");
                return ApiResponse.error("DSL body가 비어 있습니다.");
            }

            Request req = new Request("POST", "/cars/_search");
            req.setJsonEntity(objectMapper.writeValueAsString(dsl));

            Response response = restClient.performRequest(req);
            String raw = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(raw, Map.class);

            int totalItems = 0;
            Object hitsObj = result.get("hits");
            if (hitsObj instanceof Map<?, ?> hitsMap) {
                Object totalObj = hitsMap.get("total");
                if (totalObj instanceof Map<?, ?> totalMap) {
                    Object valueObj = totalMap.get("value");
                    if (valueObj instanceof Number n) totalItems = n.intValue();
                }
            }
            apiRunRecorder.recordSuccess(runId, totalItems, Map.of("totalHits", totalItems));
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[search admin] dsl failed", e);
            apiRunRecorder.recordFail(runId, 0, e);
            return ApiResponse.error("DSL 실행 실패: " + e.getMessage());
        }
    }

    @GetMapping("/status")
    @Operation(summary = "검색 인덱싱 현황", description = "현재 인덱스 건수와 최근 인덱싱 작업 정보를 조회합니다.")
    public ApiResponse<Map<String, Object>> getIndexStatus() {
        try {
            Map<String, Object> result = new HashMap<>();
            long indexCount;
            try {
                indexCount = indexingService.getCarSearchService().count();
            } catch (Exception e) {
                log.warn("[search admin] index count failed: {}", e.getMessage());
                indexCount = 0L;
            }
            result.put("indexCount", indexCount);

            List<Map<String, Object>> recentJobs;
            try {
                recentJobs = jdbc.queryForList("""
                    SELECT execution_id, job_id, status, started_at, ended_at, duration_ms,
                           COALESCE(processed_items, 0) AS processed_items,
                           COALESCE(success_items, 0) AS success_items,
                           COALESCE(failed_items, 0) AS failed_items
                    FROM batch_job_execution
                    WHERE job_id IN ('indexing_incremental', 'indexing_batch', 'indexing_reindex')
                    ORDER BY started_at DESC
                    LIMIT 10
                    """);
            } catch (BadSqlGrammarException e) {
                log.warn("[search admin] batch_job_execution table missing, recentJobs empty");
                recentJobs = List.of();
            }
            result.put("recentJobs", recentJobs);
            if (!recentJobs.isEmpty()) {
                result.put("latestJob", recentJobs.get(0));
            } else {
                result.put("latestJob", null);
            }

            List<Map<String, Object>> recentApiRuns = jdbc.queryForList("""
                SELECT id, run_id, source, status, total_items, started_at, ended_at, duration_ms, message
                FROM api_run
                WHERE source IN (
                    'search-reindex', 'search-incremental', 'search-batch', 'search-sync', 'search-dsl',
                    'indexing_incremental', 'indexing_batch', 'indexing_reindex'
                )
                ORDER BY started_at DESC
                LIMIT 20
                """);
            result.put("recentApiRuns", recentApiRuns);
            result.put("latestApiRun", recentApiRuns.isEmpty() ? null : recentApiRuns.get(0));
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[search admin] status fetch failed", e);
            return ApiResponse.error("검색 인덱싱 현황 조회 실패: " + e.getMessage());
        }
    }
}

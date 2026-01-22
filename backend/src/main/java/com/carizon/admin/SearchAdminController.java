package com.carizon.admin;

import com.carizon.common.dto.ApiResponse;
import com.carizon.search.service.CarIndexingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 검색 인덱스 관리 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/admin/search")
@RequiredArgsConstructor
@Tag(name = "검색 관리", description = "Meilisearch 인덱스 관리 API")
public class SearchAdminController {

    private final CarIndexingService indexingService;

    @PostMapping("/reindex")
    @Operation(summary = "전체 차량 재인덱싱", description = "모든 차량 데이터를 Meilisearch에 재인덱싱합니다. (기존 인덱스 삭제 후 재생성)")
    public ApiResponse<String> reindexAll() {
        log.info("[검색관리] 전체 재인덱싱 시작");
        try {
            indexingService.reindexAllCars();
            return ApiResponse.success("재인덱싱 완료");
        } catch (Exception e) {
            log.error("[검색관리] 재인덱싱 실패", e);
            return ApiResponse.error("재인덱싱 실패: " + e.getMessage());
        }
    }

    @PostMapping("/incremental")
    @Operation(summary = "증분 인덱싱", description = "지정된 시간 이후에 업데이트된 차량만 인덱싱합니다.")
    public ApiResponse<Map<String, Object>> incrementalIndex(
            @RequestParam(required = false) String since) {
        log.info("[검색관리] 증분 인덱싱 시작: since={}", since);
        try {
            LocalDateTime sinceTime = since != null && !since.isEmpty() 
                ? LocalDateTime.parse(since) 
                : LocalDateTime.now().minusHours(1); // 기본값: 1시간 전
            
            int count = indexingService.incrementalIndex(sinceTime);
            return ApiResponse.success(Map.of(
                "message", "증분 인덱싱 완료",
                "indexedCount", count,
                "since", sinceTime.toString()
            ));
        } catch (Exception e) {
            log.error("[검색관리] 증분 인덱싱 실패", e);
            return ApiResponse.error("증분 인덱싱 실패: " + e.getMessage());
        }
    }

    @PostMapping("/batch")
    @Operation(summary = "배치 인덱싱", description = "지정된 개수만큼 차량을 인덱싱합니다. (스케줄러용)")
    public ApiResponse<Map<String, Object>> batchIndex(
            @RequestParam(defaultValue = "1000") int limit) {
        log.info("[검색관리] 배치 인덱싱 시작: limit={}", limit);
        try {
            int count = indexingService.batchIndex(limit);
            return ApiResponse.success(Map.of(
                "message", "배치 인덱싱 완료",
                "indexedCount", count,
                "requestedLimit", limit
            ));
        } catch (Exception e) {
            log.error("[검색관리] 배치 인덱싱 실패", e);
            return ApiResponse.error("배치 인덱싱 실패: " + e.getMessage());
        }
    }

    @PostMapping("/test")
    @Operation(summary = "Meilisearch 검색 테스트", description = "Meilisearch 검색 API를 테스트합니다.")
    public ApiResponse<Map<String, Object>> testSearch(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String makerCode,
            @RequestParam(required = false) Integer priceMin,
            @RequestParam(required = false) Integer priceMax,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("[검색관리] Meilisearch 테스트: q={}, makerCode={}, priceMin={}, priceMax={}", 
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
            
            Map<String, Object> result = indexingService.getMeilisearchService().search(queryParams);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[검색관리] 검색 테스트 실패", e);
            return ApiResponse.error("검색 테스트 실패: " + e.getMessage());
        }
    }
}

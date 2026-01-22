package com.carizon.admin;

import com.carizon.common.dto.ApiResponse;
import com.carizon.rag.service.ChromaVectorStoreService;
import com.carizon.search.service.MeilisearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자 대시보드 컨트롤러
 * 전체 시스템 현황 및 통계 조회
 */
@Slf4j
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "관리자 대시보드", description = "시스템 현황 및 통계 조회")
public class AdminDashboardController {

    private final JdbcTemplate jdbc;
    private final ChromaVectorStoreService vectorStoreService;
    private final MeilisearchService meilisearchService;

    @GetMapping("/stats")
    @Operation(summary = "시스템 통계", description = "전체 시스템 현황 통계 조회")
    public ApiResponse<Map<String, Object>> getStats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            
            // 차량 마스터 통계 (조건 완화: adv_status가 NULL이 아니거나 'ONSALE'인 경우)
            Long carMasterCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM car_master WHERE (adv_status = 'ONSALE' OR adv_status IS NULL)", Long.class);
            if (carMasterCount == null) carMasterCount = 0L;
            stats.put("carMasterCount", carMasterCount);
            
            // 플랫폼 차량 통계 (조건 완화: status가 NULL이거나 'ONSALE'이거나 ENCAR의 경우 'ADVERTISE'인 경우)
            Long platformCarCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM platform_car WHERE (status = 'ONSALE' OR status IS NULL OR (platform_name = 'ENCAR' AND status = 'ADVERTISE'))", Long.class);
            if (platformCarCount == null) platformCarCount = 0L;
            stats.put("platformCarCount", platformCarCount);
            
            // 플랫폼별 통계
            List<Map<String, Object>> platformStats = jdbc.queryForList("""
                SELECT platform_name, COUNT(*) as count
                FROM platform_car
                WHERE (status = 'ONSALE' OR status IS NULL OR (platform_name = 'ENCAR' AND status = 'ADVERTISE'))
                GROUP BY platform_name
                ORDER BY count DESC
                """);
            stats.put("platformStats", platformStats);
            
            // 최근 크롤링 현황
            List<Map<String, Object>> recentCrawls = jdbc.queryForList("""
                SELECT source, status, total_items, started_at, ended_at
                FROM crawl_run
                ORDER BY started_at DESC
                LIMIT 10
                """);
            stats.put("recentCrawls", recentCrawls);
            
            // Meilisearch 인덱스 개수
            try {
                // Meilisearch는 검색으로 개수 확인 (간접적)
                Map<String, Object> searchResult = meilisearchService.search(new HashMap<>());
                Object totalElements = searchResult.get("totalElements");
                stats.put("meilisearchCount", totalElements != null ? totalElements : 0);
            } catch (Exception e) {
                log.warn("Meilisearch 통계 조회 실패", e);
                stats.put("meilisearchCount", 0);
            }
            
            // Chroma 임베딩 개수
            try {
                // getCollectionCount 직접 사용 (더 안정적)
                long count = vectorStoreService.getCollectionCount();
                stats.put("embeddingCount", count);
            } catch (Exception e) {
                // Chroma 서비스가 없거나 컬렉션이 없는 경우 정상적으로 처리
                log.debug("Chroma 통계 조회 실패 (무시 가능): {}", e.getMessage());
                stats.put("embeddingCount", 0);
            }
            
            return ApiResponse.success(stats);
        } catch (Exception e) {
            log.error("[대시보드] 통계 조회 실패", e);
            return ApiResponse.error("통계 조회 실패: " + e.getMessage());
        }
    }
}

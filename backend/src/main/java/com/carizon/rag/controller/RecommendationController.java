package com.carizon.rag.controller;

import com.carizon.common.dto.ApiResponse;
import com.carizon.notification.VisitorNotificationService;
import com.carizon.rag.dto.RecommendationRequest;
import com.carizon.rag.dto.RecommendationResponse;
import com.carizon.rag.service.CarRecommendationService;
import com.carizon.rag.service.SearchFallbackRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 차량 추천 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
@Tag(name = "차량 추천", description = "팩터 추출 + 검색엔진 기반 차량 추천 API")
public class RecommendationController {

    private final CarRecommendationService recommendationService;
    private final SearchFallbackRecommendationService searchFallbackRecommendationService;
    private final VisitorNotificationService visitorNotificationService;

    @PostMapping
    @Operation(summary = "차량 추천", description = "사용자 요구사항에서 팩터를 추출해 검색엔진 결과를 추천 목록으로 반환합니다")
    public ResponseEntity<ApiResponse<RecommendationResponse>> recommendCars(
            @RequestBody RecommendationRequest request,
            HttpServletRequest httpRequest) throws Exception {
        RecommendationResponse response = recommendationService.recommendCars(request);
        notifyRecommendation(request, response, httpRequest);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/search-fallback")
    @Operation(summary = "검색 fallback 추천", description = "검색 결과 0건일 때 텍스트 기반 조건으로 재검색해 매물 목록을 반환합니다 (AI 추천 API와 분리).")
    public ResponseEntity<ApiResponse<RecommendationResponse>> recommendCarsForSearchFallback(
            @RequestBody RecommendationRequest request,
            HttpServletRequest httpRequest) throws Exception {
        RecommendationResponse response = searchFallbackRecommendationService.recommendFromText(request);
        notifyRecommendation(request, response, httpRequest);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/health")
    @Operation(summary = "헬스 체크", description = "RAG 서비스 상태 확인")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(ApiResponse.success("RAG service is running"));
    }

    private void notifyRecommendation(RecommendationRequest request, RecommendationResponse response, HttpServletRequest httpRequest) {
        try {
            String ip = VisitorNotificationService.extractClientIp(httpRequest);
            String ua = httpRequest.getHeader("User-Agent");
            StringBuilder details = new StringBuilder();
            if (request.getQuery() != null && !request.getQuery().isBlank()) {
                details.append("🔍 쿼리: `").append(request.getQuery()).append("`\n");
            }
            if (response.getCars() != null && !response.getCars().isEmpty()) {
                details.append(String.format("🚗 추천 %d대:\n", response.getCars().size()));
                response.getCars().stream().limit(3).forEach(car -> {
                    Object price = car.getPrice();
                    String priceStr = price != null ? String.format("%,d만원", ((Number) price).intValue()) : "-";
                    details.append(String.format("  • %s %s %s년식 %s\n",
                            car.getMaker(), car.getModel(), car.getYear(), priceStr));
                });
            }
            visitorNotificationService.notifyUserAction(ip, ua, "🤖", "AI 추천 요청", details.toString().trim());
        } catch (Exception e) {
            log.warn("[slack-notify] recommendation notify error: {}", e.getMessage());
        }
    }
}

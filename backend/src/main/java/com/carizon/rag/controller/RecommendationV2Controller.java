package com.carizon.rag.controller;

import com.carizon.common.dto.ApiResponse;
import com.carizon.notification.VisitorNotificationService;
import com.carizon.rag.dto.RecommendationRequest;
import com.carizon.rag.dto.RecommendationResponse;
import com.carizon.rag.dto.RecommendationV2Response;
import com.carizon.rag.service.RecommendationV2Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/recommendations")
@Tag(name = "차량 추천 v2", description = "모델 임베딩 + 하이브리드 retrieval 기반 추천 API")
public class RecommendationV2Controller {

    private final RecommendationV2Service recommendationV2Service;
    private final VisitorNotificationService visitorNotificationService;

    @PostMapping
    @Operation(summary = "차량 추천 v2", description = "자연어를 하드필터/소프트선호로 분리해 하이브리드 추천을 수행합니다.")
    public ResponseEntity<ApiResponse<RecommendationV2Response>> recommendV2(
            @RequestBody RecommendationRequest request,
            HttpServletRequest httpRequest
    ) throws Exception {
        RecommendationV2Response response = recommendationV2Service.recommend(request);
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
            visitorNotificationService.notifyUserAction(ip, ua, "🤖", "AI 추천 요청 v2", details.toString().trim());
        } catch (Exception e) {
            log.warn("[slack-notify] v2 recommendation notify error: {}", e.getMessage());
        }
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

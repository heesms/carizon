package com.carizon.recommendation.controller;

import com.carizon.common.dto.ApiResponse;
import com.carizon.notification.VisitorNotificationService;
import com.carizon.recommendation.dto.WeeklyBestCarDto;
import com.carizon.recommendation.service.BlogPostService;
import com.carizon.recommendation.service.WeeklyBestCarRankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 주간 Best 매물 추천 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/recommendation/weekly-best")
@RequiredArgsConstructor
@Tag(name = "주간 Best 매물", description = "주간 Best 매물 추천 API")
public class WeeklyBestCarController {

    private final WeeklyBestCarRankingService rankingService;
    private final BlogPostService blogPostService;
    private final VisitorNotificationService visitorNotificationService;

    @GetMapping("/models/{modelCode}")
    @Operation(summary = "모델별 주간 Best 매물 조회",
               description = "특정 모델의 주간 Best 매물 순위를 조회합니다.")
    public ApiResponse<List<WeeklyBestCarDto>> getWeeklyBestByModel(
            @PathVariable String modelCode,
            @RequestParam(defaultValue = "10") int limit,
            HttpServletRequest request) {

        log.info("[weekly Best] by model: modelCode={}, limit={}", modelCode, limit);

        List<WeeklyBestCarDto> bestCars = rankingService.getWeeklyBestCars(modelCode, null, limit);

        String ip = VisitorNotificationService.extractClientIp(request);
        String ua = request.getHeader("User-Agent");
        visitorNotificationService.notifyUserAction(ip, ua, "📊", "AI 랭킹 조회",
                String.format("🏷️ 모델: %s\n📦 결과: %d대", modelCode, bestCars.size()));

        return ApiResponse.success(bestCars);
    }

    @GetMapping("/all")
    @Operation(summary = "전체 주간 Best 매물 조회",
               description = "전체 모델을 대상으로 주간 Best 매물 순위를 조회합니다.")
    public ApiResponse<List<WeeklyBestCarDto>> getWeeklyBestAll(
            @RequestParam(defaultValue = "20") int limit,
            HttpServletRequest request) {

        log.info("[weekly Best] all: limit={}", limit);

        List<WeeklyBestCarDto> bestCars = rankingService.getWeeklyBestCars(null, null, limit);

        String ip = VisitorNotificationService.extractClientIp(request);
        String ua = request.getHeader("User-Agent");
        visitorNotificationService.notifyUserAction(ip, ua, "📊", "AI 랭킹 전체 조회",
                String.format("📦 결과: %d대", bestCars.size()));

        return ApiResponse.success(bestCars);
    }

    @GetMapping("/blog-content/{modelCode}")
    @Operation(summary = "블로그 포스팅 내용 생성",
               description = "특정 모델의 주간 Best 매물을 기반으로 블로그 포스팅 내용을 생성합니다.")
    public ApiResponse<Map<String, String>> generateBlogContent(
            @PathVariable String modelCode,
            @RequestParam(defaultValue = "10") int limit) {

        log.info("[blog post] content gen: modelCode={}, limit={}", modelCode, limit);

        List<WeeklyBestCarDto> bestCars = rankingService.getWeeklyBestCars(modelCode, null, limit);

        if (bestCars.isEmpty()) {
            return ApiResponse.error("매물이 없습니다.");
        }

        // 모델명 조회 (첫 번째 매물에서)
        String modelName = bestCars.get(0).getModelName();
        if (modelName == null || modelName.isEmpty()) {
            modelName = "중고차";
        }

        String title = blogPostService.generateBlogPostTitle(modelName);
        String content = blogPostService.generateBlogPostContent(modelCode, modelName, null, null, bestCars);

        return ApiResponse.success(Map.of(
                "title", title,
                "content", content
        ));
    }
}

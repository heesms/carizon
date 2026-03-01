package com.carizon.recommendation.controller;

import com.carizon.common.dto.ApiResponse;
import com.carizon.notification.VisitorNotificationService;
import com.carizon.recommendation.dto.WeeklyBestCarDto;
import com.carizon.recommendation.service.BlogPostService;
import com.carizon.recommendation.service.WeeklyBestHomeSnapshotService;
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
    private final WeeklyBestHomeSnapshotService homeSnapshotService;
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

        String ip = VisitorNotificationService.extractClientIp(request);
        String ua = request.getHeader("User-Agent");
        String referer = request.getHeader("Referer");
        log.warn("[weekly Best] deprecated /all called. ip={}, referer={}, userAgent={}, limit={}", ip, referer, ua, limit);
        log.warn("[weekly Best] /all is deprecated and now returns snapshot data for backward compatibility");

        List<WeeklyBestCarDto> bestCars = homeSnapshotService.getHomeSnapshot(Math.max(1, Math.min(limit, 100)));

        return ApiResponse.success(bestCars);
    }

    @GetMapping("/all/trace")
    @Operation(summary = "전체 주간 Best 매물 조회(추적)",
               description = "full all 조회 사용 추적용 엔드포인트. 호출 주체 로그를 남김.")
    public ApiResponse<List<WeeklyBestCarDto>> getWeeklyBestAllTrace(
            @RequestParam(defaultValue = "20") int limit,
            jakarta.servlet.http.HttpServletRequest request) {
        String ip = com.carizon.notification.VisitorNotificationService.extractClientIp(request);
        String ua = request.getHeader("User-Agent");
        log.warn("[weekly-best-trace] /all was called by ip={}, ua={}, limit={}", ip, ua, limit);
        List<WeeklyBestCarDto> bestCars = rankingService.getWeeklyBestCars(null, null, limit);
        return ApiResponse.success(bestCars);
    }

    @GetMapping("/home")
    @Operation(summary = "메인 페이지용 주간 Best 매물 조회",
               description = "ES 스냅샷(6시간 주기 갱신) 기반으로 메인 노출 목록을 제공합니다.")
    public ApiResponse<List<WeeklyBestCarDto>> getHomeWeeklyBest(
            @RequestParam(defaultValue = "20") int limit) {

        log.info("[weekly Best] home snapshot all: limit={}", limit);

        List<WeeklyBestCarDto> bestCars = homeSnapshotService.getHomeSnapshot(limit);
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

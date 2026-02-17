package com.carizon.admin;

import com.carizon.common.dto.ApiResponse;
import com.carizon.recommendation.dto.WeeklyBestCarDto;
import com.carizon.recommendation.service.BlogPostService;
import com.carizon.recommendation.service.WeeklyBestCarBatchService;
import com.carizon.recommendation.service.WeeklyBestCarRankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 주간 Best 매물 관리자 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/admin/recommendation/weekly-best")
@RequiredArgsConstructor
@Tag(name = "주간 Best 매물 관리", description = "주간 Best 매물 선정 및 블로그 포스팅 관리")
public class WeeklyBestAdminController {

    private final WeeklyBestCarRankingService rankingService;
    private final BlogPostService blogPostService;
    private final WeeklyBestCarBatchService batchService;

    @PostMapping("/batch/run")
    @Operation(summary = "주간 Best 매물 배치 작업 실행", 
               description = "주간 Best 매물 선정 및 블로그 포스팅 내용 생성 배치 작업을 수동 실행합니다.")
    public ApiResponse<String> runBatch() {
        try {
            log.info("[admin] weekly Best batch job manual run");
            batchService.runManually();
            return ApiResponse.success("배치 작업 실행 완료");
        } catch (Exception e) {
            log.error("[admin] weekly Best batch run failed", e);
            return ApiResponse.error("배치 작업 실행 실패: " + e.getMessage());
        }
    }

    @PostMapping("/model/{modelCode}/generate")
    @Operation(summary = "특정 모델 Best 매물 포스팅 생성", 
               description = "특정 모델의 Best 매물을 선정하고 블로그 포스팅 내용을 생성합니다. 트림코드는 선택사항입니다.")
    public ApiResponse<Map<String, String>> generateModelPost(
            @PathVariable String modelCode,
            @RequestParam(required = false) String trimCode,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            log.info("[admin] model {} trim {} Best listing post create", modelCode, trimCode);
            
            List<WeeklyBestCarDto> bestCars = rankingService.getWeeklyBestCars(modelCode, trimCode, limit);
            
            if (bestCars.isEmpty()) {
                return ApiResponse.error("매물이 없습니다. 해당 모델의 platform_car가 car_master와 연결(car_id)되어 있어야 합니다. merge 파이프라인 실행 후 postProcess(linkToMaster)가 완료되었는지 확인해 보세요.");
            }

            String modelName = bestCars.get(0).getModelName();
            if (modelName == null || modelName.isEmpty()) {
                modelName = "중고차";
            }
            
            String trimName = (trimCode != null && !trimCode.trim().isEmpty() && bestCars.get(0).getTrimName() != null) 
                    ? bestCars.get(0).getTrimName() : null;

            String title = blogPostService.generateBlogPostTitleWithTrim(modelName, trimName);
            String content = blogPostService.generateBlogPostContent(modelCode, modelName, trimCode, trimName, bestCars);

            return ApiResponse.success(Map.of(
                    "title", title,
                    "content", content,
                    "carCount", String.valueOf(bestCars.size())
            ));
        } catch (Exception e) {
            log.error("[admin] post create failed", e);
            return ApiResponse.error("포스팅 생성 실패: " + e.getMessage());
        }
    }

    @PostMapping("/model/{modelCode}/post-to-wordpress")
    @Operation(summary = "모델코드로 WordPress 포스팅 생성", 
               description = "모델코드를 입력받아 Best 매물을 선정하고 WordPress에 바로 포스팅합니다. 트림코드는 선택사항입니다.")
    public ApiResponse<Map<String, Object>> postToWordPress(
            @PathVariable String modelCode,
            @RequestParam(required = false) String trimCode,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "draft") String status) {
        try {
            log.info("[admin] model {} trim {} WordPress post start", modelCode, trimCode);
            
            // Best 매물 선정
            List<WeeklyBestCarDto> bestCars = rankingService.getWeeklyBestCars(modelCode, trimCode, limit);
            
            if (bestCars.isEmpty()) {
                return ApiResponse.error("매물이 없습니다. merge 및 linkToMaster 실행 후 다시 시도해 보세요.");
            }

            String modelName = bestCars.get(0).getModelName();
            if (modelName == null || modelName.isEmpty()) {
                modelName = "중고차";
            }
            
            String trimName = (trimCode != null && !trimCode.trim().isEmpty() && bestCars.get(0).getTrimName() != null) 
                    ? bestCars.get(0).getTrimName() : null;

            // 블로그 포스팅 내용 생성
            String title = blogPostService.generateBlogPostTitle(modelName, trimName);
            String content = blogPostService.generateBlogPostContent(modelCode, modelName, trimCode, trimName, bestCars);

            // WordPress에 포스팅
            Long postId = blogPostService.postToWordPress(title, content, status);

            return ApiResponse.success(Map.of(
                    "postId", postId,
                    "title", title,
                    "status", status,
                    "carCount", bestCars.size(),
                    "message", "WordPress 포스팅이 성공적으로 생성되었습니다."
            ));
        } catch (Exception e) {
            log.error("[admin] WordPress post failed", e);
            return ApiResponse.error("WordPress 포스팅 실패: " + e.getMessage());
        }
    }

    @GetMapping("/model/{modelCode}")
    @Operation(summary = "특정 모델 Best 매물 조회", 
               description = "특정 모델의 Best 매물 순위를 조회합니다. 트림코드는 선택사항입니다.")
    public ApiResponse<List<WeeklyBestCarDto>> getModelBestCars(
            @PathVariable String modelCode,
            @RequestParam(required = false) String trimCode,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            List<WeeklyBestCarDto> bestCars = rankingService.getWeeklyBestCars(modelCode, trimCode, limit);
            if (bestCars.isEmpty()) {
                return ApiResponse.error("매물이 없습니다. merge 및 linkToMaster 실행 후 다시 시도해 보세요.");
            }
            return ApiResponse.success(bestCars);
        } catch (Exception e) {
            log.error("[admin] Best listing fetch failed", e);
            return ApiResponse.error("조회 실패: " + e.getMessage());
        }
    }
}

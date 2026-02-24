package com.carizon.recommendation.service;

import com.carizon.recommendation.dto.WeeklyBestCarDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 주간 Best 매물 배치 작업 서비스
 * 주간마다 Best 매물을 선정하고 블로그 포스팅 내용을 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyBestCarBatchService {

    private final WeeklyBestCarRankingService rankingService;
    private final BlogPostService blogPostService;
    private final JdbcTemplate jdbc;

    /**
     * 랭킹 배치 실행 (매일 새벽 4시 1회 실행)
     * TODO: 실제 워드프레스 포스팅은 BlogPostService.postToWordPress() 구현 후 활성화
     */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void generateWeeklyBestPosts() {
        log.info("[weekly Best batch] weekly Best selection and blog post gen start");

        try {
            // 1. 인기 모델 목록 조회 (최근 7일간 매물이 10개 이상인 모델)
            List<String> popularModels = getPopularModels(10);

            log.info("[weekly Best batch] {} popular models found", popularModels.size());

            // 2. 각 모델별로 Best 매물 선정 및 블로그 포스팅 내용 생성
            for (String modelCode : popularModels) {
                try {
                    generateModelBestPost(modelCode, 10);
                } catch (Exception e) {
                    log.error("[weekly Best batch] model {} process failed", modelCode, e);
                    // 개별 모델 실패해도 계속 진행
                }
            }

            // 배치 완료 후 캐시 초기화
            rankingService.evictCache();
            log.info("[weekly Best batch] done");
        } catch (Exception e) {
            log.error("[weekly Best batch] full failed", e);
        }
    }

    /**
     * 특정 모델의 Best 매물 포스팅 생성
     */
    public void generateModelBestPost(String modelCode, int limit) {
        log.info("[weekly Best batch] model {} process start", modelCode);

        // Best 매물 선정
        List<WeeklyBestCarDto> bestCars = rankingService.getWeeklyBestCars(modelCode, null, limit);

        if (bestCars.isEmpty()) {
            log.warn("[weekly Best batch] model {} no listings", modelCode);
            return;
        }

        // 모델명 조회
        String modelName = bestCars.get(0).getModelName();
        if (modelName == null || modelName.isEmpty()) {
            modelName = "중고차";
        }

        // 블로그 포스팅 내용 생성
        String title = blogPostService.generateBlogPostTitle(modelName);
        String content = blogPostService.generateBlogPostContent(modelCode, modelName, null, null, bestCars);

        // TODO: 워드프레스에 포스팅
        // blogPostService.postToWordPress(title, content);

        // 임시: 생성된 내용을 로그로 출력 (실제 구현 시 제거)
        log.info("[weekly Best batch] model {} post gen done", modelCode);
        log.debug("[weekly Best batch] title: {}", title);
        log.debug("[weekly Best batch] content length: {} bytes", content.length());

        // TODO: 생성된 포스팅 내용을 DB에 저장하거나 파일로 저장
        // saveBlogPostToDatabase(modelCode, title, content);
    }

    /**
     * 인기 모델 목록 조회 (최근 7일간 매물이 N개 이상인 모델)
     */
    private List<String> getPopularModels(int minCount) {
        String sql = """
            SELECT cm.model_code, COUNT(*) AS car_count
            FROM car_master cm
            INNER JOIN platform_car pc ON pc.car_id = cm.car_id
            WHERE cm.adv_status = 'ONSALE'
              AND (
                pc.status = 'ONSALE'
                OR (pc.platform_name = 'ENCAR' AND pc.status = 'ADVERTISE')
              )
              AND pc.last_seen_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
              AND cm.model_code IS NOT NULL
            GROUP BY cm.model_code
            HAVING COUNT(*) >= ?
            ORDER BY car_count DESC
            LIMIT 50
        """;

        return jdbc.queryForList(sql, String.class, minCount);
    }

    /**
     * 수동 실행용 메서드
     */
    public void runManually() {
        log.info("[weekly Best batch] manual run start");
        generateWeeklyBestPosts();
    }
}

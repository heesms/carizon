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
     * 주간 Best 매물 선정 및 블로그 포스팅 내용 생성 (매주 월요일 오전 9시 실행)
     * TODO: 실제 워드프레스 포스팅은 BlogPostService.postToWordPress() 구현 후 활성화
     */
    @Scheduled(cron = "0 0 9 * * MON", zone = "Asia/Seoul")
    public void generateWeeklyBestPosts() {
        log.info("[주간 Best 배치] 주간 Best 매물 선정 및 블로그 포스팅 생성 시작");

        try {
            // 1. 인기 모델 목록 조회 (최근 7일간 매물이 10개 이상인 모델)
            List<String> popularModels = getPopularModels(10);

            log.info("[주간 Best 배치] 인기 모델 {}개 발견", popularModels.size());

            // 2. 각 모델별로 Best 매물 선정 및 블로그 포스팅 내용 생성
            for (String modelCode : popularModels) {
                try {
                    generateModelBestPost(modelCode, 10);
                } catch (Exception e) {
                    log.error("[주간 Best 배치] 모델 {} 처리 실패", modelCode, e);
                    // 개별 모델 실패해도 계속 진행
                }
            }

            log.info("[주간 Best 배치] 완료");
        } catch (Exception e) {
            log.error("[주간 Best 배치] 전체 실패", e);
        }
    }

    /**
     * 특정 모델의 Best 매물 포스팅 생성
     */
    public void generateModelBestPost(String modelCode, int limit) {
        log.info("[주간 Best 배치] 모델 {} 처리 시작", modelCode);

        // Best 매물 선정
        List<WeeklyBestCarDto> bestCars = rankingService.getWeeklyBestCars(modelCode, null, limit);

        if (bestCars.isEmpty()) {
            log.warn("[주간 Best 배치] 모델 {} 매물 없음", modelCode);
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
        log.info("[주간 Best 배치] 모델 {} 포스팅 생성 완료", modelCode);
        log.debug("[주간 Best 배치] 제목: {}", title);
        log.debug("[주간 Best 배치] 내용 길이: {} bytes", content.length());

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
        log.info("[주간 Best 배치] 수동 실행 시작");
        generateWeeklyBestPosts();
    }
}

package com.carizon.recommendation.service;

import com.carizon.dto.ModelImageDto;
import com.carizon.recommendation.dto.WeeklyBestCarDto;
import com.carizon.service.ModelQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 블로그 포스팅 생성 서비스
 * 주간 Best 매물을 기반으로 블로그 포스팅 내용 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlogPostService {

    private final PlatformLinkService platformLinkService;
    private final WordPressService wordPressService;
    private final ModelQueryService modelQueryService;

    @Value("${blog.wordpress.api-url:}")
    private String wordPressApiUrl;

    @Value("${blog.wordpress.username:}")
    private String wordPressUsername;

    @Value("${blog.wordpress.password:}")
    private String wordPressPassword;

    /**
     * 주간 Best 매물 리스트를 기반으로 블로그 포스팅 내용 생성
     * 
     * @param modelCode 모델 코드
     * @param modelName 모델 이름
     * @param trimCode 트림 코드 (선택사항)
     * @param trimName 트림 이름 (선택사항)
     * @param bestCars Best 매물 리스트
     * @return 블로그 포스팅 HTML 내용
     */
    public String generateBlogPostContent(String modelCode, String modelName, String trimCode, String trimName, List<WeeklyBestCarDto> bestCars) {
        if (bestCars == null || bestCars.isEmpty()) {
            return "";
        }

        LocalDate now = LocalDate.now();
        
        StringBuilder content = new StringBuilder();

        // 모델 이미지 추가 (model_code로 조회)
        if (modelCode != null && !modelCode.isEmpty()) {
            List<ModelImageDto> modelImages = modelQueryService.images(modelCode);
            if (!modelImages.isEmpty()) {
                // 대표 이미지(is_main=true) 또는 첫 번째 이미지 사용
                ModelImageDto mainImage = modelImages.stream()
                    .filter(img -> img.isMain())
                    .findFirst()
                    .orElse(modelImages.get(0));
                
                if (mainImage.imageUrl() != null && !mainImage.imageUrl().isEmpty()) {
                    content.append("<figure style=\"text-align:center; margin:20px 0;\">\n");
                    content.append("<img src=\"").append(escapeHtml(mainImage.imageUrl())).append("\" ");
                    content.append("alt=\"").append(escapeHtml(modelName)).append("\" ");
                    content.append("style=\"max-width:100%; height:auto; border-radius:8px;\" />\n");
                    content.append("</figure>\n\n");
                }
            }
        }

        // 제목 및 소개
        String safeModelName = escapeHtml(modelName);
        
        // makerName 추출 (첫 번째 매물에서 가져오기)
        String makerName = bestCars.stream()
                .filter(car -> car.getMakerName() != null && !car.getMakerName().trim().isEmpty() && !"\\N".equals(car.getMakerName()))
                .map(car -> escapeHtml(car.getMakerName()))
                .findFirst()
                .orElse("");
        
        // 주차 정보 계산
        String weekInfo = getWeekInfo(now);
        
        // 트림 정보 포함 여부에 따라 제목 구성
        String trimDisplayName = "";
        if (trimName != null && !trimName.trim().isEmpty()) {
            trimDisplayName = " " + escapeHtml(trimName);
        }
        
        // 차종명 (제조사명 + 모델명 + 트림명)
        String carType = (makerName != null && !makerName.isEmpty() ? makerName + " " : "") + safeModelName + trimDisplayName;
        
        // 제목: {제조사명} {모델명} + (트림코드 입력시 트림명) + 중고차 매물 추천 | 실매물 주간 BEST TOP 10 ({year}년 {month}월 {week}주차)
        String title = String.format("%s 중고차 매물 추천 | 실매물 주간 BEST TOP 10 (%s)", 
                carType, weekInfo);
        content.append("<h2>").append(title).append("</h2>\n");
        
        // 주차 정보에서 "주차" 제거하여 "2026년 1월 3주" 형식으로 변환
        String weekInfoForH2 = weekInfo.replace("주차", "주");
        
        // h2 태그 1개 (두 내용을 한 줄로 합침)
        content.append("<h2>🔍 ").append(carType).append(" 중고차 매물 추천 📊 중고차 플랫폼별 매물 비교</h2>\n");
        
        // 서브 제목: 여러 중고차 플랫폼에 등록된 <strong>모델명 중고차</strong> 매물을 한 번에 비교해...
        content.append("<p>여러 중고차 플랫폼에 등록된 <strong>").append(safeModelName).append(" 중고차</strong> 매물을 한 번에 비교해, 가격·주행거리·연식·등록일 등 <strong>카리즌만의 객관적인 기준</strong>으로 가장 좋은 매물만 선별했습니다.</p>\n\n");

        // Best 매물 리스트 (h3로 변경)
        content.append("<h3>🏆 ").append(carType).append(" 주간 BEST 매물 TOP10 ").append(weekInfoForH2).append("</h3>\n");
        content.append("<ul style=\"list-style: none; padding-left: 0;\">\n");

        for (WeeklyBestCarDto car : bestCars) {
            content.append(generateCarItemHtml(car));
        }

        content.append("</ul>\n\n");

        // 마무리
        content.append("<h3>매물 선택 시 주의사항</h3>\n");
        content.append("<ul>\n");
        content.append("<li>실제 매물 확인은 각 플랫폼에서 직접 확인하시기 바랍니다.</li>\n");
        content.append("<li>가격 및 매물 정보는 변동될 수 있습니다.</li>\n");
        content.append("<li>구매 전 반드시 실차 확인 및 시승을 권장합니다.</li>\n");
        content.append("</ul>\n");

        return content.toString();
    }

    /**
     * 개별 매물 HTML 생성
     */
    private String generateCarItemHtml(WeeklyBestCarDto car) {
        StringBuilder html = new StringBuilder();
        html.append("<li>\n");
        
        // 순위를 정수로 표시 (1.1위 -> 1위)
        int rank = car.getRank() != null ? car.getRank().intValue() : 0;

        // 차량명 (내부 상세 링크)
        String carName = buildCarName(car);
        Long carId = car.getCarId();

        html.append("<h4>");
        if (carId != null) {
            html.append("<a href=\"/cars/").append(carId).append("\" ");
            html.append("data-car-id=\"").append(carId).append("\" ");
            html.append("style=\"cursor: pointer; text-decoration: underline; color: inherit;\" ");
            html.append(">");
            html.append(rank).append("위: ").append(carName);
            html.append("</a>");
        } else {
            html.append(rank).append("위: ").append(carName);
        }
        html.append("</h4>\n");

        // 매물 이미지 추가 (car_image_url 사용, 저작권 이슈로 흐리게 처리)
        // 이미지 클릭 시 내부 상세 링크로 이동
        String carImageUrl = car.getCarImageUrl();
        if (carImageUrl != null && !carImageUrl.isEmpty()) {
            html.append("<figure style=\"text-align:center; margin:10px 0; position:relative;\">\n");

            if (carId != null) {
                html.append("<a href=\"/cars/").append(carId).append("\" ");
                html.append("data-car-id=\"").append(carId).append("\" ");
                html.append("style=\"cursor: pointer; display: inline-block;\" ");
                html.append(">");
            }
            
            html.append("<img src=\"").append(escapeHtml(carImageUrl)).append("\" ");
            html.append("alt=\"").append(escapeHtml(carName)).append("\" ");
            html.append("style=\"max-width:300px; height:auto; border-radius:8px; ");
            html.append("filter: blur(2px) opacity(0.7); ");
            html.append("-webkit-filter: blur(2px) opacity(0.7); ");
            html.append("pointer-events: auto;\" />\n");
            
            if (carId != null) {
                html.append("</a>");
            }

            html.append("</figure>\n");
        }

        // 매물 정보 테이블 (모바일 반응형 - 타이틀 열 최소 2칸 보장)
        html.append("<style>\n");
        html.append("@media (max-width: 768px) {\n");
        html.append("  .car-spec-table td:first-child {\n");
        html.append("    min-width: 90px !important;\n");
        html.append("    width: 35% !important;\n");
        html.append("    white-space: nowrap;\n");
        html.append("  }\n");
        html.append("  .car-spec-table td:last-child {\n");
        html.append("    min-width: 120px !important;\n");
        html.append("    width: 65% !important;\n");
        html.append("  }\n");
        html.append("}\n");
        html.append("</style>\n");
        html.append("<table class=\"car-spec-table\" style=\"width:100%; border-collapse:collapse; margin:10px 0; table-layout:fixed;\">\n");
        html.append("<tbody>\n");
        
        if (car.getYear() != null) {
            html.append("<tr><td style=\"padding:8px 5px; border:1px solid #ddd; min-width:90px;\"><strong>연식</strong></td>")
                .append("<td style=\"padding:8px 5px; border:1px solid #ddd;\">").append(car.getYear()).append("년</td></tr>\n");
        }
        
        if (car.getMileage() != null) {
            html.append("<tr><td style=\"padding:8px 5px; border:1px solid #ddd; min-width:90px;\"><strong>주행거리</strong></td>")
                .append("<td style=\"padding:8px 5px; border:1px solid #ddd;\">")
                .append(String.format("%,d", car.getMileage())).append("km</td></tr>\n");
        }
        
        if (car.getPrice() != null) {
            html.append("<tr><td style=\"padding:8px 5px; border:1px solid #ddd; min-width:90px;\"><strong>가격</strong></td>")
                .append("<td style=\"padding:8px 5px; border:1px solid #ddd;\">")
                .append(String.format("%,d", car.getPrice())).append("만원</td></tr>\n");
        }
        
        if (car.getFuel() != null && !car.getFuel().trim().isEmpty() && !"\\N".equals(car.getFuel())) {
            html.append("<tr><td style=\"padding:8px 5px; border:1px solid #ddd; min-width:90px;\"><strong>연료</strong></td>")
                .append("<td style=\"padding:8px 5px; border:1px solid #ddd;\">").append(escapeHtml(car.getFuel())).append("</td></tr>\n");
        }
        
        if (car.getTransmission() != null && !car.getTransmission().trim().isEmpty() && !"\\N".equals(car.getTransmission())) {
            html.append("<tr><td style=\"padding:8px 5px; border:1px solid #ddd; min-width:90px;\"><strong>변속기</strong></td>")
                .append("<td style=\"padding:8px 5px; border:1px solid #ddd;\">").append(escapeHtml(car.getTransmission())).append("</td></tr>\n");
        }
        
        if (car.getRegion() != null && !car.getRegion().trim().isEmpty() && !"\\N".equals(car.getRegion())) {
            html.append("<tr><td style=\"padding:8px 5px; border:1px solid #ddd; min-width:90px;\"><strong>지역</strong></td>")
                .append("<td style=\"padding:8px 5px; border:1px solid #ddd;\">").append(escapeHtml(car.getRegion())).append("</td></tr>\n");
        }

        // 카리즌 스코어 정보 (선택적)
        if (car.getCarizonScore() != null) {
            html.append("<tr><td style=\"padding:8px 5px; border:1px solid #ddd; min-width:90px;\"><strong>카리즌 스코어</strong></td>")
                .append("<td style=\"padding:8px 5px; border:1px solid #ddd;\">")
                .append(car.getCarizonScore().setScale(1, java.math.RoundingMode.HALF_UP)).append("점</td></tr>\n");
            if (car.getCarizonScoreReason() != null && !car.getCarizonScoreReason().trim().isEmpty() && !"\\N".equals(car.getCarizonScoreReason())) {
                html.append("<tr><td style=\"padding:8px 5px; border:1px solid #ddd; min-width:90px;\"><strong>평가 사유</strong></td>")
                    .append("<td style=\"padding:8px 5px; border:1px solid #ddd;\">")
                    .append(escapeHtml(car.getCarizonScoreReason())).append("</td></tr>\n");
            }
        }

        html.append("</tbody>\n");
        html.append("</table>\n");

        // 내부 상세 링크
        if (carId != null) {
            html.append("<p><a href=\"/cars/").append(carId).append("\" ");
            html.append("data-car-id=\"").append(carId).append("\" ");
            html.append("style=\"color:#2563eb; font-weight:600; cursor:pointer; text-decoration:underline;\"");
            html.append(">🔍 상세보기</a></p>\n");
        }

        html.append("</li>\n");
        return html.toString();
    }

    /**
     * 매물 링크 가져오기 (User-Agent 기반으로 모바일/PC 구분)
     * 모바일 -> m_url, PC -> pc_url
     */
    private String getCarLink(WeeklyBestCarDto car) {
        // User-Agent는 블로그 포스팅에서는 사용할 수 없으므로
        // 기본적으로 PC URL을 사용하되, 없으면 모바일 URL 사용
        // 실제 클릭 시에는 PlatformLinkService에서 User-Agent 기반으로 처리됨
        if (car.getPcUrl() != null && !car.getPcUrl().isEmpty()) {
            return car.getPcUrl();
        } else if (car.getMUrl() != null && !car.getMUrl().isEmpty()) {
            return car.getMUrl();
        }
        return null;
    }
    
    /**
     * 차량명 조합
     */
    private String buildCarName(WeeklyBestCarDto car) {
        StringBuilder name = new StringBuilder();
        
        if (car.getMakerName() != null && !car.getMakerName().trim().isEmpty() && !"\\N".equals(car.getMakerName())) {
            name.append(escapeHtml(car.getMakerName())).append(" ");
        }
        if (car.getModelName() != null && !car.getModelName().trim().isEmpty() && !"\\N".equals(car.getModelName())) {
            name.append(escapeHtml(car.getModelName())).append(" ");
        }
        if (car.getTrimName() != null && !car.getTrimName().trim().isEmpty() && !"\\N".equals(car.getTrimName())) {
            name.append(escapeHtml(car.getTrimName())).append(" ");
        }
        if (car.getGradeName() != null && !car.getGradeName().trim().isEmpty() && !"\\N".equals(car.getGradeName())) {
            name.append(escapeHtml(car.getGradeName()));
        }

        return name.toString().trim();
    }

    /**
     * HTML 이스케이프 처리
     */
    private String escapeHtml(String text) {
        if (text == null || text.trim().isEmpty() || "\\N".equals(text) || "null".equalsIgnoreCase(text)) {
            return "";
        }
        // 먼저 리터럴 문자열 제거 (\\n, \\r, \\N)
        String cleaned = text
            .replace("\\N", "")
            .replace("\\n", " ")
            .replace("\\r", " ")
            .replace("\\t", " ");
        
        // 실제 개행 문자 제거
        cleaned = cleaned
            .replace("\n", " ")
            .replace("\r", " ")
            .replace("\t", " ");
        
        // HTML 특수 문자 이스케이프
        cleaned = cleaned
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
        
        // 연속된 공백을 하나로 정리
        cleaned = cleaned.replaceAll("\\s+", " ");
        
        return cleaned.trim();
    }

    /**
     * 주간 범위 문자열 생성 (예: "2024-01-01 ~ 2024-01-07")
     */
    private String getWeekRange(LocalDate date) {
        // 해당 주의 월요일과 일요일 계산
        int dayOfWeek = date.getDayOfWeek().getValue(); // 1=월요일, 7=일요일
        LocalDate monday = date.minusDays(dayOfWeek - 1);
        LocalDate sunday = monday.plusDays(6);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return monday.format(formatter) + " ~ " + sunday.format(formatter);
    }

    /**
     * 주차 정보 문자열 생성 (예: "2026년 1월 4주차")
     */
    private String getWeekInfo(LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        
        // 해당 월의 첫 번째 날
        LocalDate firstDayOfMonth = date.withDayOfMonth(1);
        
        // 해당 주의 월요일 계산
        int dayOfWeek = date.getDayOfWeek().getValue(); // 1=월요일, 7=일요일
        LocalDate monday = date.minusDays(dayOfWeek - 1);
        
        // 해당 월의 첫 번째 월요일 찾기
        LocalDate firstMonday = firstDayOfMonth;
        int firstDayOfWeek = firstDayOfMonth.getDayOfWeek().getValue();
        if (firstDayOfWeek != 1) {
            firstMonday = firstDayOfMonth.plusDays(8 - firstDayOfWeek);
        }
        
        // 주차 계산: (monday - firstMonday) / 7 + 1
        long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(firstMonday, monday);
        int week = (int) (daysDiff / 7) + 1;
        
        // 월의 마지막 날이 포함된 주는 다음 달 1주차로 처리하지 않도록 조정
        LocalDate lastDayOfMonth = date.withDayOfMonth(date.lengthOfMonth());
        LocalDate lastMonday = lastDayOfMonth;
        int lastDayOfWeek = lastDayOfMonth.getDayOfWeek().getValue();
        if (lastDayOfWeek != 1) {
            lastMonday = lastDayOfMonth.minusDays(lastDayOfWeek - 1);
        }
        
        // 마지막 주의 월요일보다 크면 다음 달 1주차로 처리
        if (monday.isAfter(lastMonday)) {
            week = 1;
            month++;
            if (month > 12) {
                month = 1;
                year++;
            }
        }
        
        return String.format("%d년 %d월 %d주차", year, month, week);
    }

    /**
     * 블로그 포스팅 제목 생성 (makerName 포함)
     */
    public String generateBlogPostTitle(String modelName, String makerName) {
        return generateBlogPostTitle(modelName, makerName, null);
    }
    
    /**
     * 블로그 포스팅 제목 생성 (makerName 없이)
     */
    public String generateBlogPostTitle(String modelName) {
        return generateBlogPostTitle(modelName, null, null);
    }
    
    /**
     * 블로그 포스팅 제목 생성 (트림명 포함)
     * 
     * @param modelName 모델 이름
     * @param trimName 트림 이름 (선택사항, null이면 모델명만 사용)
     * @return 블로그 포스팅 제목
     */
    public String generateBlogPostTitleWithTrim(String modelName, String trimName) {
        return generateBlogPostTitle(modelName, null, trimName);
    }
    
    /**
     * 블로그 포스팅 제목 생성 (내부 메서드 - makerName, trimName 모두 선택사항)
     */
    private String generateBlogPostTitle(String modelName, String makerName, String trimName) {
        LocalDate now = LocalDate.now();
        String weekInfo = getWeekInfo(now);
        String safeModelName = escapeHtml(modelName);
        String safeTrimName = (trimName != null && !trimName.trim().isEmpty()) ? " " + escapeHtml(trimName) : "";
        
        // 제목: {모델명} + (트림코드 입력시 트림명) + 중고차 매물 추천 | 실매물 주간 BEST TOP 10 ({year}년 {month}월 {week}주차)
        return String.format("%s%s 중고차 매물 추천 | 실매물 주간 BEST TOP 10 (%s)", 
                safeModelName, safeTrimName, weekInfo);
    }

    /**
     * 워드프레스에 포스팅
     * 
     * @param title 포스팅 제목
     * @param content 포스팅 내용 (HTML)
     * @param status 포스팅 상태 (draft, publish 등, 기본값: draft)
     * @return 생성된 포스팅 ID
     */
    public Long postToWordPress(String title, String content, String status) {
        try {
            log.info("[블로그 포스팅] WordPress 포스팅 시작: {}", title);
            Long postId = wordPressService.createPost(title, content, status);
            log.info("[블로그 포스팅] WordPress 포스팅 성공: postId={}", postId);
            return postId;
        } catch (Exception e) {
            log.error("[블로그 포스팅] WordPress 포스팅 실패", e);
            throw new RuntimeException("WordPress 포스팅 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 워드프레스에 포스팅 (기본 상태: draft)
     */
    public Long postToWordPress(String title, String content) {
        return postToWordPress(title, content, "draft");
    }
}

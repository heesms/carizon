package com.carizon.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 주간 Best 매물 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyBestCarDto {
    private Long carId;
    private Long platformCarId;
    private String platformName;
    
    // 차량 정보
    private String makerCode;
    private String makerName;
    private String modelGroupCode;
    private String modelGroupName;
    private String modelCode;
    private String modelName;
    private String trimCode;
    private String trimName;
    private String gradeCode;
    private String gradeName;
    
    // 매물 정보
    private Integer year;
    private Integer mileage;
    private Integer price;
    private String fuel;
    private String transmission;
    private String bodyType;
    private String region;
    private String status;
    
    // 링크 정보
    private String pcUrl;
    private String mUrl;
    private LocalDate lastSeenDate;
    private String carImageUrl; // 차량 이미지 URL
    
    // 스코어 정보
    private BigDecimal carizonScore; // 카리즌 스코어 (종합 점수)
    private BigDecimal priceScore;
    private BigDecimal mileageScore;
    private BigDecimal freshnessScore;
    private BigDecimal priceTrendScore;
    private BigDecimal popularityScore;
    
    // 스코어 사유
    private String carizonScoreReason; // 카리즌 스코어 종합 사유
    private String priceScoreReason;
    private String mileageScoreReason;
    private String freshnessScoreReason;
    private String priceTrendScoreReason;
    private String popularityScoreReason;
    
    // 순위
    private Integer rank;
    
    // 가격 변동 정보
    private Integer priceChange; // 최근 가격 변동 (음수면 하락, 양수면 상승)
    private Integer daysSinceUpdate; // 마지막 업데이트로부터 경과 일수
}

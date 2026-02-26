package com.carizon.rag.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 추천 엔진 입력: 필터(메타데이터) + 가중치(스코어 축).
 * 쿼리 → 이 스펙으로 변환 후, 동일 엔진(필터→벡터→스코어→top-N)만 돌림.
 */
@Data
@Builder
public class RecommendationSpec {
    // ---- 필터 (Chroma where / matchesFilters) ----
    private String maker;
    private String modelFilter;       // 문서/모델 검색용
    private String bodyTypeFilter;    // bodyTypeCategory
    private Integer yearMin;
    private Integer yearMax;
    /** 희망 연식(년식) - 이 연도면 스코어 보너스 */
    private Integer preferredYear;
    private Integer priceMin;
    private Integer priceMax;
    private Integer mileageMax;
    private String fuel;

    // ---- 스코어 가중치 (0.0~1.0, 합은 1 아님 상대비중) ----
    /** 가격 낮을수록 가점 */
    private double weightLowPrice;
    /** 주행거리 낮을수록 가점 */
    private double weightLowMileage;
    /** 연식 최신일수록 가점 */
    private double weightFreshness;
    /** 안전 프록시(연식+주행+차급) - 옵션 없을 때 대체 */
    private double weightSafetyProxy;
    /** 가성비(가격 대비 연식/주행) */
    private double weightValueForMoney;

    public static RecommendationSpec defaults() {
        return RecommendationSpec.builder()
            .weightLowPrice(0.2)
            .weightLowMileage(0.2)
            .weightFreshness(0.2)
            .weightSafetyProxy(0.2)
            .weightValueForMoney(0.2)
            .build();
    }

    /** 의도 프리셋 가중치만 반영한 스펙 (필터는 별도 설정) */
    public static RecommendationSpec fromIntent(RecommendationIntent intent) {
        Map<String, Double> w = intent.getWeights();
        return RecommendationSpec.builder()
            .weightLowPrice(w.getOrDefault("lowPrice", 0.2))
            .weightLowMileage(w.getOrDefault("lowMileage", 0.2))
            .weightFreshness(w.getOrDefault("freshness", 0.2))
            .weightSafetyProxy(w.getOrDefault("safetyProxy", 0.0))
            .weightValueForMoney(w.getOrDefault("valueForMoney", 0.2))
            .build();
    }
}


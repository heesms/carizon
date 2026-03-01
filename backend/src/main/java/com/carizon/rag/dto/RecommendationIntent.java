package com.carizon.rag.dto;

import java.util.Map;

/**
 * 추천 의도 프리셋. 질문은 무한하지만 축은 고정 → 의도별 가중치만 바꿈.
 */
public enum RecommendationIntent {
    /** 가성비 (연식 오래된 것 중 가격/주행 대비) */
    VALUE(Map.of("lowPrice", 0.35, "lowMileage", 0.25, "freshness", 0.0, "safetyProxy", 0.0, "valueForMoney", 0.4)),
    /** 안전 (연식 최신 + 주행 적음 + SUV/중형 프록시) */
    SAFETY(Map.of("lowPrice", 0.1, "lowMileage", 0.3, "freshness", 0.35, "safetyProxy", 0.25, "valueForMoney", 0.0)),
    /** 데이트/연인 (소형·준중형 + 최신 + 연비/유지비) */
    DATE(Map.of("lowPrice", 0.2, "lowMileage", 0.25, "freshness", 0.35, "safetyProxy", 0.0, "valueForMoney", 0.2)),
    /** 패밀리 (공간/SUV + 안전 프록시) */
    FAMILY(Map.of("lowPrice", 0.15, "lowMileage", 0.2, "freshness", 0.25, "safetyProxy", 0.4, "valueForMoney", 0.0)),
    /** 출퇴근 (연비/저렴/주행 적음) */
    COMMUTE(Map.of("lowPrice", 0.3, "lowMileage", 0.3, "freshness", 0.15, "safetyProxy", 0.0, "valueForMoney", 0.25)),
    /** 저예산 */
    LOW_BUDGET(Map.of("lowPrice", 0.5, "lowMileage", 0.2, "freshness", 0.0, "safetyProxy", 0.0, "valueForMoney", 0.3)),
    /** 기본 (골고루) */
    GENERAL(Map.of("lowPrice", 0.2, "lowMileage", 0.2, "freshness", 0.2, "safetyProxy", 0.1, "valueForMoney", 0.3));

    private final Map<String, Double> weights;

    RecommendationIntent(Map<String, Double> weights) {
        this.weights = weights;
    }

    public Map<String, Double> getWeights() {
        return weights;
    }
}

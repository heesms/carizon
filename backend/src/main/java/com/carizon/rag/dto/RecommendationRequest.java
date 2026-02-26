package com.carizon.rag.dto;

import lombok.Data;

/**
 * 차량 추천 요청
 */
@Data
public class RecommendationRequest {
    private String query; // 사용자 요구사항 (예: "가족용 SUV", "XC60 추천")
    /** 검색엔진 질의에 우선 적용할 텍스트 쿼리 (백엔드 전용) */
    private String searchQuery;
    private Integer maxResults; // 최대 추천 개수 (기본값: 5)
    private Integer minPrice; // 최소 가격 (선택)
    private Integer maxPrice; // 최대 가격 (선택)
    private String maker; // 제조사 (선택) - 메이커로만 한정하지 말고 비슷한 모델 추천 시에는 사용 안 함
    private String fuel; // 연료 타입 (선택)
    /** 연료 제외 필터(콤마 구분) */
    private String excludeFuel;
    /** 색상 필터(콤마 구분) */
    private String colorFilter;
    /** 색상 제외 필터(콤마 구분) */
    private String excludeColorFilter;
    /** 지역 필터(콤마 구분) */
    private String regionFilter;
    /** 지역 제외 필터(콤마 구분) */
    private String excludeRegionFilter;
    /** 모델명/차량명 필터 - 문서·model·modelGroup 검색용 (where_document $contains) */
    private String modelFilter;
    /** 차종 필터 (소형, 경차, SUV, 세단 등) - bodyTypeCategory 메타데이터 */
    private String bodyTypeFilter;
    /** 차종 제외 필터 (콤마 구분) */
    private String excludeBodyTypeFilter;
    /** 옵션 필터 (예: 선루프, 통풍시트) - optionArray/selOptionArray 매칭 */
    private String optionFilter;
    /** 무사고 우선 여부 (true면 사고 이력 차량 제외) */
    private Boolean noAccident;
    /** 무침수 우선 여부 (true면 침수전손 이력 차량 제외) */
    private Boolean noFloodDamage;
    /** 연식 상한 (오래된 거 → year <= maxYear) */
    private Integer maxYear;
    /** 연식 하한 (최신/신형 → year >= minYear) */
    private Integer minYear;
    /** 주행거리 상한 (km) */
    private Integer maxKm;
    /** 주행거리 하한 (km) */
    private Integer minKm;
    /** 희망 연식 (예: "22년식" → 2022) - 이 연도면 스코어 보너스로 먼저 노출 */
    private Integer preferredYear;
    /** 추천 의도 (VALUE/SAFETY/DATE/FAMILY/COMMUTE/LOW_BUDGET/GENERAL) → 스코어 가중치용 */
    private String intent;
    /** AI 추천(LLM/Ollama) 사용 여부. false면 팩터 추출 기반 검색만 수행 */
    private Boolean useLlm;
}

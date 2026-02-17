package com.carizon.rag.dto;

import lombok.Data;

/**
 * 차량 추천 요청
 */
@Data
public class RecommendationRequest {
    private String query; // 사용자 요구사항 (예: "가족용 SUV", "XC60 추천")
    /** RAG 검색용 해석 쿼리. 명시적 조건 없을 때 LLM이 먼저 해석해 세팅 (백엔드 전용) */
    private String searchQuery;
    private Integer maxResults; // 최대 추천 개수 (기본값: 5)
    private Integer minPrice; // 최소 가격 (선택)
    private Integer maxPrice; // 최대 가격 (선택)
    private String maker; // 제조사 (선택) - 메이커로만 한정하지 말고 비슷한 모델 추천 시에는 사용 안 함
    private String fuel; // 연료 타입 (선택)
    /** 모델명/차량명 필터 - 문서·model·modelGroup 검색용 (where_document $contains) */
    private String modelFilter;
    /** 차종 필터 (소형, 경차, SUV, 세단 등) - bodyTypeCategory 메타데이터 */
    private String bodyTypeFilter;
    /** 연식 상한 (오래된 거 → year <= maxYear) */
    private Integer maxYear;
    /** 연식 하한 (최신/신형 → year >= minYear) */
    private Integer minYear;
    /** 희망 연식 (예: "22년식" → 2022) - 이 연도면 스코어 보너스로 먼저 노출 */
    private Integer preferredYear;
    /** 추천 의도 (VALUE/SAFETY/DATE/FAMILY/COMMUTE/LOW_BUDGET/GENERAL) → 스코어 가중치용 */
    private String intent;
}

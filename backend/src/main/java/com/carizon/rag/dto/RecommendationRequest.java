package com.carizon.rag.dto;

import lombok.Data;

/**
 * 차량 추천 요청
 */
@Data
public class RecommendationRequest {
    private String query; // 사용자 요구사항 (예: "가족용 SUV, 3000만원 이하, 연비 좋은 차")
    private Integer maxResults; // 최대 추천 개수 (기본값: 5)
    private Integer minPrice; // 최소 가격 (선택)
    private Integer maxPrice; // 최대 가격 (선택)
    private String maker; // 제조사 (선택)
    private String fuel; // 연료 타입 (선택)
}

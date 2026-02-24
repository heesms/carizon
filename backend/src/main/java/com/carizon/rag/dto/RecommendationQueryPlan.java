package com.carizon.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 자연어 추천 요청을 구조화한 검색 플랜.
 * LLM이 이 객체를 채우고, 서버가 검증/정규화한 뒤 안전한 DSL 빌더로 전달한다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationQueryPlan {
    /** 검색 본문(q) */
    private String textQuery;

    /** 코드/이름 계열 */
    private String makerCode;
    private String maker;
    private String modelCode;
    private String model;

    /** 필터 계열 */
    private List<String> bodyTypes;
    private String fuel;
    private Integer minPrice;
    private Integer maxPrice;
    private Integer minYear;
    private Integer maxYear;
    private Integer maxKm;

    /** 정렬/의도 */
    private String sort;
    private String intent;
}

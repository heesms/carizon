package com.carizon.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 추천 v2 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationV2Response {
    private String recommendation;
    private List<RecommendationResponse.RecommendedCar> cars;
    private Map<String, Object> meta;
}


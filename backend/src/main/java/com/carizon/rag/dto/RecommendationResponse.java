package com.carizon.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 차량 추천 응답
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationResponse {
    private String recommendation; // LLM이 생성한 추천 설명
    private List<RecommendedCar> cars; // 추천된 차량 목록
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendedCar {
        private Long carId;
        private String maker;
        private String model;
        private String trim;
        private Integer year;
        private Integer mileage;
        private Integer price;
        private String fuel;
        private String transmission;
        private String color;
        private String url;
        private Double relevanceScore; // 유사도 점수
        private String reason; // 추천 이유
    }
}

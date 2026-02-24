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
    private String recommendation; // 추천/검색 결과 안내 문구
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
        private String region; // 지역
        private String url;    // 기본 링크 (하위 호환)
        private String pcUrl;  // PC용 링크
        private String mUrl;   // 모바일용 링크
        private String imageUrl; // 차량 이미지 URL
        private Double relevanceScore; // 유사도 점수
        private String reason; // 추천 이유
    }
}

package com.carizon.rag.service;

import com.carizon.rag.dto.RecommendationRequest;
import com.carizon.rag.dto.RecommendationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * LLM 기반 차량 추천 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarRecommendationService {
    
    private final RagSearchService ragSearchService;
    private final LlmService llmService;
    
    /**
     * 사용자 요구사항을 기반으로 차량 추천
     */
    public RecommendationResponse recommendCars(RecommendationRequest request) throws IOException {
        // 기본값 설정
        if (request.getMaxResults() == null) {
            request.setMaxResults(5);
        }
        
        // RAG 검색으로 유사한 차량 찾기
        List<RecommendationResponse.RecommendedCar> cars = ragSearchService.searchSimilarCars(request);
        
        if (cars.isEmpty()) {
            return RecommendationResponse.builder()
                    .recommendation("요청하신 조건에 맞는 차량을 찾을 수 없습니다.")
                    .cars(List.of())
                    .build();
        }
        
        // LLM으로 추천 설명 생성
        String recommendation = generateRecommendation(request, cars);
        
        // 각 차량에 추천 이유 추가
        cars = addRecommendationReasons(cars, request);
        
        return RecommendationResponse.builder()
                .recommendation(recommendation)
                .cars(cars)
                .build();
    }
    
    /**
     * LLM으로 추천 설명 생성
     */
    private String generateRecommendation(RecommendationRequest request, 
                                         List<RecommendationResponse.RecommendedCar> cars) throws IOException {
        StringBuilder prompt = new StringBuilder();
        prompt.append("사용자가 다음과 같은 요구사항으로 중고차를 찾고 있습니다:\n");
        prompt.append("요구사항: ").append(request.getQuery()).append("\n\n");
        
        if (request.getMinPrice() != null || request.getMaxPrice() != null) {
            prompt.append("가격 범위: ");
            if (request.getMinPrice() != null) prompt.append(request.getMinPrice()).append("만원 이상 ");
            if (request.getMaxPrice() != null) prompt.append(request.getMaxPrice()).append("만원 이하");
            prompt.append("\n");
        }
        
        prompt.append("\n검색된 차량 목록:\n");
        for (int i = 0; i < cars.size(); i++) {
            RecommendationResponse.RecommendedCar car = cars.get(i);
            prompt.append((i + 1)).append(". ");
            if (car.getMaker() != null) prompt.append(car.getMaker()).append(" ");
            if (car.getModel() != null) prompt.append(car.getModel()).append(" ");
            if (car.getTrim() != null) prompt.append(car.getTrim()).append(" ");
            if (car.getYear() != null) prompt.append("(").append(car.getYear()).append("년식) ");
            if (car.getMileage() != null) prompt.append("주행거리: ").append(String.format("%,d", car.getMileage())).append("km ");
            if (car.getPrice() != null) prompt.append("가격: ").append(String.format("%,d", car.getPrice())).append("만원");
            prompt.append("\n");
        }
        
        prompt.append("\n위 차량 목록을 바탕으로 사용자에게 친절하고 자연스러운 한국어로 추천 설명을 작성해주세요. ");
        prompt.append("각 차량의 특징과 사용자 요구사항과의 매칭 포인트를 설명해주세요. ");
        prompt.append("너무 길지 않게 3-5문장 정도로 간결하게 작성해주세요.");
        
        try {
            return llmService.generateResponse(prompt.toString());
        } catch (Exception e) {
            log.warn("Failed to generate LLM recommendation", e);
            return "검색된 차량 중에서 요구사항에 맞는 차량을 추천드립니다.";
        }
    }
    
    /**
     * 각 차량에 추천 이유 추가
     */
    private List<RecommendationResponse.RecommendedCar> addRecommendationReasons(
            List<RecommendationResponse.RecommendedCar> cars, RecommendationRequest request) {
        
        return cars.stream().map(car -> {
            StringBuilder reason = new StringBuilder();
            
            if (car.getRelevanceScore() != null && car.getRelevanceScore() > 0.7) {
                reason.append("요구사항과 높은 유사도(")
                      .append(String.format("%.0f%%", car.getRelevanceScore() * 100))
                      .append(")를 보입니다. ");
            }
            
            if (car.getPrice() != null && request.getMaxPrice() != null && car.getPrice() <= request.getMaxPrice()) {
                reason.append("예산 범위 내의 가격입니다. ");
            }
            
            if (car.getMileage() != null && car.getMileage() < 50000) {
                reason.append("주행거리가 적어 상태가 양호할 가능성이 높습니다. ");
            }
            
            if (reason.length() == 0) {
                reason.append("검색 조건과 일치합니다.");
            }
            
            car.setReason(reason.toString().trim());
            return car;
        }).collect(Collectors.toList());
    }
}

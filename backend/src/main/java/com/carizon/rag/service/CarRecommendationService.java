package com.carizon.rag.service;

import com.carizon.rag.config.RagProperties;
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
    private final RagProperties ragProperties;
    private final LlmConfigService llmConfigService;
    
    /**
     * 사용자 요구사항을 기반으로 차량 추천
     */
    public RecommendationResponse recommendCars(RecommendationRequest request) throws IOException {
        log.info("[추천] 서비스 시작");
        log.info("[추천] 입력 요청: {}", request);
        
        // 기본값 설정
        if (request.getMaxResults() == null) {
            request.setMaxResults(5);
        }
        
        // RAG 검색으로 유사한 차량 찾기
        List<RecommendationResponse.RecommendedCar> cars = ragSearchService.searchSimilarCars(request);
        
        if (cars.isEmpty()) {
            log.warn("[추천] 추천 차량 없음");
            return RecommendationResponse.builder()
                    .recommendation("요청하신 조건에 맞는 차량을 찾을 수 없습니다.")
                    .cars(List.of())
                    .build();
        }
        
        // LLM으로 추천 설명 생성
        log.info("[LLM] 추천 설명 생성 시작");
        String recommendation = generateRecommendation(request, cars);
        log.info("[LLM] 추천 설명 생성 완료: {}", recommendation.substring(0, Math.min(100, recommendation.length())));
        
        // 각 차량에 추천 이유 추가
        cars = addRecommendationReasons(cars, request);
        
        RecommendationResponse response = RecommendationResponse.builder()
                .recommendation(recommendation)
                .cars(cars)
                .build();
        
        log.info("[추천] 응답 완료: 차량 {}개", response.getCars().size());
        for (int i = 0; i < response.getCars().size(); i++) {
            RecommendationResponse.RecommendedCar car = response.getCars().get(i);
            log.info("  [{}] carId={}, 유사도={}%, 가격={}만원, 이유={}", 
                i + 1,
                car.getCarId(),
                car.getRelevanceScore() != null ? String.format("%.2f", car.getRelevanceScore() * 100) : "N/A",
                car.getPrice(),
                car.getReason());
        }
        
        return response;
    }
    
    /**
     * LLM으로 추천 설명 생성 (DB 설정 기반)
     */
    private String generateRecommendation(RecommendationRequest request, 
                                         List<RecommendationResponse.RecommendedCar> cars) throws IOException {
        // DB에서 프롬프트 조회 (없으면 기본값 사용)
        String intro = llmConfigService.getPrompt("intro");
        String priceRangeFormat = llmConfigService.getPrompt("price-range-format");
        String carListTitle = llmConfigService.getPrompt("car-list-title");
        String carFormat = llmConfigService.getPrompt("car-format");
        String instruction = llmConfigService.getPrompt("instruction");
        String defaultRecommendation = llmConfigService.getPrompt("default-recommendation");
        
        StringBuilder prompt = new StringBuilder();
        prompt.append(intro).append("\n");
        prompt.append("요구사항: ").append(request.getQuery()).append("\n\n");
        
        if (request.getMinPrice() != null || request.getMaxPrice() != null) {
            String priceRange = priceRangeFormat
                .replace("${minPrice}", request.getMinPrice() != null ? String.valueOf(request.getMinPrice()) : "")
                .replace("${maxPrice}", request.getMaxPrice() != null ? String.valueOf(request.getMaxPrice()) : "");
            prompt.append(priceRange).append("\n");
        }
        
        prompt.append("\n").append(carListTitle).append("\n");
        for (int i = 0; i < cars.size(); i++) {
            RecommendationResponse.RecommendedCar car = cars.get(i);
            String carInfo = carFormat
                .replace("${index}", String.valueOf(i + 1))
                .replace("${maker}", car.getMaker() != null ? car.getMaker() : "")
                .replace("${model}", car.getModel() != null ? car.getModel() : "")
                .replace("${trim}", car.getTrim() != null ? car.getTrim() : "")
                .replace("${year}", car.getYear() != null ? String.valueOf(car.getYear()) : "")
                .replace("${mileage}", car.getMileage() != null ? String.format("%,d", car.getMileage()) : "")
                .replace("${price}", car.getPrice() != null ? String.format("%,d", car.getPrice()) : "");
            prompt.append(carInfo).append("\n");
        }
        
        prompt.append("\n").append(instruction);
        
        try {
            return llmService.generateResponse(prompt.toString());
        } catch (Exception e) {
            log.warn("Failed to generate LLM recommendation", e);
            return defaultRecommendation;
        }
    }
    
    /**
     * 각 차량에 추천 이유 추가 (DB 설정 기반)
     */
    private List<RecommendationResponse.RecommendedCar> addRecommendationReasons(
            List<RecommendationResponse.RecommendedCar> cars, RecommendationRequest request) {
        
        // DB에서 설정 조회
        double highSimilarityThreshold = llmConfigService.getHighSimilarityThreshold();
        int lowMileageThreshold = llmConfigService.getLowMileageThreshold();
        String highSimilarityMessage = llmConfigService.getPrompt("high-similarity-message");
        String withinBudgetMessage = llmConfigService.getPrompt("within-budget-message");
        String lowMileageMessage = llmConfigService.getPrompt("low-mileage-message");
        String defaultMessage = llmConfigService.getPrompt("default-message");
        
        return cars.stream().map(car -> {
            StringBuilder reason = new StringBuilder();
            
            // 높은 유사도 체크
            if (car.getRelevanceScore() != null && 
                car.getRelevanceScore() >= highSimilarityThreshold) {
                String score = String.format("%.0f", car.getRelevanceScore() * 100);
                reason.append(highSimilarityMessage.replace("${score}", score));
            }
            
            // 예산 범위 체크
            if (car.getPrice() != null && request.getMaxPrice() != null && 
                car.getPrice() <= request.getMaxPrice()) {
                reason.append(withinBudgetMessage);
            }
            
            // 주행거리 체크
            if (car.getMileage() != null && 
                car.getMileage() < lowMileageThreshold) {
                reason.append(lowMileageMessage);
            }
            
            // 기본 메시지
            if (reason.length() == 0) {
                reason.append(defaultMessage);
            }
            
            car.setReason(reason.toString().trim());
            return car;
        }).collect(Collectors.toList());
    }
}

package com.carizon.rag.service;

import com.carizon.domain.mapper.CarMapper;
import com.carizon.dto.CarDetailRow;
import com.carizon.rag.dto.RecommendationRequest;
import com.carizon.rag.dto.RecommendationResponse;
import com.carizon.rag.service.ChromaVectorStoreService.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * RAG 검색 서비스 (벡터 검색 + 필터링)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagSearchService {
    
    private final EmbeddingService embeddingService;
    private final ChromaVectorStoreService vectorStoreService;
    private final CarMapper carMapper;
    
    /**
     * 사용자 쿼리로 유사한 차량 검색
     */
    public List<RecommendationResponse.RecommendedCar> searchSimilarCars(
            RecommendationRequest request) throws IOException {
        
        // 쿼리 텍스트를 임베딩으로 변환
        float[] queryEmbedding = embeddingService.generateEmbedding(request.getQuery());
        
        // 벡터 검색 (더 많은 결과를 가져와서 필터링)
        int searchCount = request.getMaxResults() != null ? request.getMaxResults() * 3 : 15;
        List<SearchResult> searchResults = vectorStoreService.searchSimilar(queryEmbedding, searchCount);
        
        // 필터링 및 차량 상세 정보 조회
        List<RecommendationResponse.RecommendedCar> recommendedCars = new ArrayList<>();
        
        for (SearchResult result : searchResults) {
            if (result.getCarId() == null) continue;
            
            // 차량 상세 정보 조회
            List<CarDetailRow> carDetails = carMapper.selectCarDetail(result.getCarId());
            if (carDetails.isEmpty()) continue;
            
            CarDetailRow car = carDetails.get(0);
            
            // 필터링 적용
            if (!matchesFilters(car, request)) {
                continue;
            }
            
            // 추천 차량 객체 생성
            RecommendationResponse.RecommendedCar recommendedCar = 
                RecommendationResponse.RecommendedCar.builder()
                    .carId(car.carId())
                    .maker(car.makerName())
                    .model(car.modelName())
                    .trim(car.trimName())
                    .year(car.year())
                    .mileage(car.mileage())
                    .price(car.price())
                    .fuel(car.fuel())
                    .transmission(car.transmission())
                    .color(car.color())
                    .url(car.pcUrl())
                    .relevanceScore(result.getScore())
                    .build();
            
            recommendedCars.add(recommendedCar);
            
            // 최대 개수 제한
            if (request.getMaxResults() != null && recommendedCars.size() >= request.getMaxResults()) {
                break;
            }
        }
        
        return recommendedCars;
    }
    
    /**
     * 필터 조건 확인
     */
    private boolean matchesFilters(CarDetailRow car, RecommendationRequest request) {
        // 가격 필터
        if (request.getMinPrice() != null && car.price() != null && car.price() < request.getMinPrice()) {
            return false;
        }
        if (request.getMaxPrice() != null && car.price() != null && car.price() > request.getMaxPrice()) {
            return false;
        }
        
        // 제조사 필터
        if (request.getMaker() != null && !request.getMaker().isBlank()) {
            if (car.makerName() == null || !car.makerName().equalsIgnoreCase(request.getMaker())) {
                return false;
            }
        }
        
        // 연료 타입 필터
        if (request.getFuel() != null && !request.getFuel().isBlank()) {
            if (car.fuel() == null || !car.fuel().equalsIgnoreCase(request.getFuel())) {
                return false;
            }
        }
        
        // 판매 중인 차량만
        if (car.status() == null || !"ONSALE".equalsIgnoreCase(car.status())) {
            return false;
        }
        
        return true;
    }
}

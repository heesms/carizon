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
    private final CarImageExtractorService imageExtractorService;
    private final LlmConfigService llmConfigService;
    
    /**
     * 사용자 쿼리로 유사한 차량 검색
     */
    public List<RecommendationResponse.RecommendedCar> searchSimilarCars(
            RecommendationRequest request) throws IOException {
        
        // 쿼리 텍스트를 임베딩으로 변환
        float[] queryEmbedding = embeddingService.generateEmbedding(request.getQuery());
        
        // 벡터 검색 (더 많은 결과를 가져와서 필터링)
        // DB에서 설정 조회 (기본값: 3배)
        int multiplier = llmConfigService.getSearchMaxResultsMultiplier();
        int searchCount = request.getMaxResults() != null ? request.getMaxResults() * multiplier : 15;
        List<SearchResult> searchResults = vectorStoreService.searchSimilar(queryEmbedding, searchCount);
        
        // 필터링 및 차량 상세 정보 조회 (URL 포함)
        List<RecommendationResponse.RecommendedCar> recommendedCars = new ArrayList<>();
        
        for (SearchResult result : searchResults) {
            if (result.getCarId() == null) continue;
            
            // 필터링 적용 (metadata에서 빠르게 확인)
            if (!matchesFilters(result, request)) {
                continue;
            }
            
            // DB에서 상세 정보 조회 (URL 포함, 오류 처리)
            String url = null;
            String imageUrl = result.getImageUrl(); // metadata에서 먼저 가져오기
            
            try {
                List<CarDetailRow> carDetails = carMapper.selectCarDetail(result.getCarId());
                if (!carDetails.isEmpty()) {
                    CarDetailRow car = carDetails.get(0);
                    // PC URL 우선, 없으면 모바일 URL
                    url = (car.pcUrl() != null && !car.pcUrl().isEmpty()) 
                        ? car.pcUrl() 
                        : car.mUrl();
                }
            } catch (Exception e) {
                log.warn("Failed to fetch car detail for carId {}: {}", result.getCarId(), e.getMessage());
                // DB 조회 실패 시 metadata의 URL 사용
                url = (result.getPcUrl() != null && !result.getPcUrl().isEmpty()) 
                    ? result.getPcUrl() 
                    : result.getMUrl();
            }
            
            // metadata에 URL이 없고 DB 조회도 실패한 경우, metadata의 URL 사용
            if (url == null || url.isEmpty()) {
                url = (result.getPcUrl() != null && !result.getPcUrl().isEmpty()) 
                    ? result.getPcUrl() 
                    : result.getMUrl();
            }
            
            // 이미지 추출은 레이지 로딩으로 처리 (프론트엔드에서 별도 API 호출)
            // 백엔드에서는 URL만 전달하고, 프론트엔드에서 필요할 때 이미지 추출 API 호출
            
            // 추천 차량 객체 생성
            RecommendationResponse.RecommendedCar recommendedCar = 
                RecommendationResponse.RecommendedCar.builder()
                    .carId(result.getCarId())
                    .maker(result.getMaker())
                    .model(result.getModel())
                    .trim(result.getTrim())
                    .year(result.getYear())
                    .mileage(result.getMileage())
                    .price(result.getPrice())
                    .fuel(result.getFuel())
                    .transmission(result.getTransmission())
                    .color(result.getColor())
                    .url(url)
                    .imageUrl(imageUrl)
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
     * 필터 조건 확인 (metadata 기반, DB 조회 없음)
     */
    private boolean matchesFilters(SearchResult result, RecommendationRequest request) {
        // 가격 필터
        if (request.getMinPrice() != null && result.getPrice() != null && result.getPrice() < request.getMinPrice()) {
            return false;
        }
        if (request.getMaxPrice() != null && result.getPrice() != null && result.getPrice() > request.getMaxPrice()) {
            return false;
        }
        
        // 제조사 필터
        if (request.getMaker() != null && !request.getMaker().isBlank()) {
            if (result.getMaker() == null || !result.getMaker().equalsIgnoreCase(request.getMaker())) {
                return false;
            }
        }
        
        // 연료 타입 필터
        if (request.getFuel() != null && !request.getFuel().isBlank()) {
            if (result.getFuel() == null || !result.getFuel().equalsIgnoreCase(request.getFuel())) {
                return false;
            }
        }
        
        // 판매 중인 차량만
        if (result.getStatus() == null || !"ONSALE".equalsIgnoreCase(result.getStatus())) {
            return false;
        }
        
        return true;
    }
}

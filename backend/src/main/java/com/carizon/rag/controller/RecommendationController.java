package com.carizon.rag.controller;

import com.carizon.common.dto.ApiResponse;
import com.carizon.rag.dto.RecommendationRequest;
import com.carizon.rag.dto.RecommendationResponse;
import com.carizon.rag.service.CarRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 차량 추천 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
@Tag(name = "차량 추천", description = "RAG 기반 LLM 차량 추천 API")
public class RecommendationController {
    
    private final CarRecommendationService recommendationService;
    
    @PostMapping
    @Operation(summary = "차량 추천", description = "사용자 요구사항을 기반으로 LLM이 차량을 추천합니다")
    public ResponseEntity<ApiResponse<RecommendationResponse>> recommendCars(@RequestBody RecommendationRequest request) throws Exception {
        RecommendationResponse response = recommendationService.recommendCars(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @GetMapping("/health")
    @Operation(summary = "헬스 체크", description = "RAG 서비스 상태 확인")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(ApiResponse.success("RAG service is running"));
    }
}

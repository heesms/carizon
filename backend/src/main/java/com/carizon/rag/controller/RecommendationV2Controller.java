package com.carizon.rag.controller;

import com.carizon.common.dto.ApiResponse;
import com.carizon.rag.dto.RecommendationRequest;
import com.carizon.rag.dto.RecommendationV2Response;
import com.carizon.rag.service.RecommendationV2Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/recommendations")
@Tag(name = "차량 추천 v2", description = "모델 임베딩 + 하이브리드 retrieval 기반 추천 API")
public class RecommendationV2Controller {

    private final RecommendationV2Service recommendationV2Service;

    @PostMapping
    @Operation(summary = "차량 추천 v2", description = "자연어를 하드필터/소프트선호로 분리해 하이브리드 추천을 수행합니다.")
    public ResponseEntity<ApiResponse<RecommendationV2Response>> recommendV2(
            @RequestBody RecommendationRequest request
    ) throws Exception {
        RecommendationV2Response response = recommendationV2Service.recommend(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}


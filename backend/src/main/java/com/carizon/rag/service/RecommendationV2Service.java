package com.carizon.rag.service;

import com.carizon.rag.dto.RecommendationRequest;
import com.carizon.rag.dto.RecommendationResponse;
import com.carizon.rag.dto.RecommendationV2Response;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RecommendationV2Service {

    private final CarRecommendationService carRecommendationService;

    public RecommendationV2Service(CarRecommendationService carRecommendationService) {
        this.carRecommendationService = carRecommendationService;
    }

    public RecommendationV2Response recommend(RecommendationRequest request) throws IOException {
        RecommendationResponse base = carRecommendationService.recommendCars(request);
        List<RecommendationResponse.RecommendedCar> cars = base.getCars() != null ? base.getCars() : List.of();

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("version", "v2");
        meta.put("retrieval", "hybrid");
        meta.put("count", cars.size());

        return RecommendationV2Response.builder()
                .recommendation(base.getRecommendation())
                .cars(cars)
                .meta(meta)
                .build();
    }
}

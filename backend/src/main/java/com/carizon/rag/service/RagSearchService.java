package com.carizon.rag.service;

import com.carizon.domain.mapper.CarMapper;
import com.carizon.rag.dto.RecommendationIntent;
import com.carizon.rag.dto.RecommendationRequest;
import com.carizon.rag.dto.RecommendationResponse;
import com.carizon.rag.dto.RecommendationSpec;
import com.carizon.rag.service.ChromaVectorStoreService.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 검색 서비스.
 * 기존 Chroma 메타데이터·임베딩 전부 활용 (maker, model, modelGroup, bodyType, bodyTypeCategory, year, price, fuel 등).
 * 스키마/메타데이터 재설계 없이 쿼리만으로 전반 반영.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagSearchService {
    
    private final EmbeddingService embeddingService;
    private final ChromaVectorStoreService vectorStoreService;
    private final CarMapper carMapper;
    private final LlmConfigService llmConfigService;
    
    /**
     * 사용자 쿼리로 유사한 차량 검색
     */
    public List<RecommendationResponse.RecommendedCar> searchSimilarCars(
            RecommendationRequest request) throws IOException {
        long start = System.currentTimeMillis();
        // RAG 검색에 쓸 문구: LLM이 해석한 searchQuery가 있으면 사용, 없으면 사용자 query
        String queryForEmbedding = (request.getSearchQuery() != null && !request.getSearchQuery().isBlank())
            ? request.getSearchQuery().trim() : (request.getQuery() != null ? request.getQuery().trim() : "");
        if (queryForEmbedding.isEmpty()) {
            log.warn("[RAG] empty query and searchQuery, returning empty");
            return List.of();
        }
        // 쿼리 텍스트를 임베딩으로 변환
        long embStart = System.currentTimeMillis();
        float[] queryEmbedding = embeddingService.generateEmbedding(queryForEmbedding);
        log.info("[RAG] embedding: {}ms, query={}", System.currentTimeMillis() - embStart, queryForEmbedding.length() > 60 ? queryForEmbedding.substring(0, 60) + "..." : queryForEmbedding);

        // 모델 전용 컬렉션 검색: "스포츠카" 등 쿼리와 유사한 모델코드(911 등) 수집 → 해당 모델 차량에 보너스
        Set<String> queryMatchedModelCodes = new HashSet<>();
        try {
            List<SearchResult> modelResults = vectorStoreService.searchSimilarInModelCollection(queryEmbedding, 25, null);
            for (SearchResult mr : modelResults) {
                String id = mr.getId();
                if (id != null && id.startsWith("model_")) {
                    String modelCode = id.substring(6).trim();
                    if (!modelCode.isEmpty()) queryMatchedModelCodes.add(modelCode);
                }
            }
            if (!queryMatchedModelCodes.isEmpty()) {
                log.info("[RAG] model_descriptions matched: {} modelCodes (e.g. 스포츠카→911 등)", queryMatchedModelCodes.size());
            }
        } catch (Exception e) {
            log.debug("[RAG] model collection search skipped: {}", e.getMessage());
        }

        // 벡터 검색 (더 많은 결과를 가져와서 필터링; 후보 많을수록 쿼리별 셔플 효과 큼)
        int multiplier = llmConfigService.getSearchMaxResultsMultiplier();
        int maxResults = request.getMaxResults() != null ? request.getMaxResults() : 5;
        int searchCount = Math.max(maxResults * multiplier, 50);
        // 차량명/모델명 있으면 문서·model·modelGroup 전부 검색 (where_document $contains)
        String modelFilter = request.getModelFilter() != null && !request.getModelFilter().isBlank() ? request.getModelFilter().trim() : null;
        Map<String, Object> chromaWhereDocument = null;
        if (modelFilter != null) {
            searchCount = Math.max(searchCount, 200);
            chromaWhereDocument = Map.of("$contains", modelFilter);
        }
        // 메타데이터 where: maker, bodyTypeCategory
        Map<String, Object> chromaWhere = buildChromaWhere(request);
        if (modelFilter != null || (request.getMaker() != null && !request.getMaker().isBlank())
                || (request.getBodyTypeFilter() != null && !request.getBodyTypeFilter().isBlank())
                || (request.getFuel() != null && !request.getFuel().isBlank())
                || request.getMaxYear() != null || request.getMinYear() != null) {
            searchCount = Math.max(searchCount, 200);
        }
        long vecStart = System.currentTimeMillis();
        List<SearchResult> searchResults = vectorStoreService.searchSimilar(queryEmbedding, searchCount, chromaWhere, chromaWhereDocument);
        log.info("[RAG] vector search: {}ms, results={}, where={}, where_doc={}", System.currentTimeMillis() - vecStart, searchResults.size(), chromaWhere, chromaWhereDocument);
        
        // A) 후보 수집: 필터 통과한 차량만 (최대 100건), car_id 중복 제거
        int candidateCap = 100;
        long loopStart = System.currentTimeMillis();
        List<RecommendationResponse.RecommendedCar> candidates = new ArrayList<>();
        Set<Long> seenCarIds = new LinkedHashSet<>();
        
        for (SearchResult result : searchResults) {
            if (result.getCarId() == null) continue;
            if (seenCarIds.contains(result.getCarId())) continue;
            seenCarIds.add(result.getCarId());
            if (!matchesFilters(result, request)) continue;
            
            String pcUrl = null;
            String mUrl = null;
            try {
                Map<String, Object> row = carMapper.selectCarUrl(result.getCarId());
                if (row != null) {
                    pcUrl = row.get("pcUrl") != null ? row.get("pcUrl").toString().trim() : null;
                    mUrl = row.get("mUrl") != null ? row.get("mUrl").toString().trim() : null;
                    if (pcUrl != null && pcUrl.isEmpty()) pcUrl = null;
                    if (mUrl != null && mUrl.isEmpty()) mUrl = null;
                }
            } catch (Exception e) {
                log.warn("Failed to fetch car URL for carId {}: {}", result.getCarId(), e.getMessage());
            }
            if (pcUrl == null && result.getPcUrl() != null && !result.getPcUrl().isBlank()) pcUrl = result.getPcUrl();
            if (mUrl == null && result.getMUrl() != null && !result.getMUrl().isBlank()) mUrl = result.getMUrl();
            String url = (pcUrl != null && !pcUrl.isEmpty()) ? pcUrl : mUrl;
            
            // 이미지: 검색된 문서(Chroma 메타데이터)에 있으면 우선, 없으면 platform_car.car_image_url DB 조회
            String imageUrl = (result.getImageUrl() != null && !result.getImageUrl().isBlank()) ? result.getImageUrl() : null;
            if (imageUrl == null || imageUrl.isBlank()) {
                try {
                    imageUrl = carMapper.selectCarRepresentativeImageUrl(result.getCarId());
                } catch (Exception ignored) { }
            }
            
            RecommendationResponse.RecommendedCar car = RecommendationResponse.RecommendedCar.builder()
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
                .region(result.getRegion())
                .url(url)
                .pcUrl(pcUrl)
                .mUrl(mUrl)
                .imageUrl(imageUrl)
                .relevanceScore(result.getScore())
                .build();
            candidates.add(car);
            if (candidates.size() >= candidateCap) break;
        }
        // 모델 컬렉션 매칭 시 해당 모델 매물을 후보에 추가 (스포츠카 요청 시 911 등이 벡터 검색에 안 걸려도 포함)
        if (!queryMatchedModelCodes.isEmpty()) {
            try {
                List<Map<String, Object>> byModel = carMapper.selectCarsByModelCodes(new ArrayList<>(queryMatchedModelCodes), 35);
                for (Map<String, Object> row : byModel != null ? byModel : List.<Map<String, Object>>of()) {
                    Object cidObj = row.get("carId");
                    if (cidObj == null) continue;
                    long cid = ((Number) cidObj).longValue();
                    if (seenCarIds.contains(cid)) continue;
                    seenCarIds.add(cid);
                    String pcUrl = null, mUrl = null;
                    try {
                        Map<String, Object> urlRow = carMapper.selectCarUrl(cid);
                        if (urlRow != null) {
                            pcUrl = urlRow.get("pcUrl") != null ? urlRow.get("pcUrl").toString().trim() : null;
                            mUrl = urlRow.get("mUrl") != null ? urlRow.get("mUrl").toString().trim() : null;
                        }
                    } catch (Exception ignored) { }
                    String imageUrl = null;
                    try { imageUrl = carMapper.selectCarRepresentativeImageUrl(cid); } catch (Exception ignored) { }
                    String url = (pcUrl != null && !pcUrl.isEmpty()) ? pcUrl : (mUrl != null ? mUrl : null);
                    RecommendationResponse.RecommendedCar car = RecommendationResponse.RecommendedCar.builder()
                            .carId(cid)
                            .maker(getString(row, "maker"))
                            .model(getString(row, "model"))
                            .trim(getString(row, "trim"))
                            .year(getInt(row, "year"))
                            .mileage(getInt(row, "mileage"))
                            .price(getInt(row, "price"))
                            .fuel(getString(row, "fuel"))
                            .transmission(getString(row, "transmission"))
                            .color(getString(row, "color"))
                            .region(getString(row, "region"))
                            .url(url)
                            .pcUrl(pcUrl)
                            .mUrl(mUrl)
                            .imageUrl(imageUrl)
                            .relevanceScore(0.5)
                            .build();
                    candidates.add(car);
                    if (candidates.size() >= candidateCap + 30) break;
                }
                if (byModel != null && !byModel.isEmpty()) {
                    log.info("[RAG] candidates +{} from modelCodes (e.g. 스포츠카→911)", byModel.size());
                }
            } catch (Exception e) {
                log.warn("[RAG] selectCarsByModelCodes failed: {}", e.getMessage());
            }
        }

        long loopMs = System.currentTimeMillis() - loopStart;
        log.info("[RAG] candidates: {} (filter+url) {}ms", candidates.size(), loopMs);

        // 후보 차량의 model_code 항상 조회 (모델 컬렉션 매칭 보너스·추후 활용용. 스포츠카뿐 아니라 모든 쿼리에서 모델 쪽 계속 참고)
        Map<Long, String> carIdToModelCode = new HashMap<>();
        if (!candidates.isEmpty()) {
            List<Long> carIds = candidates.stream().map(RecommendationResponse.RecommendedCar::getCarId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
            try {
                List<Map<String, Object>> rows = carMapper.selectCarIdModelCodes(carIds);
                if (rows != null) {
                    for (Map<String, Object> row : rows) {
                        Object cid = row.get("carId");
                        Object code = row.get("modelCode");
                        if (cid != null && code != null) {
                            carIdToModelCode.put(((Number) cid).longValue(), code.toString().trim());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[RAG] selectCarIdModelCodes failed: {}", e.getMessage());
            }
        }
        
        // B) 스코어링 → top-N 선정 (모델 컬렉션과 맞는 모델이면 보너스로 상위 노출)
        RecommendationSpec spec = buildSpecFromRequest(request);
        List<RecommendationResponse.RecommendedCar> recommendedCars = scoreAndTakeTop(candidates, spec, request.getMaxResults() != null ? request.getMaxResults() : 5, queryMatchedModelCodes, carIdToModelCode);
        log.info("[RAG] searchSimilarCars total: {}ms, top={}", System.currentTimeMillis() - start, recommendedCars.size());
        return recommendedCars;
    }
    
    private RecommendationSpec buildSpecFromRequest(RecommendationRequest request) {
        RecommendationSpec fromIntent;
        try {
            RecommendationIntent intent = request.getIntent() != null && !request.getIntent().isBlank()
                ? RecommendationIntent.valueOf(request.getIntent()) : RecommendationIntent.GENERAL;
            fromIntent = RecommendationSpec.fromIntent(intent);
        } catch (Exception e) {
            fromIntent = RecommendationSpec.defaults();
        }
        return RecommendationSpec.builder()
            .maker(request.getMaker())
            .modelFilter(request.getModelFilter())
            .bodyTypeFilter(request.getBodyTypeFilter())
            .yearMin(request.getMinYear())
            .yearMax(request.getMaxYear())
            .preferredYear(request.getPreferredYear())
            .priceMin(request.getMinPrice())
            .priceMax(request.getMaxPrice())
            .fuel(request.getFuel())
            .weightLowPrice(fromIntent.getWeightLowPrice())
            .weightLowMileage(fromIntent.getWeightLowMileage())
            .weightFreshness(fromIntent.getWeightFreshness())
            .weightSafetyProxy(fromIntent.getWeightSafetyProxy())
            .weightValueForMoney(fromIntent.getWeightValueForMoney())
            .build();
    }
    
    /** 후보에 스코어 부여 후 상위 N건 반환. queryMatchedModelCodes에 포함된 모델이면 보너스로 상위 노출. */
    private List<RecommendationResponse.RecommendedCar> scoreAndTakeTop(
            List<RecommendationResponse.RecommendedCar> candidates,
            RecommendationSpec spec,
            int topN,
            Set<String> queryMatchedModelCodes,
            Map<Long, String> carIdToModelCode) {
        if (candidates.isEmpty()) return List.of();
        boolean noWeights = spec.getWeightLowPrice() == 0 && spec.getWeightLowMileage() == 0
                && spec.getWeightFreshness() == 0 && spec.getWeightSafetyProxy() == 0 && spec.getWeightValueForMoney() == 0;
        if (noWeights) {
            // 가중치 없어도 모델 매칭만 적용해 쿼리와 맞는 모델(스포츠카→911 등)을 맨 앞에
            if (queryMatchedModelCodes != null && !queryMatchedModelCodes.isEmpty() && carIdToModelCode != null) {
                for (RecommendationResponse.RecommendedCar c : candidates) {
                    double s = c.getRelevanceScore() != null ? c.getRelevanceScore() : 0.0;
                    if (c.getCarId() != null) {
                        String code = carIdToModelCode.get(c.getCarId());
                        if (code != null && queryMatchedModelCodes.contains(code)) s += 1.0;
                    }
                    c.setRelevanceScore(Math.min(2.0, s));
                }
                candidates.sort((a, b) -> {
                    boolean aMatch = a.getCarId() != null && queryMatchedModelCodes.contains(carIdToModelCode.get(a.getCarId()));
                    boolean bMatch = b.getCarId() != null && queryMatchedModelCodes.contains(carIdToModelCode.get(b.getCarId()));
                    if (aMatch != bMatch) return aMatch ? -1 : 1;
                    return Double.compare(
                        (b.getRelevanceScore() != null ? b.getRelevanceScore() : 0.0),
                        (a.getRelevanceScore() != null ? a.getRelevanceScore() : 0.0));
                });
            }
            int n = Math.min(topN, candidates.size());
            return new ArrayList<>(candidates.subList(0, n));
        }
        int minPrice = candidates.stream().map(RecommendationResponse.RecommendedCar::getPrice).filter(p -> p != null).mapToInt(Integer::intValue).min().orElse(0);
        int maxPrice = candidates.stream().map(RecommendationResponse.RecommendedCar::getPrice).filter(p -> p != null).mapToInt(Integer::intValue).max().orElse(1);
        int minMileage = candidates.stream().map(RecommendationResponse.RecommendedCar::getMileage).filter(m -> m != null).mapToInt(Integer::intValue).min().orElse(0);
        int maxMileage = candidates.stream().map(RecommendationResponse.RecommendedCar::getMileage).filter(m -> m != null).mapToInt(Integer::intValue).max().orElse(1);
        int minYear = candidates.stream().map(RecommendationResponse.RecommendedCar::getYear).filter(y -> y != null).mapToInt(Integer::intValue).min().orElse(2000);
        int maxYear = candidates.stream().map(RecommendationResponse.RecommendedCar::getYear).filter(y -> y != null).mapToInt(Integer::intValue).max().orElse(2030);
        double rangePrice = maxPrice - minPrice; if (rangePrice <= 0) rangePrice = 1;
        double rangeMileage = maxMileage - minMileage; if (rangeMileage <= 0) rangeMileage = 1;
        double rangeYear = maxYear - minYear; if (rangeYear <= 0) rangeYear = 1;
        
        Integer preferredYear = spec.getPreferredYear();
        for (RecommendationResponse.RecommendedCar c : candidates) {
            double normPrice = c.getPrice() != null ? (double)(c.getPrice() - minPrice) / rangePrice : 0.5;
            double normMileage = c.getMileage() != null ? (double)(c.getMileage() - minMileage) / rangeMileage : 0.5;
            double normYear = c.getYear() != null ? (double)(c.getYear() - minYear) / rangeYear : 0.5;
            double score = 0.0;
            score += spec.getWeightLowPrice() * (1.0 - normPrice);
            score += spec.getWeightLowMileage() * (1.0 - normMileage);
            score += spec.getWeightFreshness() * normYear;
            score += spec.getWeightSafetyProxy() * (normYear * 0.5 + (1.0 - normMileage) * 0.5);
            score += spec.getWeightValueForMoney() * ((1.0 - normPrice) * 0.4 + (1.0 - normMileage) * 0.3 + normYear * 0.3);
            if (preferredYear != null && c.getYear() != null && c.getYear().equals(preferredYear)) {
                score += 0.5;
            }
            // 모델 컬렉션 매칭 보너스: "스포츠카" 요청 시 911 등만 상위 노출 (올란도/스파크 등 비매칭은 뒤로)
            if (c.getCarId() != null && queryMatchedModelCodes != null && !queryMatchedModelCodes.isEmpty() && carIdToModelCode != null) {
                String modelCode = carIdToModelCode.get(c.getCarId());
                if (modelCode != null && queryMatchedModelCodes.contains(modelCode)) {
                    score += 1.0; // 보너스 확대해 매칭 차량이 비매칭(올란도·스파크)보다 항상 위로
                }
            }
            c.setRelevanceScore(Math.min(2.0, Math.max(0.0, score)));
        }
        // 1순위: 모델 컬렉션 매칭 여부(매칭 먼저), 2순위: 점수
        boolean hasModelMatch = queryMatchedModelCodes != null && !queryMatchedModelCodes.isEmpty() && carIdToModelCode != null;
        candidates.sort((a, b) -> {
            if (hasModelMatch) {
                boolean aMatch = a.getCarId() != null && queryMatchedModelCodes.contains(carIdToModelCode.get(a.getCarId()));
                boolean bMatch = b.getCarId() != null && queryMatchedModelCodes.contains(carIdToModelCode.get(b.getCarId()));
                if (aMatch != bMatch) return aMatch ? -1 : 1; // 매칭된 쪽이 앞
            }
            return Double.compare(
                (b.getRelevanceScore() != null ? b.getRelevanceScore() : 0.0),
                (a.getRelevanceScore() != null ? a.getRelevanceScore() : 0.0));
        });
        int n = Math.min(topN, candidates.size());
        return new ArrayList<>(candidates.subList(0, n));
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
        
        // 제조사 필터 (표준명/영문 동시 허용: "볼보" 요청 시 "VOLVO" 메타데이터도 통과)
        if (request.getMaker() != null && !request.getMaker().isBlank()) {
            String reqMaker = request.getMaker().trim();
            String resMaker = result.getMaker() != null ? result.getMaker().trim() : "";
            if (resMaker.isEmpty()) return false;
            if (resMaker.equalsIgnoreCase(reqMaker)) { /* 통과 */ }
            else if ("볼보".equalsIgnoreCase(reqMaker) && "VOLVO".equalsIgnoreCase(resMaker)) { /* 통과 */ }
            else if ("VOLVO".equalsIgnoreCase(reqMaker) && "볼보".equalsIgnoreCase(resMaker)) { /* 통과 */ }
            else return false;
        }
        
        // 연료 타입 필터 (가솔린 ↔ 휘발유 동의어 처리)
        if (request.getFuel() != null && !request.getFuel().isBlank()) {
            String want = request.getFuel().trim();
            String res = result.getFuel() != null ? result.getFuel().trim() : "";
            if (res.isEmpty()) return false;
            boolean match = want.equalsIgnoreCase(res);
            if (!match && "가솔린".equalsIgnoreCase(want)) match = "휘발유".equalsIgnoreCase(res);
            if (!match && "휘발유".equalsIgnoreCase(want)) match = "가솔린".equalsIgnoreCase(res);
            if (!match) return false;
        }
        
        // 연식: 기존 메타데이터 year 그대로 활용
        if (request.getMaxYear() != null && result.getYear() != null && result.getYear() > request.getMaxYear()) {
            return false;
        }
        if (request.getMinYear() != null && result.getYear() != null && result.getYear() < request.getMinYear()) {
            return false;
        }
        
        // 차종 필터 (소형, 경차 등)
        if (request.getBodyTypeFilter() != null && !request.getBodyTypeFilter().isBlank()) {
            String want = request.getBodyTypeFilter().trim();
            String cat = result.getBodyTypeCategory();
            String raw = result.getBodyType();
            if ((cat == null || !want.equalsIgnoreCase(cat)) && (raw == null || !raw.toLowerCase().contains(want.toLowerCase()))) {
                return false;
            }
        }
        
        // 판매 중인 차량만 (임베딩 조건과 동일: ONSALE 또는 ENCAR+ADVERTISE)
        if (result.getStatus() == null || result.getStatus().isBlank()) {
            return false;
        }
        boolean onSale = "ONSALE".equalsIgnoreCase(result.getStatus());
        boolean encarAdvertise = "ADVERTISE".equalsIgnoreCase(result.getStatus())
            && "ENCAR".equalsIgnoreCase(result.getPlatformName());
        if (!onSale && !encarAdvertise) {
            return false;
        }
        
        return true;
    }
    
    /** Chroma where: 기존 메타데이터 전부 활용 (maker, bodyTypeCategory, year) - 스키마 변경 없음 */
    private Map<String, Object> buildChromaWhere(RecommendationRequest request) {
        List<Map<String, Object>> conditions = new ArrayList<>();
        if (request.getMaker() != null && !request.getMaker().isBlank()) {
            String maker = request.getMaker().trim();
            if ("볼보".equalsIgnoreCase(maker) || "VOLVO".equalsIgnoreCase(maker)) {
                conditions.add(Map.of("maker", Map.of("$in", List.of("볼보", "VOLVO"))));
            } else {
                conditions.add(Map.of("maker", Map.of("$eq", maker)));
            }
        }
        if (request.getBodyTypeFilter() != null && !request.getBodyTypeFilter().isBlank()) {
            conditions.add(Map.of("bodyTypeCategory", Map.of("$eq", request.getBodyTypeFilter().trim())));
        }
        if (request.getMaxYear() != null) {
            conditions.add(Map.of("year", Map.of("$lte", request.getMaxYear())));
        }
        if (request.getMinYear() != null) {
            conditions.add(Map.of("year", Map.of("$gte", request.getMinYear())));
        }
        // 연료: 메타데이터 fuel (가솔린 요청 시 휘발유 동의어 포함)
        if (request.getFuel() != null && !request.getFuel().isBlank()) {
            String fuel = request.getFuel().trim();
            if ("가솔린".equals(fuel)) {
                conditions.add(Map.of("fuel", Map.of("$in", List.of("가솔린", "휘발유"))));
            } else {
                conditions.add(Map.of("fuel", Map.of("$eq", fuel)));
            }
        }
        if (conditions.isEmpty()) return null;
        if (conditions.size() == 1) return conditions.get(0);
        return Map.of("$and", conditions);
    }

    private static String getString(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v != null ? v.toString().trim() : null;
    }

    private static Integer getInt(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }
}

package com.carizon.rag.service;

import com.carizon.domain.mapper.CarMapper;
import com.carizon.rag.config.RagProperties;
import com.carizon.rag.dto.RecommendationIntent;
import com.carizon.rag.dto.RecommendationQueryPlan;
import com.carizon.rag.dto.RecommendationRequest;
import com.carizon.rag.dto.RecommendationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 팩터 추출 + 검색엔진(ES) 기반 차량 추천 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarRecommendationService {
    private static final Pattern ALPHA_NUMERIC_MODEL_PATTERN = Pattern.compile("(?i).*([a-z]+\\d+|\\d+[a-z]+).*");
    private static final Set<String> KNOWN_MODEL_TOKENS = Set.of(
            "GV80", "GV70", "GV60", "G80", "G70", "G90", "XC90", "XC60", "XC40", "XC30", "S90", "V90", "S60", "V60", "C40"
    );
    private static final String[] OPTION_QUERY_KEYWORDS = {
            "선루프", "썬루프", "파노라마", "통풍시트", "열선시트", "전동시트", "메모리시트",
            "후방카메라", "어라운드뷰", "내비", "네비", "hud", "헤드업", "스마트크루즈",
            "크루즈", "차선이탈", "반자율", "파워트렁크", "전동트렁크", "4wd", "4륜"
    };
    private static final Set<String> GENERIC_MODEL_FILTERS = Set.of(
            "소형", "중형", "대형", "경차", "준중형", "기타", "suv", "스포츠카", "상용", "rv", "트럭", "승합", "화물",
            "세단", "해치백", "왜건", "쿠페", "컨버터블", "픽업", "미니밴", "밴"
    );
    
    private final RagSearchService ragSearchService;
    private final LlmService llmService;
    private final RagProperties ragProperties;
    private final LlmConfigService llmConfigService;
    private final RecommendationPhraseService recommendationPhraseService;
    private final RecommendationQueryPlannerService queryPlannerService;
    private final RecommendationElasticsearchService recommendationElasticsearchService;
    private final CarMapper carMapper;
    
    /**
     * 사용자 요구사항을 기반으로 차량 추천 (retrieval-only)
     */
    public RecommendationResponse recommendCars(RecommendationRequest request) throws IOException {
        long totalStart = System.currentTimeMillis();
        log.info("[recommendation] service start");
        log.info("[recommendation] request: {}", request);
        boolean useLlm = request.getUseLlm() == null || request.getUseLlm();
        
        // 기본값 설정
        if (request.getMaxResults() == null) {
            request.setMaxResults(5);
        }
        int maxResults = request.getMaxResults() != null ? request.getMaxResults() : 5;

        // 1) Query Planner (휴리스틱 구조화 플랜)
        RecommendationQueryPlan queryPlan = null;
        if (request.getQuery() != null && !request.getQuery().isBlank()) {
            long plannerStart = System.currentTimeMillis();
            queryPlan = queryPlannerService.plan(request.getQuery(), useLlm);
            applyPlannedFields(request, queryPlan);
            log.info("[recommendation] query planner done: {}ms, plan={}", System.currentTimeMillis() - plannerStart, queryPlan);
        }

        // 2) 질의에서 팩터 추출 (메이커/차종/연료/연식/의도)
        if (request.getQuery() != null && !request.getQuery().isBlank()) {
            String userQuery = request.getQuery().trim();
            String effectiveQuery = userQuery;
            applyQueryExtractions(request, effectiveQuery);
            log.info(
                    "[recommendation] extracted filters: query='{}', searchQuery='{}', maker='{}', modelFilter='{}', bodyType='{}', excludeBodyType='{}', option='{}', fuel='{}', excludeFuel='{}', color='{}', excludeColor='{}', region='{}', excludeRegion='{}', price=[{},{}], year=[{},{}], km=[{},{}], noAccident={}, noFloodDamage={}, intent={}",
                    request.getQuery(),
                    request.getSearchQuery(),
                    request.getMaker(),
                    request.getModelFilter(),
                    request.getBodyTypeFilter(),
                    request.getExcludeBodyTypeFilter(),
                    request.getOptionFilter(),
                    request.getFuel(),
                    request.getExcludeFuel(),
                    request.getColorFilter(),
                    request.getExcludeColorFilter(),
                    request.getRegionFilter(),
                    request.getExcludeRegionFilter(),
                    request.getMinPrice(),
                    request.getMaxPrice(),
                    request.getMinYear(),
                    request.getMaxYear(),
                    request.getMinKm(),
                    request.getMaxKm(),
                    request.getNoAccident(),
                    request.getNoFloodDamage(),
                    request.getIntent()
            );
        }

        // 3) RAG(Chroma) 후보 검색
        long ragStart = System.currentTimeMillis();
        List<RecommendationResponse.RecommendedCar> ragCars = List.of();
        try {
            ragCars = ragSearchService.searchSimilarCars(request);
        } catch (Exception e) {
            log.warn("[recommendation] RAG search failed, fallback to ES only: {}", e.getMessage());
        }
        long ragMs = System.currentTimeMillis() - ragStart;
        log.info("[recommendation] RAG candidate search done: {}ms, cars={}", ragMs, ragCars.size());

        // 4) 안전한 DSL 빌더 기반 ES 후보 검색
        long esStart = System.currentTimeMillis();
        int esCandidateSize = Math.max(40, maxResults * 10);
        List<RecommendationResponse.RecommendedCar> esCars =
                recommendationElasticsearchService.searchCandidates(request, queryPlan, esCandidateSize);
        long esMs = System.currentTimeMillis() - esStart;
        log.info("[recommendation] ES candidate search done: {}ms, cars={}", esMs, esCars.size());

        // 5) 하이브리드 결합 (RAG 0.65 + ES 0.35)
        List<RecommendationResponse.RecommendedCar> cars = mergeHybridCars(ragCars, esCars, maxResults);
        log.info("[recommendation] hybrid merge done: ragCars={}, esCars={}, merged={}",
                ragCars.size(), esCars.size(), cars.size());

        // 명시 모델어가 있으면 해당 모델이 먼저 보이도록 우선 정렬
        cars = prioritizeModelMatches(cars, request);

        if (!isModelExplicitlyRequested(request, queryPlan)) {
            cars = diversifyByModel(cars, maxResults);
            log.info("[recommendation] diversified by model (model unspecified): cars={}", cars.size());
        }
        if (cars.size() > maxResults) {
            cars = new ArrayList<>(cars.subList(0, maxResults));
        }

        // 표시용 이름 보정: RAG/ES 일부 경로에서 maker/model/trim 누락된 경우 DB 메타데이터로 채움
        backfillDisplayNames(cars);

        if (cars.isEmpty()) {
            log.warn("[recommendation] no cars after ES search/fallback");
            String noCarsMessage = generateNoCarsMessage(request.getQuery());
            return RecommendationResponse.builder()
                    .recommendation(noCarsMessage)
                    .cars(List.of())
                    .build();
        }

        // 차량별 한 줄 이유는 문구 뱅크(조건·조합)로 부여.
        long reasonsStart = System.currentTimeMillis();
        cars = applyPhraseReasonsToCars(cars);
        long reasonsMs = System.currentTimeMillis() - reasonsStart;
        String recommendation = generateOverallRecommendation(request, cars, useLlm);
        long totalMs = System.currentTimeMillis() - totalStart;
        log.info("[recommendation] total: {}ms (RAG={}ms, ES={}ms, reasons={}ms)", totalMs, ragMs, esMs, reasonsMs);
        
        RecommendationResponse response = RecommendationResponse.builder()
                .recommendation(recommendation)
                .cars(cars)
                .build();
        
        log.info("[recommendation] response done: {} cars", response.getCars().size());
        for (int i = 0; i < response.getCars().size(); i++) {
            RecommendationResponse.RecommendedCar car = response.getCars().get(i);
            int scorePoints = car.getRelevanceScore() != null ? (int) Math.round(car.getRelevanceScore() * 100) : -1;
            log.info("  [{}] carId={}, Carizon 점수 {}점, price={} manwon, reason={}", 
                i + 1,
                car.getCarId(),
                scorePoints >= 0 ? scorePoints : "N/A",
                car.getPrice(),
                car.getReason());
        }
        
        return response;
    }

    private List<RecommendationResponse.RecommendedCar> prioritizeModelMatches(
            List<RecommendationResponse.RecommendedCar> cars,
            RecommendationRequest request
    ) {
        if (cars == null || cars.isEmpty() || request == null) return cars;
        String token = trimOrNull(request.getModelFilter());
        if (token == null) return cars;
        String lowerToken = token.toLowerCase();
        if (GENERIC_MODEL_FILTERS.contains(lowerToken)) return cars;

        List<RecommendationResponse.RecommendedCar> matched = new ArrayList<>();
        List<RecommendationResponse.RecommendedCar> others = new ArrayList<>();
        for (RecommendationResponse.RecommendedCar car : cars) {
            String model = trimOrNull(car.getModel());
            String trim = trimOrNull(car.getTrim());
            boolean hit =
                    (model != null && model.toLowerCase().contains(lowerToken)) ||
                    (trim != null && trim.toLowerCase().contains(lowerToken));
            if (hit) matched.add(car);
            else others.add(car);
        }
        if (matched.isEmpty()) return cars;

        List<RecommendationResponse.RecommendedCar> reordered = new ArrayList<>(cars.size());
        reordered.addAll(matched);
        reordered.addAll(others);
        return reordered;
    }

    private void backfillDisplayNames(List<RecommendationResponse.RecommendedCar> cars) {
        if (cars == null || cars.isEmpty()) return;
        for (RecommendationResponse.RecommendedCar car : cars) {
            if (car == null || car.getCarId() == null) continue;
            boolean needMaker = trimOrNull(car.getMaker()) == null;
            boolean needModel = trimOrNull(car.getModel()) == null;
            boolean needTrim = trimOrNull(car.getTrim()) == null;
            if (!needMaker && !needModel && !needTrim) continue;
            try {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("carId", car.getCarId());
                List<Map<String, Object>> rows = carMapper.selectCarsForIndexingById(p);
                if (rows == null || rows.isEmpty()) continue;
                Map<String, Object> row = rows.get(0);
                if (needMaker) car.setMaker(firstNonBlank(car.getMaker(), str(row.get("makerName"))));
                if (needModel) car.setModel(firstNonBlank(car.getModel(), str(row.get("modelName"))));
                if (needTrim) car.setTrim(firstNonBlank(car.getTrim(), str(row.get("trimName"))));
            } catch (Exception e) {
                log.debug("[recommendation] display name backfill skipped for carId={}: {}", car.getCarId(), e.getMessage());
            }
        }
    }
    
    /**
     * 매물 0건일 때: 고정 안내 문구 반환.
     */
    private String generateNoCarsMessage(String query) {
        String q = query != null ? query.trim() : "";
        if (!q.isEmpty()) {
            return "텍스트 검색 결과가 없어 조건 기반으로 재검색했지만 매물을 찾지 못했습니다. 검색어를 더 짧게 입력하거나 가격·연식·차종 조건을 완화해 주세요.";
        }
        return "요청하신 조건에 맞는 차량을 찾지 못했습니다. 가격·연식·차종 조건을 완화해 다시 검색해 주세요.";
    }
    
    /**
     * 결과 요약 안내 문구.
     * - 기본: 고정 템플릿
     * - useLlm=true && explainer enabled: 이미 선택된 TOP N을 설명만 수행
     */
    private String generateOverallRecommendation(RecommendationRequest request,
                                                 List<RecommendationResponse.RecommendedCar> cars,
                                                 boolean useLlm) throws IOException {
        int count = cars != null ? cars.size() : 0;
        String fallback = "Carizon AI 매물 추천 결과입니다. 총 " + count + "건을 확인해 보세요.";
        boolean enabled = ragProperties.getRecommendation().getExplainer().isEnabled();
        if (!useLlm || !enabled || cars == null || cars.isEmpty()) {
            return fallback;
        }

        try {
            int timeoutMs = Math.max(3000, ragProperties.getRecommendation().getExplainer().getTimeoutMs());
            String prompt = buildRecommendationExplainerPrompt(request, cars);
            String raw = llmService.generateResponse(
                    prompt,
                    new LlmService.GenerationOptions(
                            260,
                            0.2,
                            timeoutMs,
                            "You are a factual explainer. Use only the provided candidates. Answer in Korean."
                    )
            );
            String text = raw != null ? raw.trim() : "";
            if (!text.isBlank()) {
                return text;
            }
        } catch (Exception e) {
            log.warn("[recommendation] explainer failed, fallback fixed text: {}", e.getMessage());
        }
        return fallback;
    }

    private static String buildRecommendationExplainerPrompt(
            RecommendationRequest request,
            List<RecommendationResponse.RecommendedCar> cars
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("아래는 이미 선택된 추천 결과입니다. 주어진 정보만 사용해 설명하세요.\\n");
        sb.append("출력 형식:\\n");
        sb.append("1) 추천 요약 1~2문장\\n");
        sb.append("2) 상위 차량 공통 강점 2~3개\\n");
        sb.append("3) 탈락 기준(일반) 1문장: 조건 미일치/점수 낮음 관점\\n");
        sb.append("주의: 목록에 없는 차량/사양/수치는 절대 언급 금지.\\n\\n");
        sb.append("사용자 요청:\\n");
        sb.append(request != null ? firstNonBlank(request.getQuery(), request.getSearchQuery()) : "").append("\\n\\n");
        sb.append("선정 차량 목록:\\n");
        int idx = 1;
        for (RecommendationResponse.RecommendedCar car : cars) {
            if (car == null) continue;
            sb.append(idx++).append(") ")
                    .append(firstNonBlank(car.getMaker(), "-")).append(" ")
                    .append(firstNonBlank(car.getModel(), "-")).append(" ")
                    .append(firstNonBlank(car.getTrim(), "")).append(" / ")
                    .append(car.getYear() != null ? car.getYear() + "년식" : "연식미상").append(" / ")
                    .append(car.getMileage() != null ? car.getMileage() + "km" : "주행거리미상").append(" / ")
                    .append(car.getPrice() != null ? car.getPrice() + "만원" : "가격미상").append(" / ")
                    .append("연료=").append(firstNonBlank(car.getFuel(), "-")).append(" / ")
                    .append("지역=").append(firstNonBlank(car.getRegion(), "-"))
                    .append("\\n");
        }
        return sb.toString();
    }
    
    /** 차량별 추천 이유는 문구 뱅크(조건·조합)로 부여. 없으면 default-message 사용 */
    private List<RecommendationResponse.RecommendedCar> applyPhraseReasonsToCars(
            List<RecommendationResponse.RecommendedCar> cars) {
        String defaultMessage = llmConfigService.getPrompt("default-message");
        for (RecommendationResponse.RecommendedCar car : cars) {
            String reason = recommendationPhraseService.getReasonForCar(car);
            car.setReason(reason != null && !reason.isBlank() ? reason.trim() : defaultMessage);
        }
        return cars;
    }

    /**
     * Query Planner 결과를 RecommendationRequest에 반영.
     * 기존 사용자가 명시한 필드가 있으면 덮어쓰지 않는다.
     */
    private static void applyPlannedFields(RecommendationRequest request, RecommendationQueryPlan plan) {
        if (request == null || plan == null) return;
        if ((request.getSearchQuery() == null || request.getSearchQuery().isBlank())
                && plan.getTextQuery() != null && !plan.getTextQuery().isBlank()) {
            request.setSearchQuery(plan.getTextQuery().trim());
        }
        if ((request.getMaker() == null || request.getMaker().isBlank())
                && plan.getMaker() != null && !plan.getMaker().isBlank()) {
            request.setMaker(plan.getMaker().trim());
        }
        if ((request.getModelFilter() == null || request.getModelFilter().isBlank())
                && plan.getModel() != null && !plan.getModel().isBlank()) {
            request.setModelFilter(plan.getModel().trim());
        }
        if ((request.getBodyTypeFilter() == null || request.getBodyTypeFilter().isBlank())
                && plan.getBodyTypes() != null && !plan.getBodyTypes().isEmpty()) {
            request.setBodyTypeFilter(String.join(",", plan.getBodyTypes()));
        }
        if ((request.getFuel() == null || request.getFuel().isBlank())
                && plan.getFuel() != null && !plan.getFuel().isBlank()) {
            request.setFuel(plan.getFuel());
        }
        if ((request.getExcludeFuel() == null || request.getExcludeFuel().isBlank())
                && plan.getExcludeFuel() != null && !plan.getExcludeFuel().isBlank()) {
            request.setExcludeFuel(plan.getExcludeFuel());
        }
        if ((request.getColorFilter() == null || request.getColorFilter().isBlank())
                && plan.getColor() != null && !plan.getColor().isBlank()) {
            request.setColorFilter(plan.getColor());
        }
        if ((request.getExcludeColorFilter() == null || request.getExcludeColorFilter().isBlank())
                && plan.getExcludeColor() != null && !plan.getExcludeColor().isBlank()) {
            request.setExcludeColorFilter(plan.getExcludeColor());
        }
        if ((request.getRegionFilter() == null || request.getRegionFilter().isBlank())
                && plan.getRegion() != null && !plan.getRegion().isBlank()) {
            request.setRegionFilter(plan.getRegion());
        }
        if ((request.getExcludeRegionFilter() == null || request.getExcludeRegionFilter().isBlank())
                && plan.getExcludeRegion() != null && !plan.getExcludeRegion().isBlank()) {
            request.setExcludeRegionFilter(plan.getExcludeRegion());
        }
        if ((request.getExcludeBodyTypeFilter() == null || request.getExcludeBodyTypeFilter().isBlank())
                && plan.getExcludeBodyTypes() != null && !plan.getExcludeBodyTypes().isEmpty()) {
            request.setExcludeBodyTypeFilter(String.join(",", plan.getExcludeBodyTypes()));
        }
        if (request.getMinPrice() == null && plan.getMinPrice() != null) request.setMinPrice(plan.getMinPrice());
        if (request.getMaxPrice() == null && plan.getMaxPrice() != null) request.setMaxPrice(plan.getMaxPrice());
        if (request.getMinYear() == null && plan.getMinYear() != null) request.setMinYear(plan.getMinYear());
        if (request.getMaxYear() == null && plan.getMaxYear() != null) request.setMaxYear(plan.getMaxYear());
        if (request.getMinKm() == null && plan.getMinKm() != null) request.setMinKm(plan.getMinKm());
        if (request.getMaxKm() == null && plan.getMaxKm() != null) request.setMaxKm(plan.getMaxKm());
        if ((request.getIntent() == null || request.getIntent().isBlank())
                && plan.getIntent() != null && !plan.getIntent().isBlank()) {
            request.setIntent(plan.getIntent());
        }
    }

    /**
     * 하이브리드 결합:
     * - 동일 carId 병합
     * - RAG 점수 + ES 점수 가중합 (둘 다 있으면 0.65/0.35)
     * - maxResults 개수로 자름
     */
    private List<RecommendationResponse.RecommendedCar> mergeHybridCars(
            List<RecommendationResponse.RecommendedCar> ragCars,
            List<RecommendationResponse.RecommendedCar> esCars,
            int maxResults
    ) {
        Map<Long, HybridAccumulator> acc = new LinkedHashMap<>();

        if (ragCars != null) {
            int size = Math.max(1, ragCars.size());
            for (int i = 0; i < ragCars.size(); i++) {
                RecommendationResponse.RecommendedCar car = ragCars.get(i);
                if (car == null || car.getCarId() == null) continue;
                HybridAccumulator a = acc.computeIfAbsent(car.getCarId(), k -> new HybridAccumulator(copyCar(car)));
                a.ragScore = clampScore(firstNonNull(car.getRelevanceScore(), rankScore(i, size)));
                a.car = mergeCarInfo(a.car, car);
            }
        }

        if (esCars != null) {
            int size = Math.max(1, esCars.size());
            for (int i = 0; i < esCars.size(); i++) {
                RecommendationResponse.RecommendedCar car = esCars.get(i);
                if (car == null || car.getCarId() == null) continue;
                HybridAccumulator a = acc.computeIfAbsent(car.getCarId(), k -> new HybridAccumulator(copyCar(car)));
                a.esScore = clampScore(firstNonNull(car.getRelevanceScore(), rankScore(i, size)));
                a.car = mergeCarInfo(a.car, car);
            }
        }

        List<HybridAccumulator> merged = new ArrayList<>(acc.values());
        merged.forEach(HybridAccumulator::finalizeScore);
        merged.sort((a, b) -> Double.compare(b.finalScore, a.finalScore));

        List<RecommendationResponse.RecommendedCar> out = new ArrayList<>();
        int limit = Math.max(1, maxResults);
        for (HybridAccumulator h : merged) {
            if (out.size() >= limit) break;
            h.car.setRelevanceScore(h.finalScore);
            out.add(h.car);
        }
        return out;
    }

    private static RecommendationResponse.RecommendedCar mergeCarInfo(
            RecommendationResponse.RecommendedCar base,
            RecommendationResponse.RecommendedCar incoming
    ) {
        if (base == null) return copyCar(incoming);
        if (incoming == null) return base;
        base.setMaker(firstNonBlank(base.getMaker(), incoming.getMaker()));
        base.setModel(firstNonBlank(base.getModel(), incoming.getModel()));
        base.setTrim(firstNonBlank(base.getTrim(), incoming.getTrim()));
        base.setYear(firstNonNull(base.getYear(), incoming.getYear()));
        base.setMileage(firstNonNull(base.getMileage(), incoming.getMileage()));
        base.setPrice(firstNonNull(base.getPrice(), incoming.getPrice()));
        base.setFuel(firstNonBlank(base.getFuel(), incoming.getFuel()));
        base.setTransmission(firstNonBlank(base.getTransmission(), incoming.getTransmission()));
        base.setColor(firstNonBlank(base.getColor(), incoming.getColor()));
        base.setRegion(firstNonBlank(base.getRegion(), incoming.getRegion()));
        base.setPcUrl(firstNonBlank(base.getPcUrl(), incoming.getPcUrl()));
        base.setMUrl(firstNonBlank(base.getMUrl(), incoming.getMUrl()));
        base.setUrl(firstNonBlank(base.getUrl(), incoming.getUrl()));
        base.setImageUrl(firstNonBlank(base.getImageUrl(), incoming.getImageUrl()));
        return base;
    }

    private static RecommendationResponse.RecommendedCar copyCar(RecommendationResponse.RecommendedCar c) {
        if (c == null) return null;
        return RecommendationResponse.RecommendedCar.builder()
                .carId(c.getCarId())
                .maker(c.getMaker())
                .model(c.getModel())
                .trim(c.getTrim())
                .year(c.getYear())
                .mileage(c.getMileage())
                .price(c.getPrice())
                .fuel(c.getFuel())
                .transmission(c.getTransmission())
                .color(c.getColor())
                .region(c.getRegion())
                .url(c.getUrl())
                .pcUrl(c.getPcUrl())
                .mUrl(c.getMUrl())
                .imageUrl(c.getImageUrl())
                .relevanceScore(c.getRelevanceScore())
                .reason(c.getReason())
                .build();
    }

    private static double rankScore(int index, int size) {
        if (size <= 1) return 1.0;
        return Math.max(0.05, 1.0 - ((double) index / (double) size));
    }

    private static double clampScore(Double v) {
        if (v == null || v.isNaN() || v.isInfinite()) return 0.0;
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static <T> T firstNonNull(T a, T b) {
        return a != null ? a : b;
    }

    private static String firstNonBlank(String a, String b) {
        String aa = trimOrNull(a);
        return aa != null ? aa : trimOrNull(b);
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static class HybridAccumulator {
        private RecommendationResponse.RecommendedCar car;
        private double ragScore;
        private double esScore;
        private double finalScore;

        private HybridAccumulator(RecommendationResponse.RecommendedCar car) {
            this.car = car;
        }

        private void finalizeScore() {
            boolean hasRag = ragScore > 0;
            boolean hasEs = esScore > 0;
            if (hasRag && hasEs) {
                this.finalScore = (ragScore * 0.65) + (esScore * 0.35);
            } else if (hasRag) {
                this.finalScore = ragScore;
            } else if (hasEs) {
                this.finalScore = esScore;
            } else {
                this.finalScore = 0.0;
            }
        }
    }
    
    /** 쿼리 한 번에 전부 반영: 문서키워드, 차종, 연식, 메이커 등 (기존 메타데이터·임베딩 전부 활용) */
    private static void applyQueryExtractions(RecommendationRequest request, String query) {
        if (query == null || query.isBlank()) return;
        String lower = query.toLowerCase();
        // 1) 메이커
        if ((request.getMaker() == null || request.getMaker().isBlank()) && (lower.contains("볼보") || lower.contains("volvo"))) {
            request.setMaker("볼보");
        }
        // 2) 차종 (bodyTypeCategory) - 메타데이터 그대로 사용
        if (request.getBodyTypeFilter() == null || request.getBodyTypeFilter().isBlank()) {
            String body = extractBodyTypeFilter(query);
            if (body != null) request.setBodyTypeFilter(body);
        }
        // 2-1) 옵션 필터 (optionArray / selOptionArray)
        if (request.getOptionFilter() == null || request.getOptionFilter().isBlank()) {
            String option = extractOptionFilter(query);
            if (option != null) request.setOptionFilter(option);
        }
        // 2-2) 사고/침수 선호
        if (request.getNoAccident() == null) {
            request.setNoAccident(extractNoAccidentPreference(query));
        }
        if (request.getNoFloodDamage() == null) {
            request.setNoFloodDamage(extractNoFloodPreference(query));
        }
        // 3) 연식: "22년식", "2022년" 등 구체적 연도 → preferredYear(해당 연도 우선 노출, 스코어 보너스)
        Integer explicitYear = extractExplicitYear(query);
        if (explicitYear != null) request.setPreferredYear(explicitYear);
        if (request.getMaxYear() == null) {
            Integer maxY = extractMaxYearForOldCars(query);
            if (maxY != null) request.setMaxYear(maxY);
        }
        if (request.getMinYear() == null) {
            Integer minY = extractMinYearForNewCars(query);
            if (minY != null) request.setMinYear(minY);
        }
        // 4) 연료 (가솔린/하이브리드/디젤/LPG/전기) - 메타데이터 fuel 필터
        if (request.getFuel() == null || request.getFuel().isBlank()) {
            String fuel = extractFuelFilter(query);
            if (fuel != null) request.setFuel(fuel);
        }
        // 5) 문서·model·modelGroup 검색용 키워드 (where_document)
        if (request.getModelFilter() == null || request.getModelFilter().isBlank()) {
            String keyword = extractDocumentKeyword(query);
            if (keyword != null) request.setModelFilter(keyword);
        }
        // 6) 의도 → 스코어 가중치 (공통 엔진에서 사용)
        if (request.getIntent() == null || request.getIntent().isBlank()) {
            RecommendationIntent detected = detectIntent(query);
            request.setIntent(detected.name());
        }
    }
    
    /** 쿼리에서 추천 의도 감지 (프리셋 가중치용) */
    private static RecommendationIntent detectIntent(String query) {
        if (query == null || query.isBlank()) return RecommendationIntent.GENERAL;
        String lower = query.toLowerCase();
        if (lower.contains("가성비") || lower.contains("연식 오래") || lower.contains("오래된") || lower.contains("구형") || lower.contains("가격 대비")) return RecommendationIntent.VALUE;
        if (lower.contains("패밀리") || lower.contains("가족") || lower.contains("캠핑") || lower.contains("공간")) return RecommendationIntent.FAMILY;
        if (lower.contains("안전") || lower.contains("신생아") || lower.contains("아기") || lower.contains("아이")) return RecommendationIntent.SAFETY;
        if (lower.contains("데이트") || lower.contains("연인") || lower.contains("20대") || lower.contains("연비") || lower.contains("유지비")) return RecommendationIntent.DATE;
        if (lower.contains("출퇴근") || lower.contains("통근") || lower.contains("연비")) return RecommendationIntent.COMMUTE;
        if (lower.contains("저예산") || lower.contains("싼") || lower.contains("저렴") || lower.contains("예산 적")) return RecommendationIntent.LOW_BUDGET;
        return RecommendationIntent.GENERAL;
    }
    
    /** 문서 검색용 키워드: 차량명·차종·첫 단어. 긴 모델명 먼저 (GV80 → G80 순으로 매칭) */
    private static String extractDocumentKeyword(String query) {
        if (query == null || query.isBlank()) return null;
        String q = query.trim();
        String upper = q.toUpperCase();
        // 제네시스·볼보 등: 부분문자열 있는 모델은 긴 것부터 (GV80 before G80)
        String[] knownModels = { "GV80", "GV70", "GV60", "G80", "G70", "G90", "XC90", "XC60", "XC40", "XC30", "S90", "V90", "S60", "V60", "C40" };
        for (String m : knownModels) {
            if (upper.contains(m)) return m;
        }
        String body = extractBodyTypeFilter(q);
        if (body != null) return body;
        String removed = q.replaceAll("(?i)(추천|해줘|해달라|해주세요|부탁|해주|주세요|원해|하고\\s*싶|만원|이하|이상)", " ").trim();
        String[] tokens = removed.split("\\s+");
        for (String t : tokens) {
            String s = t.trim();
            if (s.length() >= 2 && s.length() <= 20 && !s.matches("^[0-9]+$")) return s;
        }
        return null;
    }
    
    /** 연료 필터 - 쿼리에서 가솔린/하이브리드/디젤/LPG/전기 추출 (메타데이터 fuel·Chroma where용) */
    private static String extractFuelFilter(String query) {
        if (query == null || query.isBlank()) return null;
        String lower = query.toLowerCase();
        if (lower.contains("가솔린") || lower.contains("gasoline") || lower.contains("휘발유")) return "가솔린";
        if (lower.contains("하이브리드") || lower.contains("hybrid")) return "하이브리드";
        if (lower.contains("디젤") || lower.contains("diesel")) return "디젤";
        if (lower.contains("전기") || lower.contains("electric") || lower.contains(" ev ") || lower.contains("ev차")) return "전기";
        if (lower.contains("lpg") || lower.contains("엘피지")) return "LPG";
        return null;
    }

    private static String extractOptionFilter(String query) {
        if (query == null || query.isBlank()) return null;
        String lower = query.toLowerCase();
        LinkedHashSet<String> options = new LinkedHashSet<>();
        for (String keyword : OPTION_QUERY_KEYWORDS) {
            if (lower.contains(keyword.toLowerCase())) {
                options.add(keyword);
            }
        }
        if (options.isEmpty()) return null;
        return String.join(",", options);
    }

    private static boolean extractNoAccidentPreference(String query) {
        if (query == null || query.isBlank()) return false;
        String lower = query.toLowerCase();
        if (lower.contains("사고차") || lower.contains("사고 있는")) return false;
        return lower.contains("무사고")
                || lower.contains("사고없")
                || lower.contains("사고 없음")
                || lower.contains("사고없는");
    }

    private static boolean extractNoFloodPreference(String query) {
        if (query == null || query.isBlank()) return false;
        String lower = query.toLowerCase();
        if (lower.contains("침수차")) return false;
        return lower.contains("무침수")
                || lower.contains("침수없")
                || lower.contains("침수 없음")
                || lower.contains("침수없는");
    }
    
    /** 차종 필터 - 메타데이터 bodyTypeCategory와 동일한 값 사용 (소형, 경차, SUV, 세단, 미니밴, 해치백, 왜건) */
    private static String extractBodyTypeFilter(String query) {
        if (query == null || query.isBlank()) return null;
        String lower = query.toLowerCase();
        boolean hasSuv = lower.contains("suv") || lower.contains("에스유비") || lower.contains("스포츠유틸리티");
        boolean hasRv = lower.contains("rv");
        // 패밀리/가족 계열은 차종 미지정 또는 LLM 편향으로 한쪽만 추출되더라도 SUV+RV를 기본값으로 사용
        if (lower.contains("패밀리") || lower.contains("가족")) return "SUV,RV";
        if (hasSuv && hasRv) return "SUV,RV";
        if (hasSuv) return "SUV";
        if (hasRv) return "RV";
        if (lower.contains("대형")) return "대형";
        if (lower.contains("준중형")) return "준중형";
        if (lower.contains("중형")) return "중형";
        if (lower.contains("소형차") || lower.contains("소형")) return "소형";
        if (lower.contains("경차")) return "경차";
        if (lower.contains("스포츠카")) return "스포츠카";
        if (lower.contains("상용")) return "상용";
        if (lower.contains("트럭")) return "트럭";
        if (lower.contains("화물")) return "화물";
        if (lower.contains("승합")) return "승합";
        if (lower.contains("기타")) return "기타";
        if (lower.contains("세단")) return "세단";
        if (lower.contains("미니밴") || lower.contains("승합차") || lower.contains("밴")) return "미니밴";
        if (lower.contains("해치백") || lower.contains("해치")) return "해치백";
        if (lower.contains("왜건")) return "왜건";
        return null;
    }
    
    /** 연식/년식 구체적 연도 추출 - "22년식", "2022년", "2019년식", "19년식" 등 */
    private static Integer extractExplicitYear(String query) {
        if (query == null || query.isBlank()) return null;
        // 4자리 연도: 2015~2030
        java.util.regex.Pattern p4 = java.util.regex.Pattern.compile("(201[5-9]|202[0-9]|2030)\\s*년(식)?");
        java.util.regex.Matcher m4 = p4.matcher(query);
        if (m4.find()) return Integer.parseInt(m4.group(1));
        // 2자리: 22년식→2022, 19년식→2019. 90~99→1990s, 00~29→2000s, 30~89→2030? 보통 1990대
        java.util.regex.Pattern p2 = java.util.regex.Pattern.compile("(\\d{1,2})\\s*년(식)?");
        java.util.regex.Matcher m2 = p2.matcher(query);
        if (m2.find()) {
            int yy = Integer.parseInt(m2.group(1));
            if (yy >= 90) return 1900 + yy;
            if (yy <= 29) return 2000 + yy;
            return 2000 + yy; // 30~89: 2030~2089 무리하므로 2000+로 (2030 등)
        }
        return null;
    }
    
    private static Integer extractMaxYearForOldCars(String query) {
        if (query == null || query.isBlank()) return null;
        String lower = query.toLowerCase();
        if (lower.contains("연식 오래") || lower.contains("오래된 연식") || lower.contains("낡은 연식")
            || lower.contains("오래된 차") || lower.contains("낡은 차") || lower.contains("구형")
            || lower.contains("오래된 거") || lower.contains("예전 연식")) {
            return 2022;
        }
        return null;
    }
    
    private static Integer extractMinYearForNewCars(String query) {
        if (query == null || query.isBlank()) return null;
        String lower = query.toLowerCase();
        if (lower.contains("최신") || lower.contains("신형") || lower.contains("최신형") || lower.contains("최신 차")) {
            return 2024;
        }
        return null;
    }

    private static boolean isModelExplicitlyRequested(RecommendationRequest request, RecommendationQueryPlan queryPlan) {
        if (queryPlan != null) {
            if (trimOrNull(queryPlan.getModelCode()) != null) return true;
            if (trimOrNull(queryPlan.getModel()) != null) return true;
        }

        String modelFilter = trimOrNull(request != null ? request.getModelFilter() : null);
        if (isLikelyModelToken(modelFilter)) return true;

        String query = trimOrNull(request != null ? request.getQuery() : null);
        return isLikelyModelToken(query);
    }

    private static boolean isLikelyModelToken(String raw) {
        String token = trimOrNull(raw);
        if (token == null) return false;
        String upper = token.toUpperCase();
        for (String known : KNOWN_MODEL_TOKENS) {
            if (upper.contains(known)) return true;
        }
        return ALPHA_NUMERIC_MODEL_PATTERN.matcher(token).matches();
    }

    private static List<RecommendationResponse.RecommendedCar> diversifyByModel(
            List<RecommendationResponse.RecommendedCar> cars,
            int maxResults
    ) {
        if (cars == null || cars.isEmpty()) return List.of();

        int limit = Math.max(1, maxResults);
        List<RecommendationResponse.RecommendedCar> selected = new ArrayList<>(limit);
        Set<Long> selectedCarIds = new LinkedHashSet<>();
        Map<String, Integer> selectedModelCount = new LinkedHashMap<>();

        // 1차: 모델 중복 없이 최대한 다양하게 선별
        for (RecommendationResponse.RecommendedCar car : cars) {
            if (car == null || car.getCarId() == null) continue;
            if (selected.size() >= limit) break;

            String modelKey = modelDiversityKey(car);
            if (selectedModelCount.containsKey(modelKey)) continue;

            selected.add(car);
            selectedCarIds.add(car.getCarId());
            selectedModelCount.put(modelKey, 1);
        }

        // 2차: 결과가 너무 적으면 모델당 최대 2개까지 허용
        if (selected.size() < limit) {
            for (RecommendationResponse.RecommendedCar car : cars) {
                if (car == null || car.getCarId() == null) continue;
                if (selected.size() >= limit) break;
                if (selectedCarIds.contains(car.getCarId())) continue;

                String modelKey = modelDiversityKey(car);
                int modelCount = selectedModelCount.getOrDefault(modelKey, 0);
                if (modelCount >= 2) continue;

                selected.add(car);
                selectedCarIds.add(car.getCarId());
                selectedModelCount.put(modelKey, modelCount + 1);
            }
        }

        // 3차: 여전히 부족하면 남은 후보를 순서대로 채움
        if (selected.size() < limit) {
            for (RecommendationResponse.RecommendedCar car : cars) {
                if (car == null || car.getCarId() == null) continue;
                if (selected.size() >= limit) break;
                if (selectedCarIds.contains(car.getCarId())) continue;

                selected.add(car);
                selectedCarIds.add(car.getCarId());
            }
        }
        return selected;
    }

    private static String modelDiversityKey(RecommendationResponse.RecommendedCar car) {
        if (car == null) return "";
        String model = trimOrNull(car.getModel());
        String maker = trimOrNull(car.getMaker());
        if (model == null) {
            if (maker != null) {
                return "maker:" + maker.toUpperCase(java.util.Locale.ROOT);
            }
            return "carId:" + (car.getCarId() != null ? car.getCarId() : "unknown");
        }
        String normalizedModel = model
                .toUpperCase(java.util.Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "");
        String makerKey = maker != null ? maker.toUpperCase(java.util.Locale.ROOT) : "UNKNOWN";
        return "model:" + makerKey + ":" + normalizedModel;
    }

}

package com.carizon.rag.service;

import com.carizon.domain.mapper.CarMapper;
import com.carizon.dto.CarListItemDto;
import com.carizon.rag.dto.RecommendationQueryPlan;
import com.carizon.rag.dto.RecommendationRequest;
import com.carizon.rag.dto.RecommendationResponse;
import com.carizon.search.service.ElasticsearchCarSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 추천용 Elasticsearch 검색 서비스.
 * - Query Planner 결과를 허용 필드만 가진 안전 파라미터로 변환
 * - 텍스트 검색(BM25) 결과를 추천 후보 형태로 반환
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationElasticsearchService {
    private static final Pattern ALPHA_NUMERIC_MODEL_PATTERN = Pattern.compile("(?i).*([a-z]+\\d+|\\d+[a-z]+).*");
    private static final Set<String> KNOWN_MODEL_TOKENS = Set.of(
            "GV80", "GV70", "GV60", "G80", "G70", "G90", "XC90", "XC60", "XC40", "XC30", "S90", "V90", "S60", "V60", "C40"
    );
    private static final String DOMESTIC_MAKER_CODES_CSV = "101,102,103,104,105,189";
    private static final Set<String> BROAD_CATEGORY_TOKENS = Set.of(
            "수입차", "외제차", "국산차", "패밀리카", "가족차", "가성비", "출퇴근", "저주행", "세단", "suv", "rv"
    );
    private static final Set<String> TEXT_QUERY_NOISE_TOKENS = Set.of(
            "추천", "보여줘", "보여주세요", "찾아줘", "찾아주세요", "해주세요", "해줘",
            "위주", "조건", "매물", "차량", "자동차",
            "수입", "수입차", "외제", "외제차", "국산", "국산차",
            "이하", "이상", "미만", "초과", "이내", "언더", "오버",
            "가격", "예산", "만원", "만", "원", "억", "대"
    );
    private static final Set<String> RELAXABLE_FILTER_KEYS = Set.of(
            "makerCode", "makerCodes", "excludeMakerCodes",
            "modelCode",
            "fuel", "excludeFuel",
            "bodyType", "excludeBodyType",
            "color", "excludeColor",
            "region", "excludeRegion",
            "priceMin", "priceMax",
            "yearMin", "yearMax",
            "kmMin", "kmMax",
            "sort"
    );

    private final ElasticsearchCarSearchService elasticsearchCarSearchService;
    private final CarMapper carMapper;

    public List<RecommendationResponse.RecommendedCar> searchCandidates(
            RecommendationRequest request,
            RecommendationQueryPlan plan,
            int candidateSize
    ) {
        Map<String, Object> primaryParams = buildSafeSearchParams(request, plan, candidateSize);
        List<CarListItemDto> items = searchCarItems(primaryParams, "primary");
        Map<String, Object> selectedParams = primaryParams;
        String selectedPhase = "primary";

        if (items.isEmpty()) {
            List<Map<String, Object>> fallbackParams = buildFallbackSearchParams(primaryParams, request);
            for (int i = 0; i < fallbackParams.size(); i++) {
                Map<String, Object> candidateParams = fallbackParams.get(i);
                String phase = "fallback-" + (i + 1);
                List<CarListItemDto> fallbackItems = searchCarItems(candidateParams, phase);
                if (!fallbackItems.isEmpty()) {
                    items = fallbackItems;
                    selectedParams = candidateParams;
                    selectedPhase = phase;
                    break;
                }
            }
        }
        int desiredMinimum = resolveDesiredMinimumHits(request, candidateSize);
        if (items.size() < desiredMinimum
                && shouldTrySparseSupplement(request, selectedParams)
                && !isModelExplicitlyRequested(request, plan)) {
            List<CarListItemDto> supplemented = supplementSparseResults(
                    items,
                    selectedParams,
                    request,
                    candidateSize,
                    desiredMinimum
            );
            if (supplemented.size() > items.size()) {
                items = supplemented;
                selectedPhase = selectedPhase + "+supplement";
            }
        }

        List<RecommendationResponse.RecommendedCar> out = new ArrayList<>();
        int size = Math.max(1, items.size());
        for (int i = 0; i < items.size(); i++) {
            CarListItemDto dto = items.get(i);
            long carId = dto.carId();
            String pcUrl = null;
            String mUrl = null;
            try {
                Map<String, Object> row = carMapper.selectCarUrl(carId);
                if (row != null) {
                    pcUrl = row.get("pcUrl") != null ? String.valueOf(row.get("pcUrl")).trim() : null;
                    mUrl = row.get("mUrl") != null ? String.valueOf(row.get("mUrl")).trim() : null;
                }
            } catch (Exception ignored) {
            }
            String url = (pcUrl != null && !pcUrl.isBlank()) ? pcUrl : mUrl;

            out.add(RecommendationResponse.RecommendedCar.builder()
                    .carId(carId)
                    .maker(dto.maker())
                    .model(dto.model())
                    .trim(dto.trim())
                    .year(dto.year())
                    .mileage(dto.km())
                    .price(resolveDisplayPrice(dto.priceMin(), dto.priceMax()))
                    .fuel(dto.fuel())
                    .region(dto.region())
                    .url(url)
                    .pcUrl(pcUrl)
                    .mUrl(mUrl)
                    .imageUrl(dto.representativeImageUrl())
                    .relevanceScore(rankScore(i, size))
                    .build());
        }
        boolean shouldReRankByIntent = request == null || !Boolean.FALSE.equals(request.getUseLlm());
        if (shouldReRankByIntent) {
            applyIntentReRanking(out, request, plan);
        }
        log.info("[RecommendationES] selectedPhase={}, params={}, hits={}", selectedPhase, selectedParams, out.size());
        return out;
    }

    /**
     * Query Planner 결과 + 요청 필드를 결합해 안전한 검색 파라미터 구성.
     * 허용된 키 외에는 절대 ES로 전달하지 않는다.
     */
    private Map<String, Object> buildSafeSearchParams(
            RecommendationRequest request,
            RecommendationQueryPlan plan,
            int candidateSize
    ) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("page", 0);
        p.put("size", Math.max(20, Math.min(candidateSize, 200)));

        boolean llmMode = request != null && Boolean.TRUE.equals(request.getUseLlm());
        String q = firstNonBlank(
                plan != null ? plan.getTextQuery() : null,
                request != null ? request.getSearchQuery() : null,
                request != null ? request.getQuery() : null
        );
        q = normalizeSearchTextQuery(q);
        if (llmMode && isBroadCategoryOnlyQuery(q) && hasStructuredFilters(plan, request)) {
            q = null;
        }
        if (q != null) p.put("q", q);

        String makerCode = trimOrNull(plan != null ? plan.getMakerCode() : null);
        if (makerCode != null) p.put("makerCode", makerCode);
        applyOriginMustFilter(p, makerCode, request, plan);

        String modelCode = trimOrNull(plan != null ? plan.getModelCode() : null);
        if (modelCode != null) p.put("modelCode", modelCode);

        Integer minPrice = sanitizeMinPrice(firstNonNull(request.getMinPrice(), plan != null ? plan.getMinPrice() : null));
        Integer maxPrice = sanitizeMaxPrice(firstNonNull(request.getMaxPrice(), plan != null ? plan.getMaxPrice() : null));
        if (minPrice != null && maxPrice != null && maxPrice < minPrice) {
            maxPrice = null;
        }
        if (minPrice != null) p.put("priceMin", minPrice);
        if (maxPrice != null) p.put("priceMax", maxPrice);

        Integer minYear = firstNonNull(request != null ? request.getMinYear() : null, plan != null ? plan.getMinYear() : null);
        Integer maxYear = firstNonNull(request != null ? request.getMaxYear() : null, plan != null ? plan.getMaxYear() : null);
        if (minYear != null) p.put("yearMin", clamp(minYear, 1990, 2030));
        if (maxYear != null) p.put("yearMax", clamp(maxYear, 1990, 2030));

        Integer minKm = firstNonNull(request != null ? request.getMinKm() : null, plan != null ? plan.getMinKm() : null);
        Integer maxKm = firstNonNull(request != null ? request.getMaxKm() : null, plan != null ? plan.getMaxKm() : null);
        if (minKm != null) p.put("kmMin", clamp(minKm, 0, 300000));
        if (maxKm != null) {
            int kmMax = clamp(maxKm, 0, 300000);
            p.put("kmMax", kmMax);
            if (kmMax > 0 && kmMax <= 30000 && minKm == null) {
                // "주행거리 짧다" 계열은 비정상 0km 매물을 제외
                p.put("kmMin", 1);
            }
        }

        String fuel = firstNonBlank(
                request != null ? request.getFuel() : null,
                plan != null ? plan.getFuel() : null
        );
        if (fuel != null) p.put("fuel", normalizeFuelCsv(fuel));
        String excludeFuel = firstNonBlank(
                request != null ? request.getExcludeFuel() : null,
                plan != null ? plan.getExcludeFuel() : null
        );
        if (excludeFuel != null) p.put("excludeFuel", normalizeFuelCsv(excludeFuel));

        String bodyType = firstNonBlank(
                request != null ? request.getBodyTypeFilter() : null,
                planBodyTypesCsv(plan)
        );
        if (bodyType == null) {
            String intent = firstNonBlank(
                    request != null ? request.getIntent() : null,
                    plan != null ? plan.getIntent() : null
            );
            if ("FAMILY".equalsIgnoreCase(trimOrNull(intent))) {
                bodyType = "SUV,RV";
            }
        }
        if (bodyType != null) p.put("bodyType", normalizeBodyType(bodyType));
        String excludeBodyType = firstNonBlank(
                request != null ? request.getExcludeBodyTypeFilter() : null,
                planExcludeBodyTypesCsv(plan)
        );
        if (excludeBodyType != null) p.put("excludeBodyType", normalizeBodyType(excludeBodyType));

        String color = firstNonBlank(
                request != null ? request.getColorFilter() : null,
                plan != null ? plan.getColor() : null
        );
        if (color != null) p.put("color", color);
        String excludeColor = firstNonBlank(
                request != null ? request.getExcludeColorFilter() : null,
                plan != null ? plan.getExcludeColor() : null
        );
        if (excludeColor != null) p.put("excludeColor", excludeColor);

        String region = firstNonBlank(
                request != null ? request.getRegionFilter() : null,
                plan != null ? plan.getRegion() : null
        );
        if (region != null) p.put("region", region);
        String excludeRegion = firstNonBlank(
                request != null ? request.getExcludeRegionFilter() : null,
                plan != null ? plan.getExcludeRegion() : null
        );
        if (excludeRegion != null) p.put("excludeRegion", excludeRegion);

        String sort = firstNonBlank(
                trimOrNull(plan != null ? plan.getSort() : null),
                inferDefaultSortByIntent(firstNonBlank(
                        request != null ? request.getIntent() : null,
                        plan != null ? plan.getIntent() : null
                ))
        );
        if (sort != null) p.put("sort", sort.toUpperCase(Locale.ROOT));
        return p;
    }

    private List<CarListItemDto> supplementSparseResults(
            List<CarListItemDto> baseItems,
            Map<String, Object> selectedParams,
            RecommendationRequest request,
            int candidateSize,
            int desiredMinimum
    ) {
        LinkedHashMap<Long, CarListItemDto> merged = new LinkedHashMap<>();
        if (baseItems != null) {
            for (CarListItemDto item : baseItems) {
                if (item == null) continue;
                merged.put(item.carId(), item);
            }
        }

        List<Map<String, Object>> fallbackParams = buildFallbackSearchParams(selectedParams, request);
        int limit = Math.max(1, Math.min(candidateSize, 200));
        for (int i = 0; i < fallbackParams.size(); i++) {
            if (merged.size() >= desiredMinimum || merged.size() >= limit) break;
            Map<String, Object> candidateParams = fallbackParams.get(i);
            if (Objects.equals(selectedParams, candidateParams)) continue;

            List<CarListItemDto> fallbackItems = searchCarItems(candidateParams, "sparse-supplement-" + (i + 1));
            for (CarListItemDto item : fallbackItems) {
                if (item == null) continue;
                merged.putIfAbsent(item.carId(), item);
                if (merged.size() >= desiredMinimum || merged.size() >= limit) break;
            }
        }
        return new ArrayList<>(merged.values());
    }

    private static int resolveDesiredMinimumHits(RecommendationRequest request, int candidateSize) {
        int requested = request != null && request.getMaxResults() != null && request.getMaxResults() > 0
                ? request.getMaxResults() : 5;
        int desired = Math.max(2, requested);
        return Math.max(1, Math.min(desired, Math.max(1, candidateSize)));
    }

    private static boolean shouldTrySparseSupplement(RecommendationRequest request, Map<String, Object> params) {
        if (request != null && Boolean.FALSE.equals(request.getUseLlm())) return false;
        if (params == null || params.isEmpty()) return false;
        String q = trimOrNull(params.get("q") != null ? String.valueOf(params.get("q")) : null);
        return q != null;
    }

    private List<CarListItemDto> searchCarItems(Map<String, Object> params, String phase) {
        long start = System.currentTimeMillis();
        Map<String, Object> result = elasticsearchCarSearchService.search(params);
        long elapsed = System.currentTimeMillis() - start;
        List<CarListItemDto> items = extractCarItems(result.get("content"));
        log.info("[RecommendationES] phase={}, elapsed={}ms, params={}, hits={}", phase, elapsed, params, items.size());
        return items;
    }

    private List<Map<String, Object>> buildFallbackSearchParams(
            Map<String, Object> primaryParams,
            RecommendationRequest request
    ) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (primaryParams == null || primaryParams.isEmpty()) return out;

        String primaryQ = trimOrNull(primaryParams.get("q") != null ? String.valueOf(primaryParams.get("q")) : null);
        String modelFilter = trimOrNull(request != null ? request.getModelFilter() : null);

        // 1) 색상 완화: 색상은 선호 성격이 강해 0건 완화 1순위
        if (primaryParams.containsKey("color") || primaryParams.containsKey("excludeColor")) {
            Map<String, Object> m = new LinkedHashMap<>(primaryParams);
            m.remove("color");
            m.remove("excludeColor");
            addFallbackParam(out, m);
            log.info("[RecommendationES] fallback relax: drop color filters");
        }

        // 2) 지역 완화: 지역은 보조 제약으로 간주
        if (primaryParams.containsKey("region") || primaryParams.containsKey("excludeRegion")) {
            Map<String, Object> m = new LinkedHashMap<>(primaryParams);
            m.remove("region");
            m.remove("excludeRegion");
            addFallbackParam(out, m);
            log.info("[RecommendationES] fallback relax: drop region filters");
        }

        // 3) 연식/주행거리 소폭 완화
        Map<String, Object> widened = widenYearAndKm(primaryParams);
        if (!widened.equals(primaryParams)) {
            addFallbackParam(out, widened);
            log.info("[RecommendationES] fallback relax: widen year/km bounds");
        }

        // 4) 예산 소폭 완화
        Map<String, Object> relaxedPrice = widenPrice(primaryParams);
        if (!relaxedPrice.equals(primaryParams)) {
            addFallbackParam(out, relaxedPrice);
            log.info("[RecommendationES] fallback relax: widen price bounds");
        }

        // 5) 모델 키워드 직접 탐색
        if (primaryQ != null && modelFilter != null && !primaryQ.equalsIgnoreCase(modelFilter)) {
            Map<String, Object> m = new LinkedHashMap<>(primaryParams);
            m.put("q", modelFilter);
            addFallbackParam(out, m);
        }

        // 6) 연료/차종은 의도가 강해서 마지막 단계에서만 완화
        if (primaryParams.containsKey("fuel") || primaryParams.containsKey("excludeFuel")
                || primaryParams.containsKey("bodyType") || primaryParams.containsKey("excludeBodyType")) {
            Map<String, Object> m = new LinkedHashMap<>(primaryParams);
            m.remove("fuel");
            m.remove("excludeFuel");
            m.remove("bodyType");
            m.remove("excludeBodyType");
            addFallbackParam(out, m);
            log.info("[RecommendationES] fallback relax: drop fuel/bodyType filters");
        }

        // 7) textQuery 중심 완화
        if (primaryQ != null) {
            Map<String, Object> relaxed = relaxToTextQueryOnly(primaryParams, primaryQ);
            addFallbackParam(out, relaxed);
        }
        if (modelFilter != null && (primaryQ == null || !primaryQ.equalsIgnoreCase(modelFilter))) {
            Map<String, Object> relaxedModel = relaxToTextQueryOnly(primaryParams, modelFilter);
            addFallbackParam(out, relaxedModel);
        }

        // 검색 텍스트 fallback(useLlm=false)에서는 q를 제거하지 않는다.
        // q 제거 fallback은 무관한 매물을 대량 반환해 조건이 사라진 것처럼 보일 수 있다.
        boolean allowDropQFallback = (request == null || !Boolean.FALSE.equals(request.getUseLlm()))
                && isBroadCategoryOnlyQuery(primaryQ);
        if (allowDropQFallback && primaryQ != null) {
            Map<String, Object> m = new LinkedHashMap<>(primaryParams);
            m.remove("q");
            addFallbackParam(out, m);
        }
        return out;
    }

    private static Map<String, Object> widenYearAndKm(Map<String, Object> source) {
        Map<String, Object> out = new LinkedHashMap<>(source);
        Integer yearMin = intOrNull(source.get("yearMin"));
        Integer yearMax = intOrNull(source.get("yearMax"));
        Integer kmMin = intOrNull(source.get("kmMin"));
        Integer kmMax = intOrNull(source.get("kmMax"));

        if (yearMin != null) out.put("yearMin", Math.max(1990, yearMin - 2));
        if (yearMax != null) out.put("yearMax", Math.min(2030, yearMax + 2));
        if (kmMin != null) out.put("kmMin", Math.max(0, kmMin - 20000));
        if (kmMax != null) out.put("kmMax", Math.min(300000, kmMax + 20000));
        return out;
    }

    private static Map<String, Object> widenPrice(Map<String, Object> source) {
        Map<String, Object> out = new LinkedHashMap<>(source);
        Integer min = intOrNull(source.get("priceMin"));
        Integer max = intOrNull(source.get("priceMax"));
        if (min != null) out.put("priceMin", Math.max(0, min - 200));
        if (max != null) out.put("priceMax", Math.min(20000, max + 200));
        return out;
    }

    private static Map<String, Object> relaxToTextQueryOnly(Map<String, Object> source, String query) {
        Map<String, Object> out = new LinkedHashMap<>(source);
        out.put("q", query);
        for (String key : RELAXABLE_FILTER_KEYS) {
            out.remove(key);
        }
        out.put("q", query);
        return out;
    }

    private static void addFallbackParam(List<Map<String, Object>> out, Map<String, Object> candidate) {
        if (out == null || candidate == null || candidate.isEmpty()) return;
        for (Map<String, Object> existing : out) {
            if (candidate.equals(existing)) return;
        }
        out.add(candidate);
    }

    private static List<CarListItemDto> extractCarItems(Object content) {
        if (!(content instanceof List<?> list)) return List.of();
        List<CarListItemDto> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof CarListItemDto dto) {
                out.add(dto);
            } else if (item instanceof Map<?, ?> m) {
                Object carIdObj = m.get("carId");
                if (!(carIdObj instanceof Number num)) continue;
                out.add(new CarListItemDto(
                        num.longValue(),
                        firstNonBlank(str(m.get("maker")), str(m.get("makerName"))),
                        firstNonBlank(str(m.get("model")), str(m.get("modelName"))),
                        firstNonBlank(str(m.get("trim")), str(m.get("trimName"))),
                        intOrNull(m.get("year")),
                        intOrNull(m.get("km")),
                        intOrNull(m.get("priceMin")),
                        intOrNull(m.get("priceMax")),
                        null,
                        firstNonBlank(str(m.get("representativeImageUrl")), str(m.get("imageUrl"))),
                        str(m.get("modelCode")),
                        str(m.get("fuel")),
                        str(m.get("region"))
                ));
            }
        }
        return out;
    }

    private static int resolveDisplayPrice(Integer minPrice, Integer maxPrice) {
        if (minPrice != null && minPrice > 0) return minPrice;
        if (maxPrice != null && maxPrice > 0) return maxPrice;
        return 0;
    }

    private static void applyIntentReRanking(
            List<RecommendationResponse.RecommendedCar> cars,
            RecommendationRequest request,
            RecommendationQueryPlan plan
    ) {
        if (cars == null || cars.isEmpty()) return;

        String intent = firstNonBlank(
                request != null ? request.getIntent() : null,
                plan != null ? plan.getIntent() : null
        );
        String normalizedIntent = intent != null ? intent.toUpperCase(Locale.ROOT) : "GENERAL";

        int minYear = Integer.MAX_VALUE;
        int maxYear = Integer.MIN_VALUE;
        int minKm = Integer.MAX_VALUE;
        int maxKm = Integer.MIN_VALUE;
        int minPrice = Integer.MAX_VALUE;
        int maxPrice = Integer.MIN_VALUE;

        for (RecommendationResponse.RecommendedCar car : cars) {
            if (car.getYear() != null) {
                minYear = Math.min(minYear, car.getYear());
                maxYear = Math.max(maxYear, car.getYear());
            }
            if (car.getMileage() != null) {
                minKm = Math.min(minKm, car.getMileage());
                maxKm = Math.max(maxKm, car.getMileage());
            }
            if (car.getPrice() != null) {
                minPrice = Math.min(minPrice, car.getPrice());
                maxPrice = Math.max(maxPrice, car.getPrice());
            }
        }

        Integer reqMaxPrice = request != null ? request.getMaxPrice() : null;
        Integer reqMinPrice = request != null ? request.getMinPrice() : null;
        int size = Math.max(1, cars.size());

        for (int i = 0; i < cars.size(); i++) {
            RecommendationResponse.RecommendedCar car = cars.get(i);
            double rank = clamp01(firstNonNull(car.getRelevanceScore(), rankScore(i, size)));
            double yearScore = scoreHigherBetter(car.getYear(), minYear, maxYear);
            double kmScore = scoreLowerBetter(car.getMileage(), minKm, maxKm);
            double priceScore = scorePrice(car.getPrice(), reqMinPrice, reqMaxPrice, minPrice, maxPrice);

            double finalScore;
            switch (normalizedIntent) {
                case "FAMILY" -> finalScore = (rank * 0.45) + (yearScore * 0.30) + (kmScore * 0.25);
                case "SAFETY" -> finalScore = (rank * 0.40) + (yearScore * 0.35) + (kmScore * 0.25);
                case "VALUE", "LOW_BUDGET" -> finalScore = (rank * 0.35) + (priceScore * 0.40) + (kmScore * 0.25);
                case "COMMUTE" -> finalScore = (rank * 0.40) + (kmScore * 0.35) + (priceScore * 0.25);
                case "DATE" -> finalScore = (rank * 0.45) + (yearScore * 0.30) + (priceScore * 0.25);
                default -> finalScore = (rank * 0.60) + (yearScore * 0.20) + (kmScore * 0.20);
            }
            finalScore += preferenceBoost(car, plan);
            car.setRelevanceScore(clamp01(finalScore));
        }

        cars.sort(Comparator.comparing(
                (RecommendationResponse.RecommendedCar c) -> firstNonNull(c.getRelevanceScore(), 0.0)
        ).reversed());
    }

    private static double preferenceBoost(
            RecommendationResponse.RecommendedCar car,
            RecommendationQueryPlan plan
    ) {
        if (car == null || plan == null || plan.getPreferences() == null || plan.getPreferences().isEmpty()) {
            return 0.0;
        }
        double boost = 0.0;
        for (RecommendationQueryPlan.PreferenceSignal pref : plan.getPreferences()) {
            if (pref == null || pref.getValues() == null || pref.getValues().isEmpty()) continue;
            String field = trimOrNull(pref.getField());
            if (field == null) continue;
            double weight = Math.max(0.0, Math.min(1.0, firstNonNull(pref.getWeight(), 0.5)));
            switch (field) {
                case "fuel" -> {
                    String fuel = trimOrNull(car.getFuel());
                    if (fuel != null && containsToken(pref.getValues(), fuel)) {
                        boost += 0.08 * weight;
                    }
                }
                case "color" -> {
                    String color = trimOrNull(car.getColor());
                    if (color != null && containsToken(pref.getValues(), color)) {
                        boost += 0.05 * weight;
                    }
                }
                case "region" -> {
                    String region = trimOrNull(car.getRegion());
                    if (region != null && containsToken(pref.getValues(), region)) {
                        boost += 0.04 * weight;
                    }
                }
                case "bodyType" -> {
                    String bodyType = trimOrNull(car.getBodyType());
                    if (bodyType != null && containsToken(pref.getValues(), bodyType)) {
                        boost += 0.06 * weight;
                    }
                }
                case "mode" -> {
                    if (containsToken(pref.getValues(), "VALUE") && car.getPrice() != null) {
                        boost += 0.03 * weight;
                    }
                    if (containsToken(pref.getValues(), "COMMUTE") && car.getMileage() != null) {
                        boost += 0.02 * weight;
                    }
                    if (containsToken(pref.getValues(), "FAMILY") && car.getYear() != null) {
                        boost += 0.02 * weight;
                    }
                }
                default -> {
                    // ignore unsupported preference field
                }
            }
        }
        return Math.min(0.15, boost);
    }

    private static boolean containsToken(List<String> values, String target) {
        if (values == null || values.isEmpty() || target == null) return false;
        String t = target.trim().toUpperCase(Locale.ROOT);
        for (String value : values) {
            String v = trimOrNull(value);
            if (v == null) continue;
            if (t.contains(v.toUpperCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static String inferDefaultSortByIntent(String intentRaw) {
        String intent = trimOrNull(intentRaw);
        if (intent == null) return null;
        String up = intent.toUpperCase(Locale.ROOT);
        return switch (up) {
            case "FAMILY", "SAFETY" -> "NEW_YEAR";
            case "VALUE", "LOW_BUDGET" -> "LOW_PRICE";
            case "COMMUTE" -> "LOW_KM";
            default -> null;
        };
    }

    private static double scoreHigherBetter(Integer value, int min, int max) {
        if (value == null) return 0.5;
        if (min == Integer.MAX_VALUE || max == Integer.MIN_VALUE || max <= min) return 0.5;
        return clamp01((value - min) / (double) (max - min));
    }

    private static double scoreLowerBetter(Integer value, int min, int max) {
        if (value == null) return 0.5;
        if (min == Integer.MAX_VALUE || max == Integer.MIN_VALUE || max <= min) return 0.5;
        return clamp01((max - value) / (double) (max - min));
    }

    private static double scorePrice(
            Integer price,
            Integer reqMinPrice,
            Integer reqMaxPrice,
            int minPrice,
            int maxPrice
    ) {
        if (price == null) return 0.5;
        if (reqMaxPrice != null && price > reqMaxPrice) return 0.1;
        if (reqMinPrice != null && price < reqMinPrice) return 0.4;
        if (reqMinPrice != null && reqMaxPrice != null && reqMaxPrice > reqMinPrice) {
            double mid = (reqMinPrice + reqMaxPrice) / 2.0;
            double halfRange = (reqMaxPrice - reqMinPrice) / 2.0;
            double dist = Math.abs(price - mid);
            return clamp01(1.0 - (dist / Math.max(1.0, halfRange)));
        }
        if (minPrice == Integer.MAX_VALUE || maxPrice == Integer.MIN_VALUE || maxPrice <= minPrice) return 0.5;
        return clamp01((maxPrice - price) / (double) (maxPrice - minPrice));
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static double rankScore(int index, int size) {
        if (size <= 1) return 1.0;
        return Math.max(0.05, 1.0 - ((double) index / (double) size));
    }

    private static String normalizeFuel(String raw) {
        String t = trimOrNull(raw);
        if (t == null) return null;
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.contains("휘발유") || lower.equals("gasoline")) return "가솔린";
        if (lower.contains("하이브리드") || lower.equals("hybrid")) return "하이브리드";
        if (lower.contains("디젤") || lower.equals("diesel")) return "디젤";
        if (lower.contains("전기") || lower.equals("electric") || lower.equals("ev")) return "전기";
        if (lower.equals("lpg") || lower.contains("엘피지")) return "LPG";
        return t;
    }

    private static String normalizeFuelCsv(String raw) {
        String value = trimOrNull(raw);
        if (value == null) return null;
        List<String> tokens = value.contains(",") ? parseCsv(value) : List.of(value);
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String token : tokens) {
            String n = normalizeFuel(token);
            if (n != null && !n.isBlank()) normalized.add(n);
        }
        if (normalized.isEmpty()) return null;
        return String.join(",", normalized);
    }

    private static List<String> parseCsv(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        String[] parts = raw.split(",");
        List<String> out = new ArrayList<>();
        for (String part : parts) {
            String token = trimOrNull(part);
            if (token != null) out.add(token);
        }
        return out;
    }

    private static String normalizeBodyType(String raw) {
        String t = trimOrNull(raw);
        if (t == null) return null;
        if ("suv".equalsIgnoreCase(t)) return "SUV";
        if ("rv".equalsIgnoreCase(t)) return "RV";
        if ("스포츠카".equalsIgnoreCase(t) || "sportscar".equalsIgnoreCase(t) || "sports car".equalsIgnoreCase(t)) {
            return "스포츠카,쿠페,컨버터블";
        }
        return t;
    }

    private static Integer sanitizeMinPrice(Integer value) {
        if (value == null) return null;
        return clamp(value, 0, 20000);
    }

    private static Integer sanitizeMaxPrice(Integer value) {
        if (value == null) return null;
        int clamped = clamp(value, 0, 20000);
        return clamped > 0 ? clamped : null;
    }

    private static void applyOriginMustFilter(
            Map<String, Object> params,
            String makerCode,
            RecommendationRequest request,
            RecommendationQueryPlan plan
    ) {
        if (params == null) return;
        if (trimOrNull(makerCode) != null) return;

        String source = firstNonBlank(
                request != null ? request.getQuery() : null,
                plan != null ? plan.getTextQuery() : null,
                request != null ? request.getSearchQuery() : null
        );
        OriginPreference origin = detectOriginPreference(source);
        if (origin == OriginPreference.IMPORT) {
            // 수입차 조건은 MUST: 국산 makerCode를 제외한다.
            params.put("excludeMakerCodes", DOMESTIC_MAKER_CODES_CSV);
        } else if (origin == OriginPreference.DOMESTIC) {
            // 국산 조건은 MUST: 국산 makerCode만 허용한다.
            params.put("makerCodes", DOMESTIC_MAKER_CODES_CSV);
        }
    }

    private static OriginPreference detectOriginPreference(String rawQuery) {
        String q = trimOrNull(rawQuery);
        if (q == null) return OriginPreference.UNKNOWN;
        String lower = q.toLowerCase(Locale.ROOT);
        boolean wantsImport = lower.contains("수입") || lower.contains("외제");
        boolean wantsDomestic = lower.contains("국산");
        if (wantsImport && !wantsDomestic) return OriginPreference.IMPORT;
        if (wantsDomestic && !wantsImport) return OriginPreference.DOMESTIC;
        return OriginPreference.UNKNOWN;
    }

    private static String normalizeSearchTextQuery(String raw) {
        String q = trimOrNull(raw);
        if (q == null) return null;
        String compact = q.replaceAll("[\\p{Punct}]+", " ").replaceAll("\\s+", " ").trim();
        compact = compact
                .replaceAll("(\\d+)\\s*천\\s*만\\s*원", "$1천만원")
                .replaceAll("(\\d+)\\s*만\\s*원", "$1만원")
                .replaceAll("(\\d+)\\s*억", "$1억");
        if (compact.isEmpty()) return null;
        String[] tokens = compact.split("\\s+");
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (String token : tokens) {
            String t = trimOrNull(token);
            if (t == null) continue;
            String lower = t.toLowerCase(Locale.ROOT);
            if (isNoiseTextToken(t, lower)) continue;
            if (selected.size() >= 8) break;
            selected.add(t);
        }
        if (selected.isEmpty()) return null;
        return String.join(" ", selected);
    }

    private static boolean isNoiseTextToken(String token, String lowerToken) {
        if (TEXT_QUERY_NOISE_TOKENS.contains(lowerToken)) return true;
        if (BROAD_CATEGORY_TOKENS.contains(lowerToken)) return true;
        if (lowerToken.matches("\\d{1,2}억대?")) return true;
        if (lowerToken.matches("\\d{1,2}천만원대?")) return true;
        if (lowerToken.matches("\\d{1,2}천만대?")) return true;
        if (lowerToken.matches("\\d{1,5}만원대?")) return true;
        if (lowerToken.matches("\\d{1,5}만대?")) return true;
        if (lowerToken.matches("\\d{1,5}만")) return true;
        if (lowerToken.matches("\\d{1,7}원")) return true;
        if (lowerToken.matches("\\d{2,4}년식?")) return true;
        if (lowerToken.matches(".*\\d.*") && (lowerToken.contains("만") || lowerToken.contains("억") || lowerToken.contains("원"))) {
            return true;
        }
        String compact = lowerToken.replaceAll("\\s+", "");
        return compact.matches("(이하|이상|미만|초과|이내)(로|는|인|이면)?");
    }

    private static boolean isBroadCategoryOnlyQuery(String query) {
        String q = trimOrNull(query);
        if (q == null) return false;
        String[] tokens = q.split("\\s+");
        boolean hasToken = false;
        for (String token : tokens) {
            String t = trimOrNull(token);
            if (t == null) continue;
            hasToken = true;
            if (!BROAD_CATEGORY_TOKENS.contains(t.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return hasToken;
    }

    private static boolean hasStructuredFilters(RecommendationQueryPlan plan, RecommendationRequest request) {
        return trimOrNull(plan != null ? plan.getMakerCode() : null) != null
                || trimOrNull(plan != null ? plan.getModelCode() : null) != null
                || trimOrNull(request != null ? request.getBodyTypeFilter() : null) != null
                || trimOrNull(planBodyTypesCsv(plan)) != null
                || trimOrNull(request != null ? request.getExcludeBodyTypeFilter() : null) != null
                || trimOrNull(planExcludeBodyTypesCsv(plan)) != null
                || trimOrNull(request != null ? request.getFuel() : null) != null
                || trimOrNull(plan != null ? plan.getFuel() : null) != null
                || trimOrNull(request != null ? request.getExcludeFuel() : null) != null
                || trimOrNull(plan != null ? plan.getExcludeFuel() : null) != null
                || trimOrNull(request != null ? request.getColorFilter() : null) != null
                || trimOrNull(plan != null ? plan.getColor() : null) != null
                || trimOrNull(request != null ? request.getExcludeColorFilter() : null) != null
                || trimOrNull(plan != null ? plan.getExcludeColor() : null) != null
                || trimOrNull(request != null ? request.getRegionFilter() : null) != null
                || trimOrNull(plan != null ? plan.getRegion() : null) != null
                || trimOrNull(request != null ? request.getExcludeRegionFilter() : null) != null
                || trimOrNull(plan != null ? plan.getExcludeRegion() : null) != null
                || (request != null && request.getMinPrice() != null)
                || (request != null && request.getMaxPrice() != null)
                || (plan != null && plan.getMinPrice() != null)
                || (plan != null && plan.getMaxPrice() != null)
                || (request != null && request.getMinYear() != null)
                || (request != null && request.getMaxYear() != null)
                || (plan != null && plan.getMinYear() != null)
                || (plan != null && plan.getMaxYear() != null)
                || (request != null && request.getMinKm() != null)
                || (request != null && request.getMaxKm() != null)
                || (plan != null && plan.getMinKm() != null)
                || (plan != null && plan.getMaxKm() != null);
    }

    private static boolean isModelExplicitlyRequested(RecommendationRequest request, RecommendationQueryPlan plan) {
        if (plan != null) {
            if (trimOrNull(plan.getModelCode()) != null) return true;
            if (trimOrNull(plan.getModel()) != null) return true;
        }
        String modelFilter = trimOrNull(request != null ? request.getModelFilter() : null);
        if (isLikelyModelToken(modelFilter)) return true;
        String query = trimOrNull(request != null ? request.getQuery() : null);
        return isLikelyModelToken(query);
    }

    private static boolean isLikelyModelToken(String raw) {
        String token = trimOrNull(raw);
        if (token == null) return false;
        String upper = token.toUpperCase(Locale.ROOT);
        for (String known : KNOWN_MODEL_TOKENS) {
            if (upper.contains(known)) return true;
        }
        return ALPHA_NUMERIC_MODEL_PATTERN.matcher(token).matches();
    }

    private static String planBodyTypesCsv(RecommendationQueryPlan plan) {
        if (plan == null || plan.getBodyTypes() == null || plan.getBodyTypes().isEmpty()) return null;
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (String bodyType : plan.getBodyTypes()) {
            String normalized = normalizeBodyType(bodyType);
            String token = trimOrNull(normalized);
            if (token != null) values.put(token.toUpperCase(Locale.ROOT), token);
        }
        if (values.isEmpty()) return null;
        return String.join(",", values.values());
    }

    private static String planExcludeBodyTypesCsv(RecommendationQueryPlan plan) {
        if (plan == null || plan.getExcludeBodyTypes() == null || plan.getExcludeBodyTypes().isEmpty()) return null;
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (String bodyType : plan.getExcludeBodyTypes()) {
            String normalized = normalizeBodyType(bodyType);
            String token = trimOrNull(normalized);
            if (token != null) values.put(token.toUpperCase(Locale.ROOT), token);
        }
        if (values.isEmpty()) return null;
        return String.join(",", values.values());
    }

    private static int clamp(int n, int min, int max) {
        return Math.max(min, Math.min(max, n));
    }

    private static Integer firstNonNull(Integer a, Integer b) {
        return a != null ? a : b;
    }

    private static Double firstNonNull(Double a, Double b) {
        return a != null ? a : b;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            String t = trimOrNull(v);
            if (t != null) return t;
        }
        return null;
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String str(Object o) {
        return o != null ? String.valueOf(o) : null;
    }

    private static Integer intOrNull(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(Objects.toString(o));
        } catch (Exception ignored) {
            return null;
        }
    }

    private enum OriginPreference {
        IMPORT,
        DOMESTIC,
        UNKNOWN
    }
}

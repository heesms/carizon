package com.carizon.rag.service;

import com.carizon.rag.dto.RecommendationQueryPlan;
import com.carizon.rag.dto.RecommendationRequest;
import com.carizon.rag.dto.RecommendationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 검색 페이지 전용 텍스트 fallback 추천 서비스.
 * - AI 추천 API와 분리된 전용 엔드포인트에서만 사용
 * - LLM/생성 응답을 사용하지 않고 검색 리스트용 매물만 반환
 * - 오타/표기 변형 보정 + 조건 추출로 재검색
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchFallbackRecommendationService {
    private static final long MODEL_METADATA_CACHE_TTL_MS = 10 * 60 * 1000L;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[가-힣A-Za-z0-9]+");
    private static final Set<String> MODEL_CORRECTION_STOPWORDS = Set.of(
            "추천", "매물", "차량", "자동차",
            "수입차", "외제차", "국산차",
            "저주행", "주행거리", "짧은", "위주",
            "패밀리카", "가족차", "출퇴근", "가성비",
            "세단", "suv", "rv", "하이브리드", "가솔린", "디젤", "전기", "lpg"
    );

    private static final Map<String, String> QUERY_NORMALIZATION = new LinkedHashMap<>();

    static {
        QUERY_NORMALIZATION.put("그랜져", "그랜저");
        QUERY_NORMALIZATION.put("아반테", "아반떼");
        QUERY_NORMALIZATION.put("소렌토", "쏘렌토");
        QUERY_NORMALIZATION.put("스토닉스", "스토닉");
        QUERY_NORMALIZATION.put("투산", "투싼");
    }

    private final JdbcTemplate jdbcTemplate;
    private final RecommendationQueryPlannerService queryPlannerService;
    private final RecommendationElasticsearchService recommendationElasticsearchService;
    private volatile List<ModelNameEntry> cachedModelNames = List.of();
    private volatile long cachedModelLoadedAt = 0L;

    public RecommendationResponse recommendFromText(RecommendationRequest request) {
        RecommendationRequest baseRequest = normalizeIncomingRequest(request);
        String originalQuery = trimOrNull(baseRequest.getQuery());
        if (originalQuery == null) {
            return RecommendationResponse.builder()
                    .recommendation("텍스트 검색어가 비어 있어 추천 매물을 찾을 수 없습니다.")
                    .cars(List.of())
                    .build();
        }

        int maxResults = baseRequest.getMaxResults() != null ? baseRequest.getMaxResults() : 12;
        int candidateSize = Math.max(40, maxResults * 12);
        LinkedHashMap<Long, RecommendationResponse.RecommendedCar> merged = new LinkedHashMap<>();
        String usedVariant = originalQuery;
        boolean usedCorrectedVariant = false;
        log.info("[search-fallback] start: query='{}', maxResults={}", originalQuery, maxResults);

        for (String queryVariant : buildQueryVariants(originalQuery)) {
            RecommendationRequest runRequest = copyRequest(baseRequest);
            runRequest.setQuery(queryVariant);
            runRequest.setSearchQuery(null);
            runRequest.setUseLlm(false);

            RecommendationQueryPlan plan = queryPlannerService.plan(queryVariant, false);
            List<RecommendationResponse.RecommendedCar> hits =
                    recommendationElasticsearchService.searchCandidates(runRequest, plan, candidateSize);
            log.info("[search-fallback] variant='{}', hits={}", queryVariant, hits.size());
            if (!hits.isEmpty() && !queryVariant.equals(originalQuery) && !usedCorrectedVariant) {
                usedVariant = queryVariant;
                usedCorrectedVariant = true;
            }
            for (RecommendationResponse.RecommendedCar car : hits) {
                if (car == null || car.getCarId() == null) continue;
                car.setRelevanceScore(null);
                car.setReason(null);
                merged.putIfAbsent(car.getCarId(), car);
                if (merged.size() >= maxResults) break;
            }
            if (merged.size() >= maxResults) break;
        }

        List<RecommendationResponse.RecommendedCar> cars = new ArrayList<>(merged.values());
        cars = filterPositivePrice(cars);
        if (cars.size() > maxResults) {
            cars = new ArrayList<>(cars.subList(0, maxResults));
        }
        if (cars.isEmpty()) {
            return RecommendationResponse.builder()
                    .recommendation("검색결과가 없어 오타/유사어 보정까지 재검색했지만 매물을 찾지 못했습니다.")
                    .cars(List.of())
                    .build();
        }
        String message = usedCorrectedVariant
                ? "검색결과가 없어 '" + originalQuery + "' 관련 보정어('" + usedVariant + "')로 재검색한 결과입니다."
                : "검색결과가 없어 텍스트 조건으로 재검색한 결과입니다.";
        return RecommendationResponse.builder()
                .recommendation(message)
                .cars(cars)
                .build();
    }

    private static RecommendationRequest normalizeIncomingRequest(RecommendationRequest in) {
        RecommendationRequest req = new RecommendationRequest();
        if (in != null) {
            req.setQuery(in.getQuery());
            req.setSearchQuery(in.getSearchQuery());
            req.setMaxResults(in.getMaxResults());
            req.setMinPrice(in.getMinPrice());
            req.setMaxPrice(in.getMaxPrice());
            req.setMaker(in.getMaker());
            req.setFuel(in.getFuel());
            req.setExcludeFuel(in.getExcludeFuel());
            req.setColorFilter(in.getColorFilter());
            req.setExcludeColorFilter(in.getExcludeColorFilter());
            req.setRegionFilter(in.getRegionFilter());
            req.setExcludeRegionFilter(in.getExcludeRegionFilter());
            req.setModelFilter(in.getModelFilter());
            req.setBodyTypeFilter(in.getBodyTypeFilter());
            req.setExcludeBodyTypeFilter(in.getExcludeBodyTypeFilter());
            req.setMaxYear(in.getMaxYear());
            req.setMinYear(in.getMinYear());
            req.setMaxKm(in.getMaxKm());
            req.setMinKm(in.getMinKm());
            req.setPreferredYear(in.getPreferredYear());
            req.setIntent(in.getIntent());
        }
        if (req.getMaxResults() == null || req.getMaxResults() <= 0) {
            req.setMaxResults(12);
        }
        req.setUseLlm(false);
        if (req.getQuery() != null) req.setQuery(req.getQuery().trim());
        return req;
    }

    private static RecommendationRequest copyRequest(RecommendationRequest source) {
        RecommendationRequest req = new RecommendationRequest();
        req.setQuery(source.getQuery());
        req.setSearchQuery(source.getSearchQuery());
        req.setMaxResults(source.getMaxResults());
        req.setMinPrice(source.getMinPrice());
        req.setMaxPrice(source.getMaxPrice());
        req.setMaker(source.getMaker());
        req.setFuel(source.getFuel());
        req.setExcludeFuel(source.getExcludeFuel());
        req.setColorFilter(source.getColorFilter());
        req.setExcludeColorFilter(source.getExcludeColorFilter());
        req.setRegionFilter(source.getRegionFilter());
        req.setExcludeRegionFilter(source.getExcludeRegionFilter());
        req.setModelFilter(source.getModelFilter());
        req.setBodyTypeFilter(source.getBodyTypeFilter());
        req.setExcludeBodyTypeFilter(source.getExcludeBodyTypeFilter());
        req.setMaxYear(source.getMaxYear());
        req.setMinYear(source.getMinYear());
        req.setMaxKm(source.getMaxKm());
        req.setMinKm(source.getMinKm());
        req.setPreferredYear(source.getPreferredYear());
        req.setIntent(source.getIntent());
        req.setUseLlm(Boolean.FALSE);
        return req;
    }

    private static String normalizeQuery(String query) {
        if (query == null) return null;
        String out = query;
        for (Map.Entry<String, String> entry : QUERY_NORMALIZATION.entrySet()) {
            out = out.replace(entry.getKey(), entry.getValue());
        }
        // 자주 나오는 오타 보정 (예: 그랜져 -> 그랜저)
        out = out.replace("져", "저");
        return out.replaceAll("\\s+", " ").trim();
    }

    private List<String> buildQueryVariants(String originalQuery) {
        LinkedHashSet<String> variants = new LinkedHashSet<>();
        String original = trimOrNull(originalQuery);
        if (original == null) return List.of();
        variants.add(original);

        String normalized = normalizeQuery(original);
        if (normalized != null && !normalized.isBlank()) variants.add(normalized);

        String metadataCorrected = applyMetadataModelCorrection(original);
        if (metadataCorrected != null && !metadataCorrected.isBlank()) variants.add(metadataCorrected);

        String metadataCorrectedNormalized = metadataCorrected != null ? normalizeQuery(metadataCorrected) : null;
        if (metadataCorrectedNormalized != null && !metadataCorrectedNormalized.isBlank()) {
            variants.add(metadataCorrectedNormalized);
        }

        String noSpace = normalized != null ? normalized.replace(" ", "") : null;
        if (noSpace != null && noSpace.length() >= 2) variants.add(noSpace);
        return new ArrayList<>(variants);
    }

    private String applyMetadataModelCorrection(String query) {
        String source = trimOrNull(query);
        if (source == null) return null;
        List<ModelNameEntry> modelNames = loadModelMetadata();
        if (modelNames.isEmpty()) return source;

        Matcher matcher = TOKEN_PATTERN.matcher(source);
        StringBuffer sb = new StringBuffer();
        boolean changed = false;
        while (matcher.find()) {
            String token = matcher.group();
            String replacement = findClosestModelName(token, modelNames);
            if (replacement != null && !replacement.equals(token)) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                changed = true;
                continue;
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(token));
        }
        matcher.appendTail(sb);
        return changed ? sb.toString().replaceAll("\\s+", " ").trim() : source;
    }

    private String findClosestModelName(String token, List<ModelNameEntry> modelNames) {
        String raw = trimOrNull(token);
        if (raw == null) return null;
        String lower = raw.toLowerCase();
        if (MODEL_CORRECTION_STOPWORDS.contains(lower)) return null;

        String normToken = normalizeToken(raw);
        if (normToken.length() < 2 || normToken.length() > 12) return null;

        int maxDistance = normToken.length() <= 4 ? 1 : 2;
        ModelNameEntry best = null;
        int bestDistance = Integer.MAX_VALUE;
        int bestPrefix = -1;

        for (ModelNameEntry model : modelNames) {
            if (Math.abs(model.normalized.length() - normToken.length()) > maxDistance) continue;
            if (!sameInitial(normToken, model.normalized)) continue;

            int distance = levenshteinDistanceWithin(normToken, model.normalized, maxDistance);
            if (distance < 0) continue;
            int prefix = commonPrefix(normToken, model.normalized);
            if (best == null || distance < bestDistance || (distance == bestDistance && prefix > bestPrefix)) {
                best = model;
                bestDistance = distance;
                bestPrefix = prefix;
            }
        }
        return best != null ? best.displayName : null;
    }

    private List<ModelNameEntry> loadModelMetadata() {
        long now = System.currentTimeMillis();
        List<ModelNameEntry> local = cachedModelNames;
        if (!local.isEmpty() && (now - cachedModelLoadedAt) < MODEL_METADATA_CACHE_TTL_MS) {
            return local;
        }
        synchronized (this) {
            long ts = System.currentTimeMillis();
            if (!cachedModelNames.isEmpty() && (ts - cachedModelLoadedAt) < MODEL_METADATA_CACHE_TTL_MS) {
                return cachedModelNames;
            }
            try {
                List<ModelNameEntry> loaded = jdbcTemplate.query(
                        "SELECT DISTINCT TRIM(model_name) AS modelName " +
                                "FROM cz_model " +
                                "WHERE model_name IS NOT NULL AND TRIM(model_name) <> ''",
                        (rs, rowNum) -> {
                            String modelName = rs.getString("modelName");
                            String normalized = normalizeToken(modelName);
                            if (normalized.isEmpty()) return null;
                            return new ModelNameEntry(modelName, normalized);
                        }
                );
                LinkedHashMap<String, ModelNameEntry> dedup = new LinkedHashMap<>();
                for (ModelNameEntry e : loaded) {
                    if (e == null) continue;
                    dedup.putIfAbsent(e.normalized, e);
                }
                cachedModelNames = new ArrayList<>(dedup.values());
                cachedModelLoadedAt = ts;
                log.info("[search-fallback] model metadata cache refreshed: {} models", cachedModelNames.size());
            } catch (Exception e) {
                log.warn("[search-fallback] model metadata load failed: {}", e.getMessage());
            }
            return cachedModelNames;
        }
    }

    private static String normalizeToken(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[^가-힣a-z0-9]", "");
    }

    private static boolean sameInitial(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        return a.charAt(0) == b.charAt(0);
    }

    private static List<RecommendationResponse.RecommendedCar> filterPositivePrice(List<RecommendationResponse.RecommendedCar> cars) {
        if (cars == null || cars.isEmpty()) return List.of();
        List<RecommendationResponse.RecommendedCar> filtered = new ArrayList<>(cars.size());
        for (RecommendationResponse.RecommendedCar car : cars) {
            if (car == null) continue;
            Integer price = car.getPrice();
            if (price == null || price <= 0) continue;
            filtered.add(car);
        }
        return filtered;
    }

    private static int commonPrefix(String a, String b) {
        int n = Math.min(a.length(), b.length());
        int i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) i++;
        return i;
    }

    private static int levenshteinDistanceWithin(String s1, String s2, int maxDistance) {
        int len1 = s1.length();
        int len2 = s2.length();
        if (Math.abs(len1 - len2) > maxDistance) return -1;
        int[] prev = new int[len2 + 1];
        int[] curr = new int[len2 + 1];
        for (int j = 0; j <= len2; j++) prev[j] = j;

        for (int i = 1; i <= len1; i++) {
            curr[0] = i;
            int rowMin = curr[0];
            char c1 = s1.charAt(i - 1);
            for (int j = 1; j <= len2; j++) {
                int cost = (c1 == s2.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(
                        prev[j] + 1,
                        curr[j - 1] + 1
                ), prev[j - 1] + cost);
                rowMin = Math.min(rowMin, curr[j]);
            }
            if (rowMin > maxDistance) return -1;
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[len2] <= maxDistance ? prev[len2] : -1;
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private record ModelNameEntry(String displayName, String normalized) {}
}

package com.carizon.rag.service;

import com.carizon.rag.config.RagProperties;
import com.carizon.rag.dto.RecommendationQueryPlan;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 자연어 질의를 구조화된 검색 플랜으로 변환.
 * LLM은 "해석"(슬롯/의도/선호)만 담당하고,
 * 실제 검색 조건 결정/완화는 서버 규칙이 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationQueryPlannerService {

    private static final Set<String> ALLOWED_SORTS = Set.of("RECENT", "LOW_PRICE", "LOW_KM", "NEW_YEAR");
    private static final Set<String> ALLOWED_INTENTS = Set.of("VALUE", "SAFETY", "DATE", "FAMILY", "COMMUTE", "LOW_BUDGET", "GENERAL");

    private static final Set<String> ALLOWED_FUELS = Set.of(
            "가솔린", "디젤", "전기", "수소", "LPG", "LPG(일반인)",
            "하이브리드", "하이브리드(가솔린)", "하이브리드(디젤)", "하이브리드(LPG)", "기타"
    );

    private static final Set<String> ALLOWED_BODY_TYPES = Set.of(
            "중형", "소형", "스포츠카", "SUV", "경차", "준중형", "RV", "트럭",
            "대형", "승합", "버스", "화물", "상용", "기타"
    );

    private static final Set<String> ALLOWED_COLORS = Set.of(
            "흰색", "흰색투톤", "회색", "하늘색", "파랑색", "초록색", "진주색", "진주색투톤",
            "주황색", "보라색", "은색", "은색투톤", "금색", "금색투톤", "빨강색", "분홍색",
            "노랑색", "검정색", "검정투톤", "갈색", "갈색투톤", "미색", "기타"
    );

    private static final Set<String> ALLOWED_ASKED_FIELDS = Set.of(
            "price", "year", "mileage", "fuel", "color", "bodyType", "region", "maker", "model", "sort"
    );

    private static final Set<String> ROOT_ALLOWED_KEYS = Set.of(
            "text_query", "textQuery", "intent", "confidence", "asked_fields", "askedFields",
            "hard_filters", "preferences", "evidence_spans", "evidenceSpans"
    );

    private static final Set<String> HARD_FILTER_ALLOWED_KEYS = Set.of(
            "makerCode", "maker", "modelCode", "model",
            "fuel", "bodyType", "color", "region",
            "price", "year", "mileage", "sort"
    );

    private static final Set<String> FILTER_OBJECT_ALLOWED_KEYS = Set.of("include", "exclude", "preference", "evidence", "value");

    private static final Set<String> RANGE_ALLOWED_KEYS = Set.of("min", "max", "evidence");

    private static final Set<String> PREFERENCE_ALLOWED_KEYS = Set.of("field", "values", "weight", "evidence");

    private static final Set<String> EVIDENCE_ALLOWED_KEYS = Set.of("field", "value", "text", "start", "end", "mode");

    private static final int YEAR_MIN = 1990;
    private static final int YEAR_MAX = 2035;
    private static final int PRICE_MAX = 20000;
    private static final int KM_MAX = 500000;

    private static final int MODE_WINDOW_SIZE = 14;
    private static final List<String> EXCLUDE_KEYWORDS = List.of("빼고", "제외", "말고", "싫어", "안돼", "제외해", "제외하고");
    private static final List<String> INCLUDE_KEYWORDS = List.of("만", "로만", "만으로", "만으로만", "만 찾아", "만 골라");
    private static final List<String> PREFERENCE_KEYWORDS = List.of("선호", "좋아", "우선", "위주", "원해", "좋고");

    private static final Pattern PRICE_RANGE_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(억|천|만|만원|원)?\\s*[~\\-]\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(억|천|만|만원|원)?");
    private static final Pattern PRICE_BOUND_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(억|천|만|만원|원)");
    private static final Pattern YEAR_PATTERN = Pattern.compile("(19\\d{2}|20\\d{2}|\\d{2})\\s*년(?:식)?");
    private static final Pattern KM_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(만)?\\s*(km|KM|키로|킬로)");

    private static final List<EnumPattern> FUEL_PATTERNS = List.of(
            enumPattern("(전기차|\\bev\\b|전비|충전)", "전기"),
            enumPattern("(수소|fcev)", "수소"),
            enumPattern("(가솔린|휘발유|gasoline|petrol)", "가솔린"),
            enumPattern("(디젤|경유|diesel)", "디젤"),
            enumPattern("(lpg\\s*\\(일반인\\)|일반인\\s*lpg|lpg\\s*일반인)", "LPG(일반인)"),
            enumPattern("(lpg|엘피지|가스차|가스)", "LPG"),
            enumPattern("(하이브리드\\s*\\(가솔린\\)|가솔린\\s*하이브리드)", "하이브리드(가솔린)"),
            enumPattern("(하이브리드\\s*\\(디젤\\)|디젤\\s*하이브리드)", "하이브리드(디젤)"),
            enumPattern("(하이브리드\\s*\\(lpg\\)|lpg\\s*하이브리드)", "하이브리드(LPG)"),
            enumPattern("(하이브리드|\\bhev\\b|하브)", "하이브리드")
    );

    private static final List<EnumPattern> COLOR_PATTERNS = List.of(
            enumPattern("(흰색\\s*투톤|투톤\\s*흰|white\\s*two[- ]?tone)", "흰색투톤"),
            enumPattern("(진주\\s*투톤|투톤\\s*진주)", "진주색투톤"),
            enumPattern("(검정\\s*투톤|투톤\\s*검정)", "검정투톤"),
            enumPattern("(은색\\s*투톤|투톤\\s*은색)", "은색투톤"),
            enumPattern("(갈색\\s*투톤|투톤\\s*갈색)", "갈색투톤"),
            enumPattern("(금색\\s*투톤|투톤\\s*금색)", "금색투톤"),
            enumPattern("(진주|펄|pearl)", "진주색"),
            enumPattern("(검정|블랙|black)", "검정색"),
            enumPattern("(흰색|화이트|white)", "흰색"),
            enumPattern("(회색|그레이|gray|쥐색)", "회색"),
            enumPattern("(은색|실버|silver)", "은색"),
            enumPattern("(금색|골드|gold)", "금색"),
            enumPattern("(빨강|빨간색|레드|red|버건디|와인)", "빨강색"),
            enumPattern("(파랑|파란색|블루|blue|청색|남색)", "파랑색"),
            enumPattern("(하늘색|스카이블루)", "하늘색"),
            enumPattern("(초록|녹색|그린|green)", "초록색"),
            enumPattern("(주황|오렌지|orange)", "주황색"),
            enumPattern("(노랑|노란색|옐로|yellow)", "노랑색"),
            enumPattern("(분홍|핑크|pink)", "분홍색"),
            enumPattern("(보라|퍼플|purple|자주)", "보라색"),
            enumPattern("(갈색|브라운|brown)", "갈색"),
            enumPattern("(미색|베이지|아이보리)", "미색")
    );

    private static final List<EnumPattern> BODYTYPE_PATTERNS = List.of(
            enumPattern("(suv|지프|스포츠유틸리티)", "SUV"),
            enumPattern("(rv|미니밴|mpv|카니발|스타리아)", "RV"),
            enumPattern("(경차|모닝|레이|스파크)", "경차"),
            enumPattern("(준중형)", "준중형"),
            enumPattern("(중형|중대형)", "중형"),
            enumPattern("(대형|준대형)", "대형"),
            enumPattern("(소형|소형차)", "소형"),
            enumPattern("(스포츠카|쿠페|오픈카|컨버터블)", "스포츠카"),
            enumPattern("(트럭|픽업)", "트럭"),
            enumPattern("(버스)", "버스"),
            enumPattern("(화물|짐차)", "화물"),
            enumPattern("(상용|영업용|업무용)", "상용"),
            enumPattern("(승합|승합차)", "승합")
    );

    private static final List<EnumPattern> REGION_PATTERNS = List.of(
            enumPattern("(수도권)", "서울,경기,인천"),
            enumPattern("(서울)", "서울"),
            enumPattern("(경기)", "경기"),
            enumPattern("(인천)", "인천"),
            enumPattern("(부산)", "부산"),
            enumPattern("(대구)", "대구"),
            enumPattern("(대전)", "대전"),
            enumPattern("(광주)", "광주"),
            enumPattern("(울산)", "울산"),
            enumPattern("(세종)", "세종"),
            enumPattern("(강원)", "강원"),
            enumPattern("(충북)", "충북"),
            enumPattern("(충남)", "충남"),
            enumPattern("(전북)", "전북"),
            enumPattern("(전남)", "전남"),
            enumPattern("(경북)", "경북"),
            enumPattern("(경남)", "경남"),
            enumPattern("(제주)", "제주")
    );

    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final RagProperties ragProperties;

    private final AtomicInteger parserFailureStreak = new AtomicInteger(0);
    private final AtomicLong parserDisabledUntilMs = new AtomicLong(0L);

    public RecommendationQueryPlan plan(String userQuery) {
        return plan(userQuery, true);
    }

    public RecommendationQueryPlan plan(String userQuery, boolean useLlm) {
        if (userQuery == null || userQuery.isBlank()) {
            return RecommendationQueryPlan.builder().parserSource("EMPTY").build();
        }

        RecommendationQueryPlan heuristic = sanitize(heuristicPlan(userQuery), userQuery, "HEURISTIC");

        if (!useLlm) {
            log.info("[QueryPlanner] heuristic plan (LLM disabled): {}", heuristic);
            return heuristic;
        }

        if (isParserCircuitOpen()) {
            RecommendationQueryPlan skipped = cloneWithParserSource(heuristic, "SKIP_CIRCUIT");
            log.warn("[QueryPlanner] parser circuit open, skip LLM parser");
            return skipped;
        }

        try {
            String prompt = buildParserPrompt(userQuery);
            int timeoutMs = Math.max(1500, ragProperties.getRecommendation().getParser().getTimeoutMs());
            String raw = llmService.generateResponse(
                    prompt,
                    new LlmService.GenerationOptions(520, 0.0, timeoutMs, parserSystemPrompt())
            );
            RecommendationQueryPlan llmPlan = sanitize(parsePlanJson(raw, userQuery), userQuery, "LLM");
            boolean lowConfidence = isLowConfidence(llmPlan);
            RecommendationQueryPlan merged = sanitize(
                    mergePreferLlm(llmPlan, heuristic, lowConfidence),
                    userQuery,
                    lowConfidence ? "LLM_LOW_CONFIDENCE" : "LLM"
            );
            resetParserCircuit();
            log.info("[QueryPlanner] plan from LLM parser: source={}, confidence={}, askedFields={}, plan={}",
                    merged.getParserSource(), merged.getConfidence(), merged.getAskedFields(), merged);
            return merged;
        } catch (Exception e) {
            recordParserFailure(e);
            RecommendationQueryPlan fallback = cloneWithParserSource(heuristic, "FALLBACK_HEURISTIC");
            log.warn("[QueryPlanner] LLM parser unavailable/invalid, fallback heuristic: {}", e.getMessage());
            return fallback;
        }
    }

    private static RecommendationQueryPlan cloneWithParserSource(RecommendationQueryPlan base, String source) {
        if (base == null) return RecommendationQueryPlan.builder().parserSource(source).build();
        base.setParserSource(source);
        return base;
    }

    private boolean isParserCircuitOpen() {
        long now = System.currentTimeMillis();
        return now < parserDisabledUntilMs.get();
    }

    private void resetParserCircuit() {
        parserFailureStreak.set(0);
        parserDisabledUntilMs.set(0L);
    }

    private void recordParserFailure(Exception e) {
        int threshold = Math.max(1, ragProperties.getRecommendation().getParser().getCircuitFailThreshold());
        int openMs = Math.max(1000, ragProperties.getRecommendation().getParser().getCircuitOpenMs());
        int fail = parserFailureStreak.incrementAndGet();
        if (fail >= threshold) {
            parserDisabledUntilMs.set(System.currentTimeMillis() + openMs);
            parserFailureStreak.set(0);
            log.warn("[QueryPlanner] parser circuit opened for {}ms after failures: {}", openMs, e.getMessage());
        }
    }

    private boolean isLowConfidence(RecommendationQueryPlan plan) {
        if (plan == null || plan.getConfidence() == null) return false;
        double threshold = ragProperties.getRecommendation().getParser().getLowConfidenceThreshold();
        return plan.getConfidence() < threshold;
    }

    private String parserSystemPrompt() {
        return "You are a deterministic query parser. Return strict JSON only. "
                + "Do not add prose, markdown, comments, or extra keys.";
    }

    private String buildParserPrompt(String userQuery) {
        return """
            사용자 자연어를 검색용 구조화 JSON으로만 변환하세요.
            당신의 역할은 해석(parser)입니다. 추천 결정/정렬 결정은 서버가 합니다.

            반드시 JSON 객체 하나만 출력하세요. 코드블록/설명 금지.
            스키마 외 키는 절대 금지합니다.

            JSON 스키마:
            {
              "text_query": "string",
              "intent": "VALUE|SAFETY|DATE|FAMILY|COMMUTE|LOW_BUDGET|GENERAL",
              "confidence": 0.0,
              "asked_fields": ["price|year|mileage|fuel|color|bodyType|region|maker|model|sort"],
              "hard_filters": {
                "makerCode": "string",
                "maker": "string",
                "modelCode": "string",
                "model": "string",
                "fuel": {"include": ["가솔린","디젤","전기","수소","LPG","LPG(일반인)","하이브리드","하이브리드(가솔린)","하이브리드(디젤)","하이브리드(LPG)","기타"], "exclude": [], "preference": [], "evidence": "원문 구절"},
                "color": {"include": ["흰색","흰색투톤","회색","하늘색","파랑색","초록색","진주색","진주색투톤","주황색","보라색","은색","은색투톤","금색","금색투톤","빨강색","분홍색","노랑색","검정색","검정투톤","갈색","갈색투톤","미색","기타"], "exclude": [], "preference": [], "evidence": "원문 구절"},
                "bodyType": {"include": ["중형","소형","스포츠카","SUV","경차","준중형","RV","트럭","대형","승합","버스","화물","상용","기타"], "exclude": [], "preference": [], "evidence": "원문 구절"},
                "region": {"include": ["서울","경기","인천","부산","대구","대전","광주","울산","세종","강원","충북","충남","전북","전남","경북","경남","제주"], "exclude": [], "preference": [], "evidence": "원문 구절"},
                "price": {"min": 0, "max": 0, "evidence": "원문 구절"},
                "year": {"min": 0, "max": 0, "evidence": "원문 구절"},
                "mileage": {"min": 0, "max": 0, "evidence": "원문 구절"},
                "sort": "RECENT|LOW_PRICE|LOW_KM|NEW_YEAR"
              },
              "preferences": [
                {"field": "fuel|color|bodyType|region|mode", "values": ["string"], "weight": 0.0, "evidence": "원문 구절"}
              ],
              "evidence_spans": [
                {"field": "string", "value": "string", "text": "원문 조각", "start": 0, "end": 3, "mode": "include|exclude|preference|unknown"}
              ]
            }

            규칙:
            - 결정(필수/정렬 확정)을 하지 말고 해석만 하세요.
            - "~빼고/제외"는 exclude만 사용.
            - "~만/로만"은 include만 사용.
            - "선호/위주/우선"은 preferences 또는 hard_filters.*.preference로만 표기하고 하드필터로 강제하지 마세요.
            - 확실하지 않으면 해당 필드는 생략하세요.
            - 수치는 정수, 가격은 만원 단위로 반환하세요.

            사용자 질의:
            """ + userQuery.trim();
    }

    private RecommendationQueryPlan parsePlanJson(String raw, String userQuery) throws IOException {
        String json = extractFirstJsonObject(raw);
        JsonNode root = objectMapper.readTree(json);
        if (root == null || !root.isObject()) {
            throw new IOException("LLM parser output is not JSON object");
        }

        assertAllowedKeys(root, ROOT_ALLOWED_KEYS, "root");

        JsonNode hardFilters = firstNode(root, "hard_filters", "hardFilters");
        if (hardFilters != null && !hardFilters.isNull()) {
            if (!hardFilters.isObject()) throw new IOException("hard_filters must be object");
            assertAllowedKeys(hardFilters, HARD_FILTER_ALLOWED_KEYS, "hard_filters");
        }

        RecommendationQueryPlan.RecommendationQueryPlanBuilder b = RecommendationQueryPlan.builder();
        b.textQuery(firstNonBlank(readText(root, "text_query"), readText(root, "textQuery"), userQuery));
        b.intent(normalizeIntent(readText(root, "intent")));
        b.confidence(clampDouble(readDouble(root, "confidence"), 0.0, 1.0));
        b.askedFields(normalizeAskedFields(readStringList(firstNode(root, "asked_fields", "askedFields"))));
        b.preferences(parsePreferences(root.get("preferences")));
        b.evidenceSpans(parseEvidenceSpans(firstNode(root, "evidence_spans", "evidenceSpans")));

        if (hardFilters != null && hardFilters.isObject()) {
            b.makerCode(trimOrNull(readText(hardFilters, "makerCode")));
            b.maker(trimOrNull(readText(hardFilters, "maker")));
            b.modelCode(trimOrNull(readText(hardFilters, "modelCode")));
            b.model(trimOrNull(readText(hardFilters, "model")));

            ParsedListFilter fuel = parseListFilter(hardFilters.get("fuel"), "fuel", ALLOWED_FUELS);
            b.fuel(joinCsv(fuel.include));
            b.excludeFuel(joinCsv(fuel.exclude));

            ParsedListFilter color = parseListFilter(hardFilters.get("color"), "color", ALLOWED_COLORS);
            b.color(joinCsv(color.include));
            b.excludeColor(joinCsv(color.exclude));

            ParsedListFilter bodyType = parseListFilter(hardFilters.get("bodyType"), "bodyType", ALLOWED_BODY_TYPES);
            b.bodyTypes(new ArrayList<>(bodyType.include));
            b.excludeBodyTypes(new ArrayList<>(bodyType.exclude));

            ParsedListFilter region = parseListFilter(hardFilters.get("region"), "region", null);
            b.region(joinCsv(region.include));
            b.excludeRegion(joinCsv(region.exclude));

            ParsedRangeFilter price = parseRangeFilter(hardFilters.get("price"), "price");
            b.minPrice(price.min);
            b.maxPrice(price.max);

            ParsedRangeFilter year = parseRangeFilter(hardFilters.get("year"), "year");
            b.minYear(year.min);
            b.maxYear(year.max);

            ParsedRangeFilter mileage = parseRangeFilter(hardFilters.get("mileage"), "mileage");
            b.minKm(mileage.min);
            b.maxKm(mileage.max);

            b.sort(normalizeSort(readText(hardFilters, "sort")));

            List<RecommendationQueryPlan.PreferenceSignal> fromFilterPreference = new ArrayList<>();
            if (!fuel.preference.isEmpty()) {
                fromFilterPreference.add(preferenceSignal("fuel", fuel.preference, 0.65, fuel.evidence));
            }
            if (!color.preference.isEmpty()) {
                fromFilterPreference.add(preferenceSignal("color", color.preference, 0.6, color.evidence));
            }
            if (!bodyType.preference.isEmpty()) {
                fromFilterPreference.add(preferenceSignal("bodyType", bodyType.preference, 0.7, bodyType.evidence));
            }
            if (!region.preference.isEmpty()) {
                fromFilterPreference.add(preferenceSignal("region", region.preference, 0.55, region.evidence));
            }
            b.preferences(mergePreferences(b.build().getPreferences(), fromFilterPreference));
        }

        b.parserSource("LLM");
        return b.build();
    }

    private RecommendationQueryPlan heuristicPlan(String query) {
        String q = query == null ? "" : query.trim();
        String lower = q.toLowerCase(Locale.ROOT);

        RecommendationQueryPlan.RecommendationQueryPlanBuilder b = RecommendationQueryPlan.builder();
        b.textQuery(q);
        b.intent(detectIntent(lower));
        b.confidence(0.55);
        b.parserSource("HEURISTIC");

        List<RecommendationQueryPlan.PreferenceSignal> preferences = new ArrayList<>();
        List<RecommendationQueryPlan.EvidenceSpan> evidence = new ArrayList<>();

        ParsedEnumResult fuel = extractEnumValues(q, FUEL_PATTERNS, "fuel", this::normalizeFuel, ALLOWED_FUELS);
        if (!fuel.include.isEmpty()) b.fuel(joinCsv(fuel.include));
        if (!fuel.exclude.isEmpty()) b.excludeFuel(joinCsv(fuel.exclude));
        preferences.addAll(toPreferenceSignals("fuel", fuel.preference, fuel.preferenceEvidence));
        evidence.addAll(fuel.evidenceSpans);

        ParsedEnumResult color = extractEnumValues(q, COLOR_PATTERNS, "color", this::normalizeColor, ALLOWED_COLORS);
        if (!color.include.isEmpty()) b.color(joinCsv(color.include));
        if (!color.exclude.isEmpty()) b.excludeColor(joinCsv(color.exclude));
        preferences.addAll(toPreferenceSignals("color", color.preference, color.preferenceEvidence));
        evidence.addAll(color.evidenceSpans);

        ParsedEnumResult bodyType = extractEnumValues(q, BODYTYPE_PATTERNS, "bodyType", this::normalizeBodyType, ALLOWED_BODY_TYPES);
        if (!bodyType.include.isEmpty()) b.bodyTypes(new ArrayList<>(bodyType.include));
        if (!bodyType.exclude.isEmpty()) b.excludeBodyTypes(new ArrayList<>(bodyType.exclude));
        preferences.addAll(toPreferenceSignals("bodyType", bodyType.preference, bodyType.preferenceEvidence));
        evidence.addAll(bodyType.evidenceSpans);

        ParsedEnumResult region = extractEnumValues(q, REGION_PATTERNS, "region", this::normalizeRegion, null);
        if (!region.include.isEmpty()) b.region(joinCsv(region.include));
        if (!region.exclude.isEmpty()) b.excludeRegion(joinCsv(region.exclude));
        preferences.addAll(toPreferenceSignals("region", region.preference, region.preferenceEvidence));
        evidence.addAll(region.evidenceSpans);

        PriceBound price = extractPriceBounds(q);
        b.minPrice(price.min);
        b.maxPrice(price.max);
        evidence.addAll(price.evidenceSpans);

        YearBound year = extractYearBounds(q);
        b.minYear(year.min);
        b.maxYear(year.max);
        evidence.addAll(year.evidenceSpans);

        KmBound km = extractMileageBounds(q);
        b.minKm(km.min);
        b.maxKm(km.max);
        evidence.addAll(km.evidenceSpans);

        String sort = extractSortHint(lower);
        if (sort != null) b.sort(sort);

        preferences.addAll(extractModePreferences(q));

        b.preferences(preferences);
        b.evidenceSpans(evidence);
        b.askedFields(inferAskedFields(b.build(), lower));
        return b.build();
    }

    private RecommendationQueryPlan mergePreferLlm(
            RecommendationQueryPlan llm,
            RecommendationQueryPlan fallback,
            boolean lowConfidence
    ) {
        if (llm == null) return fallback != null ? fallback : RecommendationQueryPlan.builder().build();
        if (fallback == null) return llm;

        RecommendationQueryPlan strong = lowConfidence ? fallback : llm;
        RecommendationQueryPlan weak = lowConfidence ? llm : fallback;

        return RecommendationQueryPlan.builder()
                .textQuery(firstNonBlank(strong.getTextQuery(), weak.getTextQuery()))
                .makerCode(firstNonBlank(strong.getMakerCode(), weak.getMakerCode()))
                .maker(firstNonBlank(strong.getMaker(), weak.getMaker()))
                .modelCode(firstNonBlank(strong.getModelCode(), weak.getModelCode()))
                .model(firstNonBlank(strong.getModel(), weak.getModel()))
                .bodyTypes(firstNonEmptyList(strong.getBodyTypes(), weak.getBodyTypes()))
                .excludeBodyTypes(firstNonEmptyList(strong.getExcludeBodyTypes(), weak.getExcludeBodyTypes()))
                .fuel(firstNonBlank(strong.getFuel(), weak.getFuel()))
                .excludeFuel(firstNonBlank(strong.getExcludeFuel(), weak.getExcludeFuel()))
                .color(firstNonBlank(strong.getColor(), weak.getColor()))
                .excludeColor(firstNonBlank(strong.getExcludeColor(), weak.getExcludeColor()))
                .region(firstNonBlank(strong.getRegion(), weak.getRegion()))
                .excludeRegion(firstNonBlank(strong.getExcludeRegion(), weak.getExcludeRegion()))
                .minPrice(firstNonNull(strong.getMinPrice(), weak.getMinPrice()))
                .maxPrice(firstNonNull(strong.getMaxPrice(), weak.getMaxPrice()))
                .minYear(firstNonNull(strong.getMinYear(), weak.getMinYear()))
                .maxYear(firstNonNull(strong.getMaxYear(), weak.getMaxYear()))
                .minKm(firstNonNull(strong.getMinKm(), weak.getMinKm()))
                .maxKm(firstNonNull(strong.getMaxKm(), weak.getMaxKm()))
                .sort(firstNonBlank(strong.getSort(), weak.getSort()))
                .intent(firstNonBlank(strong.getIntent(), weak.getIntent()))
                .confidence(firstNonNull(llm.getConfidence(), fallback.getConfidence()))
                .askedFields(firstNonEmptyList(llm.getAskedFields(), fallback.getAskedFields()))
                .preferences(mergePreferences(llm.getPreferences(), fallback.getPreferences()))
                .evidenceSpans(mergeEvidence(llm.getEvidenceSpans(), fallback.getEvidenceSpans()))
                .parserSource(lowConfidence ? "LLM_LOW_CONFIDENCE" : "LLM")
                .build();
    }

    private RecommendationQueryPlan sanitize(RecommendationQueryPlan in, String originalQuery, String source) {
        RecommendationQueryPlan.RecommendationQueryPlanBuilder b = RecommendationQueryPlan.builder();

        b.textQuery(firstNonBlank(in != null ? in.getTextQuery() : null, originalQuery));
        b.makerCode(trimOrNull(in != null ? in.getMakerCode() : null));
        b.maker(trimOrNull(in != null ? in.getMaker() : null));
        b.modelCode(trimOrNull(in != null ? in.getModelCode() : null));
        b.model(trimOrNull(in != null ? in.getModel() : null));

        List<String> bodyTypes = normalizeBodyTypes(in != null ? in.getBodyTypes() : null);
        List<String> excludeBodyTypes = normalizeBodyTypes(in != null ? in.getExcludeBodyTypes() : null);
        b.bodyTypes(bodyTypes);
        b.excludeBodyTypes(excludeBodyTypes);

        b.fuel(normalizeCsvValues(in != null ? in.getFuel() : null, this::normalizeFuel, ALLOWED_FUELS));
        b.excludeFuel(normalizeCsvValues(in != null ? in.getExcludeFuel() : null, this::normalizeFuel, ALLOWED_FUELS));
        b.color(normalizeCsvValues(in != null ? in.getColor() : null, this::normalizeColor, ALLOWED_COLORS));
        b.excludeColor(normalizeCsvValues(in != null ? in.getExcludeColor() : null, this::normalizeColor, ALLOWED_COLORS));
        b.region(normalizeCsvValues(in != null ? in.getRegion() : null, this::normalizeRegion, null));
        b.excludeRegion(normalizeCsvValues(in != null ? in.getExcludeRegion() : null, this::normalizeRegion, null));

        Integer minPrice = clampInt(in != null ? in.getMinPrice() : null, 0, PRICE_MAX);
        Integer maxPrice = clampInt(in != null ? in.getMaxPrice() : null, 0, PRICE_MAX);
        Integer[] queryPriceBand = extractPriceBandManwon(originalQuery);
        if (queryPriceBand != null) {
            minPrice = queryPriceBand[0];
            maxPrice = queryPriceBand[1];
        }
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            int tmp = minPrice;
            minPrice = maxPrice;
            maxPrice = tmp;
        }
        b.minPrice(minPrice);
        b.maxPrice(maxPrice);

        Integer minYear = clampInt(in != null ? in.getMinYear() : null, YEAR_MIN, YEAR_MAX);
        Integer maxYear = clampInt(in != null ? in.getMaxYear() : null, YEAR_MIN, YEAR_MAX);
        if (minYear != null && maxYear != null && minYear > maxYear) {
            int tmp = minYear;
            minYear = maxYear;
            maxYear = tmp;
        }
        b.minYear(minYear);
        b.maxYear(maxYear);

        Integer minKm = clampInt(in != null ? in.getMinKm() : null, 0, KM_MAX);
        Integer maxKm = clampInt(in != null ? in.getMaxKm() : null, 0, KM_MAX);
        Integer shortMileageMaxKm = extractShortMileageMaxKm(originalQuery);
        if (shortMileageMaxKm != null) maxKm = shortMileageMaxKm;
        if (minKm != null && maxKm != null && minKm > maxKm) {
            int tmp = minKm;
            minKm = maxKm;
            maxKm = tmp;
        }
        b.minKm(minKm);
        b.maxKm(maxKm);

        b.sort(normalizeSort(in != null ? in.getSort() : null));
        b.intent(normalizeIntent(in != null ? in.getIntent() : null));
        b.confidence(clampDouble(in != null ? in.getConfidence() : null, 0.0, 1.0));
        b.askedFields(normalizeAskedFields(in != null ? in.getAskedFields() : null));
        b.preferences(normalizePreferences(in != null ? in.getPreferences() : null));
        b.evidenceSpans(normalizeEvidenceSpans(in != null ? in.getEvidenceSpans() : null));
        b.parserSource(source);
        return b.build();
    }

    private static List<String> inferAskedFields(RecommendationQueryPlan plan, String lowerQuery) {
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        if (plan.getMinPrice() != null || plan.getMaxPrice() != null || lowerQuery.contains("가격") || lowerQuery.contains("만원")) fields.add("price");
        if (plan.getMinYear() != null || plan.getMaxYear() != null || lowerQuery.contains("연식") || lowerQuery.contains("년")) fields.add("year");
        if (plan.getMinKm() != null || plan.getMaxKm() != null || lowerQuery.contains("주행") || lowerQuery.contains("키로")) fields.add("mileage");
        if (trimOrNull(plan.getFuel()) != null || trimOrNull(plan.getExcludeFuel()) != null) fields.add("fuel");
        if (trimOrNull(plan.getColor()) != null || trimOrNull(plan.getExcludeColor()) != null) fields.add("color");
        if ((plan.getBodyTypes() != null && !plan.getBodyTypes().isEmpty()) || (plan.getExcludeBodyTypes() != null && !plan.getExcludeBodyTypes().isEmpty())) fields.add("bodyType");
        if (trimOrNull(plan.getRegion()) != null || trimOrNull(plan.getExcludeRegion()) != null) fields.add("region");
        if (trimOrNull(plan.getMaker()) != null || trimOrNull(plan.getMakerCode()) != null) fields.add("maker");
        if (trimOrNull(plan.getModel()) != null || trimOrNull(plan.getModelCode()) != null) fields.add("model");
        if (trimOrNull(plan.getSort()) != null) fields.add("sort");
        return new ArrayList<>(fields);
    }

    private List<RecommendationQueryPlan.PreferenceSignal> extractModePreferences(String query) {
        List<RecommendationQueryPlan.PreferenceSignal> out = new ArrayList<>();
        if (query == null || query.isBlank()) return out;
        String q = query.toLowerCase(Locale.ROOT);
        if (q.contains("가성비") || q.contains("저예산")) {
            out.add(preferenceSignal("mode", List.of("VALUE"), 0.85, "가성비/저예산"));
        }
        if (q.contains("정숙") || q.contains("장거리")) {
            out.add(preferenceSignal("mode", List.of("COMMUTE"), 0.75, "정숙/장거리"));
        }
        if (q.contains("도심") || q.contains("출퇴근")) {
            out.add(preferenceSignal("mode", List.of("COMMUTE"), 0.8, "도심/출퇴근"));
        }
        if (q.contains("가족") || q.contains("패밀리")) {
            out.add(preferenceSignal("mode", List.of("FAMILY"), 0.8, "가족/패밀리"));
        }
        return out;
    }

    private static String detectIntent(String lower) {
        if (lower == null || lower.isBlank()) return "GENERAL";
        if (lower.contains("가성비") || lower.contains("저렴") || lower.contains("예산")) return "VALUE";
        if (lower.contains("가족") || lower.contains("패밀리")) return "FAMILY";
        if (lower.contains("출퇴근") || lower.contains("통근") || lower.contains("도심")) return "COMMUTE";
        if (lower.contains("안전") || lower.contains("무사고")) return "SAFETY";
        if (lower.contains("데이트") || lower.contains("감성")) return "DATE";
        return "GENERAL";
    }

    private String extractSortHint(String lower) {
        if (lower == null) return null;
        if (lower.contains("저렴") || lower.contains("낮은 가격") || lower.contains("가성비")) return "LOW_PRICE";
        if (lower.contains("저주행") || lower.contains("짧은 주행")) return "LOW_KM";
        if (lower.contains("신형") || lower.contains("최신")) return "NEW_YEAR";
        return null;
    }

    private ParsedEnumResult extractEnumValues(
            String query,
            List<EnumPattern> patterns,
            String field,
            java.util.function.Function<String, String> normalizer,
            Set<String> allowed
    ) {
        LinkedHashSet<String> include = new LinkedHashSet<>();
        LinkedHashSet<String> exclude = new LinkedHashSet<>();
        LinkedHashSet<String> preference = new LinkedHashSet<>();
        List<RecommendationQueryPlan.EvidenceSpan> spans = new ArrayList<>();
        Map<String, String> prefEvidence = new LinkedHashMap<>();

        if (query == null || query.isBlank()) {
            return new ParsedEnumResult(include, exclude, preference, spans, prefEvidence);
        }

        for (EnumPattern p : patterns) {
            Matcher m = p.pattern.matcher(query);
            while (m.find()) {
                String raw = m.group();
                String normalized = normalizer.apply(p.canonical);
                if (normalized == null) continue;
                if (allowed != null && !allowed.contains(normalized)) continue;

                ConstraintMode mode = detectMode(query, m.start(), m.end());
                switch (mode) {
                    case EXCLUDE -> exclude.add(normalized);
                    case PREFERENCE -> {
                        preference.add(normalized);
                        prefEvidence.putIfAbsent(normalized, raw);
                    }
                    case INCLUDE -> include.add(normalized);
                }
                spans.add(RecommendationQueryPlan.EvidenceSpan.builder()
                        .field(field)
                        .value(normalized)
                        .text(raw)
                        .start(m.start())
                        .end(m.end())
                        .mode(mode.name().toLowerCase(Locale.ROOT))
                        .build());
            }
        }

        include.removeAll(exclude);
        return new ParsedEnumResult(include, exclude, preference, spans, prefEvidence);
    }

    private static ConstraintMode detectMode(String query, int start, int end) {
        int from = Math.max(0, start - MODE_WINDOW_SIZE);
        int to = Math.min(query.length(), end + MODE_WINDOW_SIZE);
        String ctx = query.substring(from, to).toLowerCase(Locale.ROOT);

        if (containsAny(ctx, EXCLUDE_KEYWORDS)) return ConstraintMode.EXCLUDE;
        if (containsAny(ctx, PREFERENCE_KEYWORDS)) return ConstraintMode.PREFERENCE;
        if (containsAny(ctx, INCLUDE_KEYWORDS)) return ConstraintMode.INCLUDE;
        return ConstraintMode.INCLUDE;
    }

    private static boolean containsAny(String text, Collection<String> keywords) {
        if (text == null || keywords == null) return false;
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private PriceBound extractPriceBounds(String query) {
        Integer min = null;
        Integer max = null;
        List<RecommendationQueryPlan.EvidenceSpan> spans = new ArrayList<>();
        if (query == null || query.isBlank()) return new PriceBound(min, max, spans);

        Matcher range = PRICE_RANGE_PATTERN.matcher(query.replace(",", ""));
        if (range.find()) {
            Integer a = parseMoneyToManwon(range.group(1), range.group(2));
            Integer b = parseMoneyToManwon(range.group(3), range.group(4));
            if (a != null && b != null) {
                min = Math.min(a, b);
                max = Math.max(a, b);
                spans.add(span("price", min + "~" + max, range.group(), range.start(), range.end(), "include"));
            }
        }

        Matcher bound = PRICE_BOUND_PATTERN.matcher(query.replace(",", ""));
        while (bound.find()) {
            Integer value = parseMoneyToManwon(bound.group(1), bound.group(2));
            if (value == null) continue;
            ConstraintMode mode = detectRangeMode(query, bound.end());
            if (mode == ConstraintMode.MAX) {
                max = max == null ? value : Math.min(max, value);
                spans.add(span("price", String.valueOf(value), bound.group(), bound.start(), bound.end(), "include"));
            } else if (mode == ConstraintMode.MIN) {
                min = min == null ? value : Math.max(min, value);
                spans.add(span("price", String.valueOf(value), bound.group(), bound.start(), bound.end(), "include"));
            }
        }

        return new PriceBound(min, max, spans);
    }

    private YearBound extractYearBounds(String query) {
        Integer min = null;
        Integer max = null;
        List<RecommendationQueryPlan.EvidenceSpan> spans = new ArrayList<>();
        if (query == null || query.isBlank()) return new YearBound(min, max, spans);

        Matcher m = YEAR_PATTERN.matcher(query);
        while (m.find()) {
            Integer year = parseYearToken(m.group(1));
            if (year == null) continue;
            ConstraintMode mode = detectRangeMode(query, m.end());
            if (mode == ConstraintMode.MAX) {
                max = max == null ? year : Math.min(max, year);
            } else if (mode == ConstraintMode.MIN) {
                min = min == null ? year : Math.max(min, year);
            } else {
                min = year;
                max = year;
            }
            spans.add(span("year", String.valueOf(year), m.group(), m.start(), m.end(), "include"));
        }

        String lower = query.toLowerCase(Locale.ROOT);
        int nowYear = Year.now().getValue();
        if (min == null && (lower.contains("신형") || lower.contains("최신") || lower.contains("최근연식"))) {
            min = nowYear - 3;
        }
        return new YearBound(min, max, spans);
    }

    private KmBound extractMileageBounds(String query) {
        Integer min = null;
        Integer max = null;
        List<RecommendationQueryPlan.EvidenceSpan> spans = new ArrayList<>();
        if (query == null || query.isBlank()) return new KmBound(min, max, spans);

        Matcher m = KM_PATTERN.matcher(query.replace(",", ""));
        while (m.find()) {
            Integer km = parseKmValue(m.group(1), m.group(2));
            if (km == null) continue;
            ConstraintMode mode = detectRangeMode(query, m.end());
            if (mode == ConstraintMode.MAX) {
                max = max == null ? km : Math.min(max, km);
            } else if (mode == ConstraintMode.MIN) {
                min = min == null ? km : Math.max(min, km);
            } else {
                max = max == null ? km : Math.min(max, km);
            }
            spans.add(span("mileage", String.valueOf(km), m.group(), m.start(), m.end(), "include"));
        }

        Integer shortMileage = extractShortMileageMaxKm(query);
        if (shortMileage != null) {
            max = shortMileage;
        }
        return new KmBound(min, max, spans);
    }

    private static Integer parseMoneyToManwon(String numberRaw, String unitRaw) {
        if (numberRaw == null) return null;
        try {
            double n = Double.parseDouble(numberRaw.trim());
            String unit = unitRaw == null ? "만" : unitRaw.trim().toLowerCase(Locale.ROOT);
            if ("억".equals(unit)) return (int) Math.round(n * 10000.0);
            if ("천".equals(unit)) return (int) Math.round(n * 1000.0);
            if ("원".equals(unit)) return (int) Math.round(n / 10000.0);
            return (int) Math.round(n);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Integer parseYearToken(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            int v = Integer.parseInt(token.trim());
            if (v >= 1900) return v;
            if (v >= 90) return 1900 + v;
            return 2000 + v;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Integer parseKmValue(String numberRaw, String manToken) {
        if (numberRaw == null) return null;
        try {
            double n = Double.parseDouble(numberRaw.trim());
            if (manToken != null && !manToken.isBlank()) {
                return (int) Math.round(n * 10000.0);
            }
            return (int) Math.round(n);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ConstraintMode detectRangeMode(String query, int anchor) {
        int to = Math.min(query.length(), anchor + 10);
        String right = query.substring(anchor, to).toLowerCase(Locale.ROOT);
        if (containsAny(right, List.of("이하", "미만", "까지", "이내", "넘지"))) return ConstraintMode.MAX;
        if (containsAny(right, List.of("이상", "초과", "부터"))) return ConstraintMode.MIN;

        int from = Math.max(0, anchor - 10);
        String left = query.substring(from, anchor).toLowerCase(Locale.ROOT);
        if (containsAny(left, List.of("이하", "미만", "까지", "이내", "넘지"))) return ConstraintMode.MAX;
        if (containsAny(left, List.of("이상", "초과", "부터"))) return ConstraintMode.MIN;
        return ConstraintMode.UNKNOWN;
    }

    private static RecommendationQueryPlan.EvidenceSpan span(String field, String value, String text, int start, int end, String mode) {
        return RecommendationQueryPlan.EvidenceSpan.builder()
                .field(field)
                .value(value)
                .text(text)
                .start(start)
                .end(end)
                .mode(mode)
                .build();
    }

    private ParsedListFilter parseListFilter(JsonNode node, String field, Set<String> allowed) throws IOException {
        if (node == null || node.isNull()) return ParsedListFilter.empty();
        if (node.isTextual()) {
            List<String> one = normalizeCsv(node.asText(), field, allowed);
            return new ParsedListFilter(one, List.of(), List.of(), null);
        }
        if (!node.isObject()) {
            throw new IOException(field + " must be object or string");
        }
        assertAllowedKeys(node, FILTER_OBJECT_ALLOWED_KEYS, field);

        List<String> include = normalizeCsv(readText(node, "include"), field, allowed);
        include = mergeUnique(include, normalizeList(readStringList(node.get("include")), field, allowed));

        List<String> exclude = normalizeCsv(readText(node, "exclude"), field, allowed);
        exclude = mergeUnique(exclude, normalizeList(readStringList(node.get("exclude")), field, allowed));

        List<String> pref = normalizeCsv(readText(node, "preference"), field, allowed);
        pref = mergeUnique(pref, normalizeList(readStringList(node.get("preference")), field, allowed));

        if (include.isEmpty()) {
            String value = readText(node, "value");
            include = normalizeCsv(value, field, allowed);
        }

        String evidence = trimOrNull(readText(node, "evidence"));
        return new ParsedListFilter(include, exclude, pref, evidence);
    }

    private ParsedRangeFilter parseRangeFilter(JsonNode node, String field) throws IOException {
        if (node == null || node.isNull()) return ParsedRangeFilter.empty();
        if (!node.isObject()) throw new IOException(field + " must be object");
        assertAllowedKeys(node, RANGE_ALLOWED_KEYS, field);
        Integer min = readInt(node, "min");
        Integer max = readInt(node, "max");
        return new ParsedRangeFilter(min, max, trimOrNull(readText(node, "evidence")));
    }

    private List<RecommendationQueryPlan.PreferenceSignal> parsePreferences(JsonNode node) throws IOException {
        if (node == null || node.isNull()) return List.of();
        if (!node.isArray()) throw new IOException("preferences must be array");

        List<RecommendationQueryPlan.PreferenceSignal> out = new ArrayList<>();
        for (JsonNode item : node) {
            if (item == null || item.isNull()) continue;
            if (!item.isObject()) throw new IOException("preferences item must be object");
            assertAllowedKeys(item, PREFERENCE_ALLOWED_KEYS, "preferences[]");

            String field = trimOrNull(readText(item, "field"));
            if (field == null) continue;
            List<String> values = readStringList(item.get("values"));
            Double weight = clampDouble(readDouble(item, "weight"), 0.0, 1.0);
            String evidence = trimOrNull(readText(item, "evidence"));
            out.add(preferenceSignal(field, values, weight != null ? weight : 0.5, evidence));
        }
        return out;
    }

    private List<RecommendationQueryPlan.EvidenceSpan> parseEvidenceSpans(JsonNode node) throws IOException {
        if (node == null || node.isNull()) return List.of();
        if (!node.isArray()) throw new IOException("evidence_spans must be array");

        List<RecommendationQueryPlan.EvidenceSpan> out = new ArrayList<>();
        for (JsonNode item : node) {
            if (item == null || item.isNull()) continue;
            if (!item.isObject()) throw new IOException("evidence_spans item must be object");
            assertAllowedKeys(item, EVIDENCE_ALLOWED_KEYS, "evidence_spans[]");

            out.add(RecommendationQueryPlan.EvidenceSpan.builder()
                    .field(trimOrNull(readText(item, "field")))
                    .value(trimOrNull(readText(item, "value")))
                    .text(trimOrNull(readText(item, "text")))
                    .start(readInt(item, "start"))
                    .end(readInt(item, "end"))
                    .mode(trimOrNull(readText(item, "mode")))
                    .build());
        }
        return out;
    }

    private static void assertAllowedKeys(JsonNode node, Set<String> allowedKeys, String context) throws IOException {
        if (node == null || !node.isObject()) return;
        var names = node.fieldNames();
        while (names.hasNext()) {
            String key = names.next();
            if (!allowedKeys.contains(key)) {
                throw new IOException("Unknown key in " + context + ": " + key);
            }
        }
    }

    private static JsonNode firstNode(JsonNode node, String... keys) {
        if (node == null || keys == null) return null;
        for (String key : keys) {
            if (key != null && node.has(key)) return node.get(key);
        }
        return null;
    }

    private static String readText(JsonNode node, String key) {
        if (node == null || key == null || !node.has(key) || node.get(key).isNull()) return null;
        JsonNode v = node.get(key);
        if (v.isTextual()) return trimOrNull(v.asText());
        if (v.isNumber() || v.isBoolean()) return trimOrNull(v.asText());
        return null;
    }

    private static Integer readInt(JsonNode node, String key) {
        if (node == null || key == null || !node.has(key) || node.get(key).isNull()) return null;
        JsonNode v = node.get(key);
        if (v.isInt() || v.isLong() || v.isShort()) return v.asInt();
        if (v.isTextual()) {
            try {
                return Integer.parseInt(v.asText().replace(",", "").trim());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static Double readDouble(JsonNode node, String key) {
        if (node == null || key == null || !node.has(key) || node.get(key).isNull()) return null;
        JsonNode v = node.get(key);
        if (v.isDouble() || v.isFloat() || v.isNumber()) return v.asDouble();
        if (v.isTextual()) {
            try {
                return Double.parseDouble(v.asText().trim());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static List<String> readStringList(JsonNode node) {
        if (node == null || node.isNull()) return List.of();
        if (node.isArray()) {
            List<String> out = new ArrayList<>();
            for (JsonNode it : node) {
                if (it == null || it.isNull()) continue;
                String s = trimOrNull(it.asText());
                if (s != null) out.add(s);
            }
            return out;
        }
        String one = trimOrNull(node.asText());
        return one != null ? List.of(one) : List.of();
    }

    private static String extractFirstJsonObject(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        int start = s.indexOf('{');
        if (start < 0) return s;

        int depth = 0;
        boolean inString = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (inString) continue;
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) return s.substring(start, i + 1);
            }
        }
        return s.substring(start);
    }

    private static List<String> normalizeBodyTypes(List<String> bodyTypes) {
        if (bodyTypes == null || bodyTypes.isEmpty()) return List.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String item : bodyTypes) {
            String v = normalizeStaticBodyType(item);
            if (v != null && ALLOWED_BODY_TYPES.contains(v)) out.add(v);
        }
        return new ArrayList<>(out);
    }

    private static String normalizeStaticBodyType(String raw) {
        String t = trimOrNull(raw);
        if (t == null) return null;
        String lower = t.toLowerCase(Locale.ROOT);
        if ("suv".equals(lower)) return "SUV";
        if ("rv".equals(lower)) return "RV";
        return switch (t) {
            case "준중형차" -> "준중형";
            case "중형차", "중대형" -> "중형";
            case "대형차", "준대형" -> "대형";
            case "소형차" -> "소형";
            case "승합차" -> "승합";
            default -> ALLOWED_BODY_TYPES.contains(t) ? t : null;
        };
    }

    private String normalizeFuel(String raw) {
        String t = trimOrNull(raw);
        if (t == null) return null;
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.contains("휘발유") || lower.equals("gasoline") || lower.equals("petrol")) return "가솔린";
        if (lower.contains("디젤") || lower.contains("경유") || lower.equals("diesel")) return "디젤";
        if (lower.contains("전기") || lower.equals("ev") || lower.equals("electric")) return "전기";
        if (lower.contains("수소") || lower.equals("fcev")) return "수소";
        if (lower.contains("lpg(일반인)") || lower.contains("일반인 lpg")) return "LPG(일반인)";
        if (lower.contains("lpg") || lower.contains("엘피지") || lower.contains("가스")) return "LPG";
        if (lower.contains("하이브리드") || lower.equals("hev") || lower.contains("하브")) return "하이브리드";
        return ALLOWED_FUELS.contains(t) ? t : null;
    }

    private String normalizeColor(String raw) {
        String t = trimOrNull(raw);
        if (t == null) return null;
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.contains("white") || lower.contains("화이트") || lower.equals("흰") || lower.equals("흰색")) return "흰색";
        if (lower.contains("black") || lower.contains("블랙") || lower.equals("검정") || lower.equals("검정색")) return "검정색";
        if (lower.contains("gray") || lower.contains("그레이") || lower.contains("쥐색") || lower.equals("회색")) return "회색";
        if (lower.contains("silver") || lower.contains("실버") || lower.equals("은색")) return "은색";
        if (lower.contains("gold") || lower.contains("골드") || lower.equals("금색")) return "금색";
        if (lower.contains("blue") || lower.contains("블루") || lower.contains("파랑") || lower.contains("청색")) return "파랑색";
        if (lower.contains("하늘") || lower.contains("sky")) return "하늘색";
        if (lower.contains("green") || lower.contains("그린") || lower.contains("초록") || lower.contains("녹색")) return "초록색";
        if (lower.contains("red") || lower.contains("레드") || lower.contains("빨강") || lower.contains("버건디") || lower.contains("와인")) return "빨강색";
        if (lower.contains("pink") || lower.contains("핑크") || lower.contains("분홍")) return "분홍색";
        if (lower.contains("yellow") || lower.contains("옐로") || lower.contains("노랑")) return "노랑색";
        if (lower.contains("purple") || lower.contains("퍼플") || lower.contains("보라") || lower.contains("자주")) return "보라색";
        if (lower.contains("orange") || lower.contains("오렌지") || lower.contains("주황")) return "주황색";
        if (lower.contains("beige") || lower.contains("베이지") || lower.contains("아이보리") || lower.contains("미색")) return "미색";
        if (lower.contains("brown") || lower.contains("브라운") || lower.contains("갈색")) return "갈색";
        if (lower.contains("진주") || lower.contains("펄") || lower.contains("pearl")) return "진주색";
        return ALLOWED_COLORS.contains(t) ? t : null;
    }

    private String normalizeBodyType(String raw) {
        return normalizeStaticBodyType(raw);
    }

    private String normalizeRegion(String raw) {
        String t = trimOrNull(raw);
        if (t == null) return null;
        if ("수도권".equals(t)) return "서울,경기,인천";
        return t;
    }

    private static String normalizeIntent(String intent) {
        String s = trimOrNull(intent);
        if (s == null) return null;
        String up = s.toUpperCase(Locale.ROOT);
        return ALLOWED_INTENTS.contains(up) ? up : null;
    }

    private static String normalizeSort(String sort) {
        String s = trimOrNull(sort);
        if (s == null) return null;
        String up = s.toUpperCase(Locale.ROOT);
        return ALLOWED_SORTS.contains(up) ? up : null;
    }

    private static List<String> normalizeAskedFields(List<String> fields) {
        if (fields == null || fields.isEmpty()) return List.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String field : fields) {
            String f = trimOrNull(field);
            if (f == null) continue;
            if (ALLOWED_ASKED_FIELDS.contains(f)) out.add(f);
        }
        return new ArrayList<>(out);
    }

    private List<RecommendationQueryPlan.PreferenceSignal> normalizePreferences(List<RecommendationQueryPlan.PreferenceSignal> in) {
        if (in == null || in.isEmpty()) return List.of();
        List<RecommendationQueryPlan.PreferenceSignal> out = new ArrayList<>();
        for (RecommendationQueryPlan.PreferenceSignal p : in) {
            if (p == null) continue;
            String field = trimOrNull(p.getField());
            if (field == null) continue;
            List<String> values = new ArrayList<>();
            if (p.getValues() != null) {
                for (String v : p.getValues()) {
                    String token = switch (field) {
                        case "fuel" -> normalizeFuel(v);
                        case "color" -> normalizeColor(v);
                        case "bodyType" -> normalizeBodyType(v);
                        case "region" -> normalizeRegion(v);
                        default -> trimOrNull(v);
                    };
                    if (token != null) values.add(token);
                }
            }
            if (values.isEmpty()) continue;
            out.add(preferenceSignal(field, values, clampDouble(p.getWeight(), 0.0, 1.0), p.getEvidence()));
        }
        return out;
    }

    private static List<RecommendationQueryPlan.EvidenceSpan> normalizeEvidenceSpans(List<RecommendationQueryPlan.EvidenceSpan> in) {
        if (in == null || in.isEmpty()) return List.of();
        List<RecommendationQueryPlan.EvidenceSpan> out = new ArrayList<>();
        for (RecommendationQueryPlan.EvidenceSpan e : in) {
            if (e == null) continue;
            if (trimOrNull(e.getField()) == null || trimOrNull(e.getText()) == null) continue;
            out.add(RecommendationQueryPlan.EvidenceSpan.builder()
                    .field(trimOrNull(e.getField()))
                    .value(trimOrNull(e.getValue()))
                    .text(trimOrNull(e.getText()))
                    .start(e.getStart())
                    .end(e.getEnd())
                    .mode(trimOrNull(e.getMode()))
                    .build());
        }
        return out;
    }

    private static RecommendationQueryPlan.PreferenceSignal preferenceSignal(String field, List<String> values, Double weight, String evidence) {
        return RecommendationQueryPlan.PreferenceSignal.builder()
                .field(field)
                .values(values != null ? values : List.of())
                .weight(weight)
                .evidence(trimOrNull(evidence))
                .build();
    }

    private static List<RecommendationQueryPlan.PreferenceSignal> toPreferenceSignals(
            String field,
            Collection<String> values,
            Map<String, String> evidences
    ) {
        if (values == null || values.isEmpty()) return List.of();
        List<RecommendationQueryPlan.PreferenceSignal> out = new ArrayList<>();
        for (String value : values) {
            out.add(preferenceSignal(field, List.of(value), 0.6, evidences != null ? evidences.get(value) : null));
        }
        return out;
    }

    private static List<RecommendationQueryPlan.PreferenceSignal> mergePreferences(
            List<RecommendationQueryPlan.PreferenceSignal> first,
            List<RecommendationQueryPlan.PreferenceSignal> second
    ) {
        List<RecommendationQueryPlan.PreferenceSignal> out = new ArrayList<>();
        if (first != null) out.addAll(first);
        if (second != null) out.addAll(second);
        return out;
    }

    private static List<RecommendationQueryPlan.EvidenceSpan> mergeEvidence(
            List<RecommendationQueryPlan.EvidenceSpan> first,
            List<RecommendationQueryPlan.EvidenceSpan> second
    ) {
        List<RecommendationQueryPlan.EvidenceSpan> out = new ArrayList<>();
        if (first != null) out.addAll(first);
        if (second != null) out.addAll(second);
        return out;
    }

    private List<String> normalizeCsv(String raw, String field, Set<String> allowed) {
        if (raw == null || raw.isBlank()) return List.of();
        String[] parts = raw.split(",");
        List<String> out = new ArrayList<>();
        for (String part : parts) {
            String token = switch (field) {
                case "fuel" -> normalizeFuel(part);
                case "color" -> normalizeColor(part);
                case "bodyType" -> normalizeBodyType(part);
                case "region" -> normalizeRegion(part);
                default -> trimOrNull(part);
            };
            if (token == null) continue;
            if (allowed != null && !allowed.contains(token)) continue;
            out.add(token);
        }
        return dedup(out);
    }

    private List<String> normalizeList(List<String> values, String field, Set<String> allowed) {
        if (values == null || values.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        for (String value : values) {
            out.addAll(normalizeCsv(value, field, allowed));
        }
        return dedup(out);
    }

    private static List<String> dedup(List<String> in) {
        if (in == null || in.isEmpty()) return List.of();
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String s : in) {
            String t = trimOrNull(s);
            if (t != null) set.add(t);
        }
        return new ArrayList<>(set);
    }

    private static List<String> mergeUnique(List<String> a, List<String> b) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (a != null) out.addAll(a);
        if (b != null) out.addAll(b);
        return new ArrayList<>(out);
    }

    private static String normalizeCsvValues(
            String raw,
            java.util.function.Function<String, String> normalizer,
            Set<String> allowed
    ) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split(",");
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String part : parts) {
            String token = normalizer.apply(part);
            if (token == null) continue;
            if (allowed != null && !allowed.contains(token)) continue;
            out.add(token);
        }
        return out.isEmpty() ? null : String.join(",", out);
    }

    private static String joinCsv(Collection<String> values) {
        if (values == null || values.isEmpty()) return null;
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String value : values) {
            String t = trimOrNull(value);
            if (t != null) set.add(t);
        }
        return set.isEmpty() ? null : String.join(",", set);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            String t = trimOrNull(value);
            if (t != null) return t;
        }
        return null;
    }

    private static <T> List<T> firstNonEmptyList(List<T> a, List<T> b) {
        if (a != null && !a.isEmpty()) return a;
        if (b != null && !b.isEmpty()) return b;
        return List.of();
    }

    private static Integer firstNonNull(Integer a, Integer b) {
        return a != null ? a : b;
    }

    private static Double firstNonNull(Double a, Double b) {
        return a != null ? a : b;
    }

    private static Integer clampInt(Integer n, int min, int max) {
        if (n == null) return null;
        return Math.max(min, Math.min(max, n));
    }

    private static Double clampDouble(Double n, double min, double max) {
        if (n == null) return null;
        return Math.max(min, Math.min(max, n));
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static Integer[] extractPriceBandManwon(String query) {
        if (query == null || query.isBlank()) return null;
        String compact = query.replace(",", "");

        Matcher mMan = Pattern.compile("(\\d{3,5})\\s*만\\s*원?\\s*대").matcher(compact);
        if (mMan.find()) {
            int min = clampInt(Integer.parseInt(mMan.group(1)), 0, PRICE_MAX);
            int max = clampInt(min + 999, 0, PRICE_MAX);
            return new Integer[]{min, max};
        }

        Matcher mCheonMan = Pattern.compile("(\\d{1,2})\\s*천\\s*만\\s*원?\\s*대").matcher(compact);
        if (mCheonMan.find()) {
            int min = clampInt(Integer.parseInt(mCheonMan.group(1)) * 1000, 0, PRICE_MAX);
            int max = clampInt(min + 999, 0, PRICE_MAX);
            return new Integer[]{min, max};
        }

        Matcher mEok = Pattern.compile("(\\d{1,2})\\s*억\\s*대").matcher(compact);
        if (mEok.find()) {
            int min = clampInt(Integer.parseInt(mEok.group(1)) * 10000, 0, PRICE_MAX);
            int max = clampInt(min + 9999, 0, PRICE_MAX);
            return new Integer[]{min, max};
        }
        return null;
    }

    private static Integer extractShortMileageMaxKm(String query) {
        if (query == null || query.isBlank()) return null;
        String lower = query.toLowerCase(Locale.ROOT);
        if (lower.contains("주행거리 짧")
                || lower.contains("짧은 주행거리")
                || lower.contains("저주행")
                || lower.contains("주행거리 적")
                || lower.contains("키로수 짧")
                || lower.contains("키로수 적")) {
            return 70000;
        }
        return null;
    }

    private static EnumPattern enumPattern(String regex, String canonical) {
        return new EnumPattern(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), canonical);
    }

    private enum ConstraintMode {
        INCLUDE,
        EXCLUDE,
        PREFERENCE,
        MIN,
        MAX,
        UNKNOWN
    }

    private record EnumPattern(Pattern pattern, String canonical) {
    }

    private record ParsedListFilter(List<String> include, List<String> exclude, List<String> preference, String evidence) {
        private static ParsedListFilter empty() {
            return new ParsedListFilter(List.of(), List.of(), List.of(), null);
        }
    }

    private record ParsedRangeFilter(Integer min, Integer max, String evidence) {
        private static ParsedRangeFilter empty() {
            return new ParsedRangeFilter(null, null, null);
        }
    }

    private record ParsedEnumResult(
            LinkedHashSet<String> include,
            LinkedHashSet<String> exclude,
            LinkedHashSet<String> preference,
            List<RecommendationQueryPlan.EvidenceSpan> evidenceSpans,
            Map<String, String> preferenceEvidence
    ) {
    }

    private record PriceBound(Integer min, Integer max, List<RecommendationQueryPlan.EvidenceSpan> evidenceSpans) {
    }

    private record YearBound(Integer min, Integer max, List<RecommendationQueryPlan.EvidenceSpan> evidenceSpans) {
    }

    private record KmBound(Integer min, Integer max, List<RecommendationQueryPlan.EvidenceSpan> evidenceSpans) {
    }
}

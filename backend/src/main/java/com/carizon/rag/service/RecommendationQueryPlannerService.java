package com.carizon.rag.service;

import com.carizon.rag.dto.RecommendationQueryPlan;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 자연어 질의를 구조화된 검색 플랜으로 변환.
 * 1) 휴리스틱 기반 플랜 생성
 * 2) 서버 측 강제 정규화/검증(허용 필드만)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationQueryPlannerService {

    private static final Set<String> ALLOWED_SORTS = Set.of("RECENT", "LOW_PRICE", "LOW_KM", "NEW_YEAR");
    private static final Set<String> ALLOWED_FUELS = Set.of("가솔린", "디젤", "하이브리드", "전기", "LPG");
    private static final Set<String> ALLOWED_BODY_TYPES = Set.of(
            "경차", "소형", "준중형", "중형", "대형", "SUV", "RV", "스포츠카", "상용", "트럭", "승합", "화물", "기타"
    );

    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    public RecommendationQueryPlan plan(String userQuery) {
        return plan(userQuery, true);
    }

    public RecommendationQueryPlan plan(String userQuery, boolean useLlm) {
        if (userQuery == null || userQuery.isBlank()) {
            return RecommendationQueryPlan.builder().build();
        }

        RecommendationQueryPlan heuristic = heuristicPlan(userQuery);
        if (!useLlm) {
            RecommendationQueryPlan sanitized = sanitize(heuristic, userQuery);
            log.info("[QueryPlanner] heuristic plan (LLM disabled): {}", sanitized);
            return sanitized;
        }

        try {
            String prompt = """
                사용자의 중고차 추천 질의를 JSON으로만 구조화하세요.
                반드시 JSON 객체 하나만 출력하고, 설명/마크다운/코드블록을 쓰지 마세요.

                출력 스키마(필요한 항목만 채움):
                {
                  "textQuery": "string",
                  "makerCode": "string",
                  "maker": "string",
                  "modelCode": "string",
                  "model": "string",
                  "bodyTypes": ["경차|소형|준중형|중형|대형|SUV|RV|스포츠카|상용|트럭|승합|화물|기타"],
                  "fuel": "가솔린|디젤|하이브리드|전기|LPG",
                  "minPrice": 0,
                  "maxPrice": 0,
                  "minYear": 1990,
                  "maxYear": 2030,
                  "maxKm": 300000,
                  "sort": "RECENT|LOW_PRICE|LOW_KM|NEW_YEAR",
                  "intent": "VALUE|SAFETY|DATE|FAMILY|COMMUTE|LOW_BUDGET|GENERAL"
                }

                규칙:
                - 확실하지 않으면 해당 필드는 생략.
                - 수치는 정수.
                - 가격(minPrice/maxPrice)은 반드시 만원 단위로 채움. 예: "2000만원대" -> minPrice=2000, maxPrice=2999.
                - "주행거리 짧다/저주행" 요청은 maxKm=30000으로 채움.
                - textQuery는 검색 핵심 키워드 3~12단어로 정제.
                - 패밀리/가족 용도이고 차종을 명시하지 않았다면 bodyTypes는 ["SUV","RV"]를 사용.
                - 사용자가 SUV 또는 RV를 명시하면 해당 차종만 사용.

                사용자 질의:
                """ + userQuery.trim();

            String raw = llmService.generateResponse(prompt, new LlmService.GenerationOptions(280, 0.0));
            RecommendationQueryPlan llmPlan = parsePlanJson(raw);
            RecommendationQueryPlan merged = mergePreferLlm(llmPlan, heuristic);
            RecommendationQueryPlan sanitized = sanitize(merged, userQuery);
            log.info("[QueryPlanner] plan from LLM: {}", sanitized);
            return sanitized;
        } catch (Exception e) {
            RecommendationQueryPlan fallback = sanitize(heuristic, userQuery);
            log.warn("[QueryPlanner] LLM unavailable or parse failed, fallback heuristic: {}", e.getMessage());
            return fallback;
        }
    }

    private RecommendationQueryPlan parsePlanJson(String raw) throws IOException {
        if (raw == null || raw.isBlank()) {
            return RecommendationQueryPlan.builder().build();
        }
        String json = extractFirstJsonObject(raw);
        JsonNode root = objectMapper.readTree(json);
        return RecommendationQueryPlan.builder()
                .textQuery(readText(root, "textQuery"))
                .makerCode(readText(root, "makerCode"))
                .maker(readText(root, "maker"))
                .modelCode(readText(root, "modelCode"))
                .model(readText(root, "model"))
                .bodyTypes(readStringList(root.get("bodyTypes")))
                .fuel(readText(root, "fuel"))
                .minPrice(readInt(root, "minPrice"))
                .maxPrice(readInt(root, "maxPrice"))
                .minYear(readInt(root, "minYear"))
                .maxYear(readInt(root, "maxYear"))
                .maxKm(readInt(root, "maxKm"))
                .sort(readText(root, "sort"))
                .intent(readText(root, "intent"))
                .build();
    }

    private RecommendationQueryPlan heuristicPlan(String query) {
        String lower = query.toLowerCase(Locale.ROOT);
        RecommendationQueryPlan.RecommendationQueryPlanBuilder b = RecommendationQueryPlan.builder();
        b.textQuery(query.trim());

        if (lower.contains("가족") || lower.contains("패밀리")) {
            b.intent("FAMILY");
            b.bodyTypes(List.of("SUV", "RV"));
        } else if (lower.contains("출퇴근") || lower.contains("통근")) {
            b.intent("COMMUTE");
        } else if (lower.contains("가성비") || lower.contains("저렴")) {
            b.intent("VALUE");
            b.sort("LOW_PRICE");
        }

        if (lower.contains("suv")) b.bodyTypes(List.of("SUV"));
        else if (lower.contains("rv")) b.bodyTypes(List.of("RV"));
        else if (lower.contains("경차")) b.bodyTypes(List.of("경차"));

        if (lower.contains("하이브리드")) b.fuel("하이브리드");
        else if (lower.contains("디젤")) b.fuel("디젤");
        else if (lower.contains("전기")) b.fuel("전기");
        else if (lower.contains("lpg")) b.fuel("LPG");
        else if (lower.contains("가솔린") || lower.contains("휘발유")) b.fuel("가솔린");

        Integer maxPrice = extractMaxPriceManwon(query);
        if (maxPrice != null) b.maxPrice(maxPrice);
        Integer shortMileageMaxKm = extractShortMileageMaxKm(query);
        if (shortMileageMaxKm != null) b.maxKm(shortMileageMaxKm);

        return b.build();
    }

    private RecommendationQueryPlan mergePreferLlm(RecommendationQueryPlan llm, RecommendationQueryPlan fallback) {
        if (llm == null) return fallback != null ? fallback : RecommendationQueryPlan.builder().build();
        if (fallback == null) return llm;

        return RecommendationQueryPlan.builder()
                .textQuery(firstNonBlank(llm.getTextQuery(), fallback.getTextQuery()))
                .makerCode(firstNonBlank(llm.getMakerCode(), fallback.getMakerCode()))
                .maker(firstNonBlank(llm.getMaker(), fallback.getMaker()))
                .modelCode(firstNonBlank(llm.getModelCode(), fallback.getModelCode()))
                .model(firstNonBlank(llm.getModel(), fallback.getModel()))
                .bodyTypes(mergeBodyTypes(llm, fallback))
                .fuel(firstNonBlank(llm.getFuel(), fallback.getFuel()))
                .minPrice(firstNonNull(llm.getMinPrice(), fallback.getMinPrice()))
                .maxPrice(firstNonNull(llm.getMaxPrice(), fallback.getMaxPrice()))
                .minYear(firstNonNull(llm.getMinYear(), fallback.getMinYear()))
                .maxYear(firstNonNull(llm.getMaxYear(), fallback.getMaxYear()))
                .maxKm(firstNonNull(llm.getMaxKm(), fallback.getMaxKm()))
                .sort(firstNonBlank(llm.getSort(), fallback.getSort()))
                .intent(firstNonBlank(llm.getIntent(), fallback.getIntent()))
                .build();
    }

    private static List<String> mergeBodyTypes(RecommendationQueryPlan llm, RecommendationQueryPlan fallback) {
        List<String> llmBodyTypes = normalizeBodyTypes(llm != null ? llm.getBodyTypes() : null);
        List<String> fallbackBodyTypes = normalizeBodyTypes(fallback != null ? fallback.getBodyTypes() : null);
        if (llmBodyTypes.isEmpty()) return fallbackBodyTypes;
        if (fallbackBodyTypes.isEmpty()) return llmBodyTypes;

        // 가족/패밀리 기본값(SUV+RV)이 있는데 LLM이 한쪽만 반환하면 기본값을 유지한다.
        String fallbackIntent = trimOrNull(fallback != null ? fallback.getIntent() : null);
        boolean familyFallback =
                "FAMILY".equalsIgnoreCase(fallbackIntent)
                        && fallbackBodyTypes.size() == 2
                        && fallbackBodyTypes.contains("SUV")
                        && fallbackBodyTypes.contains("RV");
        boolean llmNarrowedOne =
                llmBodyTypes.size() == 1
                        && (llmBodyTypes.contains("SUV") || llmBodyTypes.contains("RV"));
        if (familyFallback && llmNarrowedOne) return fallbackBodyTypes;
        return llmBodyTypes;
    }

    private RecommendationQueryPlan sanitize(RecommendationQueryPlan in, String originalQuery) {
        RecommendationQueryPlan.RecommendationQueryPlanBuilder b = RecommendationQueryPlan.builder();
        String textQuery = firstNonBlank(in != null ? in.getTextQuery() : null, originalQuery);
        b.textQuery(textQuery != null ? textQuery.trim() : null);
        b.makerCode(trimOrNull(in != null ? in.getMakerCode() : null));
        b.maker(trimOrNull(in != null ? in.getMaker() : null));
        b.modelCode(trimOrNull(in != null ? in.getModelCode() : null));
        b.model(trimOrNull(in != null ? in.getModel() : null));
        b.fuel(normalizeFuel(in != null ? in.getFuel() : null));
        b.sort(normalizeSort(in != null ? in.getSort() : null));
        b.intent(normalizeIntent(in != null ? in.getIntent() : null));
        b.bodyTypes(normalizeBodyTypes(in != null ? in.getBodyTypes() : null));

        Integer minPrice = clampInt(in != null ? in.getMinPrice() : null, 0, 20000);
        Integer maxPrice = clampInt(in != null ? in.getMaxPrice() : null, 0, 20000);
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

        Integer minYear = clampInt(in != null ? in.getMinYear() : null, 1990, 2030);
        Integer maxYear = clampInt(in != null ? in.getMaxYear() : null, 1990, 2030);
        if (minYear != null && maxYear != null && minYear > maxYear) {
            int tmp = minYear;
            minYear = maxYear;
            maxYear = tmp;
        }
        b.minYear(minYear);
        b.maxYear(maxYear);
        Integer maxKm = clampInt(in != null ? in.getMaxKm() : null, 0, 300000);
        Integer queryShortMileageMaxKm = extractShortMileageMaxKm(originalQuery);
        if (queryShortMileageMaxKm != null) {
            maxKm = queryShortMileageMaxKm;
        }
        b.maxKm(maxKm);
        return b.build();
    }

    private static Integer extractMaxPriceManwon(String query) {
        java.util.regex.Matcher mEok = java.util.regex.Pattern.compile("(\\d+)\\s*억").matcher(query);
        if (mEok.find()) {
            return Integer.parseInt(mEok.group(1)) * 10000;
        }
        java.util.regex.Matcher mMan = java.util.regex.Pattern.compile("(\\d{3,5})\\s*만\\s*원").matcher(query.replace(",", ""));
        if (mMan.find()) {
            return Integer.parseInt(mMan.group(1));
        }
        return null;
    }

    private static Integer[] extractPriceBandManwon(String query) {
        if (query == null || query.isBlank()) return null;
        String compact = query.replace(",", "");

        // 예) 2000만원대 -> 2000~2999
        java.util.regex.Matcher mMan = java.util.regex.Pattern.compile("(\\d{3,5})\\s*만\\s*원\\s*대").matcher(compact);
        if (mMan.find()) {
            int min = clampInt(Integer.parseInt(mMan.group(1)), 0, 20000);
            int max = clampInt(min + 999, 0, 20000);
            return new Integer[]{min, max};
        }

        // 예) 2천만원대 -> 2000~2999
        java.util.regex.Matcher mCheonMan = java.util.regex.Pattern.compile("(\\d{1,2})\\s*천\\s*만\\s*원\\s*대").matcher(compact);
        if (mCheonMan.find()) {
            int min = clampInt(Integer.parseInt(mCheonMan.group(1)) * 1000, 0, 20000);
            int max = clampInt(min + 999, 0, 20000);
            return new Integer[]{min, max};
        }

        // 예) 2억대 -> 20000~20000(클램프 상한)
        java.util.regex.Matcher mEok = java.util.regex.Pattern.compile("(\\d{1,2})\\s*억\\s*대").matcher(compact);
        if (mEok.find()) {
            int min = clampInt(Integer.parseInt(mEok.group(1)) * 10000, 0, 20000);
            int max = clampInt(min + 9999, 0, 20000);
            return new Integer[]{min, max};
        }

        return null;
    }

    private static Integer extractShortMileageMaxKm(String query) {
        if (query == null || query.isBlank()) return null;
        String lower = query.toLowerCase(Locale.ROOT);
        if (lower.contains("주행거리 짧")
                || lower.contains("짧은 주행거리")
                || lower.contains("주행거리 짧은")
                || lower.contains("저주행")
                || lower.contains("주행거리 적")
                || lower.contains("적은 주행거리")
                || lower.contains("키로수 짧")
                || lower.contains("키로수 적")) {
            return 30000;
        }
        return null;
    }

    private static String extractFirstJsonObject(String raw) {
        String s = raw.trim();
        int start = s.indexOf('{');
        if (start < 0) return s;
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) return s.substring(start, i + 1);
            }
        }
        return s.substring(start);
    }

    private static String readText(JsonNode node, String key) {
        if (node == null || !node.has(key) || node.get(key).isNull()) return null;
        String v = node.get(key).asText();
        return trimOrNull(v);
    }

    private static Integer readInt(JsonNode node, String key) {
        if (node == null || !node.has(key) || node.get(key).isNull()) return null;
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

    private static List<String> readStringList(JsonNode node) {
        if (node == null || node.isNull()) return List.of();
        if (node.isArray()) {
            List<String> out = new ArrayList<>();
            node.forEach(it -> {
                if (it != null && !it.isNull()) {
                    String s = trimOrNull(it.asText());
                    if (s != null) out.add(s);
                }
            });
            return out;
        }
        String one = trimOrNull(node.asText());
        return one != null ? List.of(one) : List.of();
    }

    private static List<String> normalizeBodyTypes(List<String> bodyTypes) {
        if (bodyTypes == null || bodyTypes.isEmpty()) return List.of();
        Map<String, String> canonical = Map.of(
                "suv", "SUV",
                "rv", "RV",
                "경형", "경차",
                "소형차", "소형"
        );
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String item : bodyTypes) {
            String t = trimOrNull(item);
            if (t == null) continue;
            String fixed = canonical.getOrDefault(t.toLowerCase(Locale.ROOT), t);
            if (ALLOWED_BODY_TYPES.contains(fixed)) out.add(fixed);
        }
        return new ArrayList<>(out);
    }

    private static String normalizeFuel(String fuel) {
        String t = trimOrNull(fuel);
        if (t == null) return null;
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.contains("휘발유") || lower.equals("gasoline")) t = "가솔린";
        if (lower.contains("하이브리드") || lower.equals("hybrid")) t = "하이브리드";
        if (lower.contains("디젤") || lower.equals("diesel")) t = "디젤";
        if (lower.contains("전기") || lower.equals("electric") || lower.equals("ev")) t = "전기";
        if (lower.equals("lpg") || lower.contains("엘피지")) t = "LPG";
        return ALLOWED_FUELS.contains(t) ? t : null;
    }

    private static String normalizeSort(String sort) {
        String s = trimOrNull(sort);
        if (s == null) return null;
        String up = s.toUpperCase(Locale.ROOT);
        return ALLOWED_SORTS.contains(up) ? up : null;
    }

    private static String normalizeIntent(String intent) {
        String s = trimOrNull(intent);
        return s != null ? s.toUpperCase(Locale.ROOT) : null;
    }

    private static Integer clampInt(Integer n, int min, int max) {
        if (n == null) return null;
        return Math.max(min, Math.min(max, n));
    }

    private static String firstNonBlank(String a, String b) {
        String aa = trimOrNull(a);
        return aa != null ? aa : trimOrNull(b);
    }

    private static Integer firstNonNull(Integer a, Integer b) {
        return a != null ? a : b;
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

package com.carizon.rag.service;

import com.carizon.rag.config.RagProperties;
import com.carizon.rag.dto.RecommendationIntent;
import com.carizon.rag.dto.RecommendationRequest;
import com.carizon.rag.dto.RecommendationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/**
 * LLM 기반 차량 추천 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarRecommendationService {
    
    private final RagSearchService ragSearchService;
    private final LlmService llmService;
    private final RagProperties ragProperties;
    private final LlmConfigService llmConfigService;
    private final RecommendationPhraseService recommendationPhraseService;
    
    /**
     * 사용자 요구사항을 기반으로 차량 추천
     */
    public RecommendationResponse recommendCars(RecommendationRequest request) throws IOException {
        long totalStart = System.currentTimeMillis();
        log.info("[recommendation] service start");
        log.info("[recommendation] request: {}", request);
        
        // 기본값 설정
        if (request.getMaxResults() == null) {
            request.setMaxResults(5);
        }
        // 명시적 메이커/모델/연료/연식 없으면 LLM이 먼저 의도 해석 → RAG 검색용 searchQuery 생성
        if (request.getQuery() != null && !request.getQuery().isBlank()) {
            String userQuery = request.getQuery().trim();
            if (isVagueQuery(userQuery)) {
                try {
                    String interpreted = interpretQueryForSearch(userQuery);
                    if (interpreted != null && !interpreted.isBlank()) {
                        request.setSearchQuery(interpreted.trim());
                        log.info("[recommendation] vague query → LLM interpreted: {}", request.getSearchQuery());
                    }
                } catch (Exception e) {
                    log.warn("[recommendation] LLM interpret failed, using original query: {}", e.getMessage());
                }
            }
            // RAG 검색·필터에 쓸 쿼리: 해석된 searchQuery 우선
            String effectiveQuery = (request.getSearchQuery() != null && !request.getSearchQuery().isBlank())
                ? request.getSearchQuery().trim() : userQuery;
            applyQueryExtractions(request, effectiveQuery);
        }
        
        // RAG 검색으로 유사한 차량 찾기
        long ragStart = System.currentTimeMillis();
        List<RecommendationResponse.RecommendedCar> cars = ragSearchService.searchSimilarCars(request);
        long ragMs = System.currentTimeMillis() - ragStart;
        log.info("[recommendation] RAG search done: {}ms, cars={}", ragMs, cars.size());
        
        if (cars.isEmpty()) {
            log.warn("[recommendation] no cars - LLM으로 안내 문구 생성 시도");
            String noCarsMessage = generateNoCarsMessage(request.getQuery());
            return RecommendationResponse.builder()
                    .recommendation(noCarsMessage)
                    .cars(List.of())
                    .build();
        }
        
        // LLM: 전반적 추천 설명만 생성. 차량별 한 줄 이유는 문구 뱅크(조건·조합)로 부여.
        long llmStart = System.currentTimeMillis();
        log.info("[LLM] recommendation description gen start");
        String recommendation = generateOverallRecommendation(request, cars);
        long llmMs = System.currentTimeMillis() - llmStart;
        log.info("[LLM] recommendation description done: {}ms", llmMs);
        
        long reasonsStart = System.currentTimeMillis();
        cars = applyPhraseReasonsToCars(cars);
        long reasonsMs = System.currentTimeMillis() - reasonsStart;
        long totalMs = System.currentTimeMillis() - totalStart;
        log.info("[recommendation] total: {}ms (RAG={}ms, LLM={}ms, reasons={}ms)", totalMs, ragMs, llmMs, reasonsMs);
        
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
    
    /**
     * 매물 0건일 때: 사용자 질의를 LLM에 넘겨 한두 문장 안내 생성. 실패/헛소리면 기본 문구 반환.
     */
    private String generateNoCarsMessage(String query) {
        String defaultMessage = "요청하신 조건에 맞는 차량을 찾을 수 없습니다. 다른 조건으로 검색해 보시거나, 가격·연식·차종 조건을 완화해 보시겠어요?";
        if (query == null || query.isBlank()) {
            return defaultMessage;
        }
        try {
            String prompt = "사용자가 중고차 추천을 요청했는데, 조건에 맞는 매물이 한 대도 없습니다.\n\n"
                + "사용자 검색어: \"" + query.trim() + "\"\n\n"
                + "위 검색어를 반영해서, 한두 문장으로 친절히 안내해 주세요. (예: 조건을 완화해 보시거나, 다른 키워드로 검색해 보시라고 권유). 한국어로만 답하고, 100자 이내로 짧게.";
            String response = llmService.generateResponse(prompt);
            if (response != null && !response.isBlank()) {
                String trimmed = response.trim();
                if (trimmed.length() >= 10 && trimmed.length() <= 500) {
                    return trimmed;
                }
            }
        } catch (Exception e) {
            log.warn("[recommendation] no-cars LLM failed, using default: {}", e.getMessage());
        }
        return defaultMessage;
    }
    
    /**
     * LLM 1회 호출: 사용자 검색에 대한 전반적 추천 사유(2~4문장)만 생성.
     * 차량별 한 줄 이유는 RecommendationPhraseService(문구 뱅크)에서 조건·조합으로 부여.
     */
    private String generateOverallRecommendation(RecommendationRequest request,
                                                 List<RecommendationResponse.RecommendedCar> cars) throws IOException {
        String carListTitle = llmConfigService.getPrompt("car-list-title");
        String carFormat = llmConfigService.getPrompt("car-format");
        String defaultRecommendation = llmConfigService.getPrompt("default-recommendation");
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("당신은 이미 선정된 추천 차량 목록에 대해 '전반적인 추천 사유'만 작성합니다. 차량을 고르거나 추가하지 마세요.\n\n");
        prompt.append("사용자 요청: ").append(request.getQuery()).append("\n\n");
        prompt.append(carListTitle).append("\n");
        for (int i = 0; i < cars.size(); i++) {
            RecommendationResponse.RecommendedCar car = cars.get(i);
            String carInfo = carFormat
                .replace("${index}", String.valueOf(i + 1))
                .replace("${maker}", car.getMaker() != null ? car.getMaker() : "")
                .replace("${model}", car.getModel() != null ? car.getModel() : "")
                .replace("${trim}", car.getTrim() != null ? car.getTrim() : "")
                .replace("${year}", car.getYear() != null ? String.valueOf(car.getYear()) : "")
                .replace("${mileage}", car.getMileage() != null ? String.format("%,d", car.getMileage()) : "")
                .replace("${price}", car.getPrice() != null ? String.format("%,d", car.getPrice()) : "");
            prompt.append(carInfo).append("\n");
        }
        prompt.append("\n[출력]\n");
        prompt.append("사용자 검색에 대한 '전반적인 추천 사유'만 2~4문장으로 작성하세요. ");
        prompt.append("개별 차량을 하나씩 나열하지 말고, 왜 이런 추천 결과가 나왔는지 전체적인 답변만 적으세요. ");
        prompt.append("한국어로만, 목록 밖 정보나 새 사실을 만들지 마세요.\n");
        
        try {
            String raw = llmService.generateResponse(prompt.toString());
            if (raw != null && !raw.isBlank()) {
                String trimmed = raw.replace("\r\n", "\n").trim();
                if (!trimmed.isEmpty()) return trimmed;
            }
        } catch (Exception e) {
            log.warn("Failed to generate LLM recommendation", e);
        }
        return defaultRecommendation;
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
    
    /** 명시적 메이커/모델/연료/연식 언급이 없으면 true → LLM 해석 단계 진행 */
    private static boolean isVagueQuery(String query) {
        if (query == null || query.isBlank()) return false;
        String lower = query.toLowerCase();
        // 메이커/브랜드
        if (lower.contains("볼보") || lower.contains("volvo") || lower.contains("현대") || lower.contains("기아") || lower.contains("제네시스") || lower.contains("genesis") || lower.contains("벤츠") || lower.contains("bmw") || lower.contains("아우디") || lower.contains("쉐보레") || lower.contains("테슬라")) return false;
        // 모델명 (일부)
        if (lower.contains("xc60") || lower.contains("xc90") || lower.contains("gv80") || lower.contains("g80") || lower.contains("g70") || lower.contains("쏘나타") || lower.contains("그랜저") || lower.contains("펠리세이드") || lower.contains("카니발") || lower.contains("스포티지")) return false;
        // 연료
        if (lower.contains("가솔린") || lower.contains("디젤") || lower.contains("하이브리드") || lower.contains("전기") || lower.contains("lpg") || lower.contains("휘발유")) return false;
        // 연식
        if (java.util.regex.Pattern.compile("\\d{2,4}\\s*년(식)?").matcher(query).find()) return false;
        if (lower.contains("22년") || lower.contains("23년") || lower.contains("24년") || lower.contains("최신") || lower.contains("신형") || lower.contains("구형") || lower.contains("오래된 연식")) return false;
        return true;
    }

    /** LLM으로 사용자 말을 'RAG 검색용 한 줄 키워드'로 해석 (7명 가족 → 7인승 미니밴 SUV 등) */
    private String interpretQueryForSearch(String userQuery) throws IOException {
        String promptTemplate = llmConfigService.getPrompt("interpret-search-query");
        if (promptTemplate == null || promptTemplate.isBlank()) {
            promptTemplate = "사용자가 중고차 추천을 요청했습니다. 아래 요청을 '중고차 검색에 쓸 한 줄 키워드'로만 바꿔주세요.\n"
                + "예: 7명 가족 큰차 필요해 → 7인승 미니밴 SUV 대형 가족용. 메이커·모델·연료·연식을 사용자가 안 말했으면 추론해서 보충하되, 검색어만 한 줄로 출력하세요. 다른 설명 없이 검색어 한 줄만 한국어로.\n\n사용자 요청:\n${query}";
        }
        String prompt = promptTemplate.contains("${query}") ? promptTemplate.replace("${query}", userQuery) : promptTemplate + "\n\n사용자 요청:\n" + userQuery;
        String raw = llmService.generateResponse(prompt);
        if (raw == null || raw.isBlank()) return null;
        String line = raw.lines().findFirst().orElse(raw).trim();
        return line.length() > 500 ? line.substring(0, 500) : line;
    }

    /** 쿼리에서 추천 의도 감지 (프리셋 가중치용) */
    private static RecommendationIntent detectIntent(String query) {
        if (query == null || query.isBlank()) return RecommendationIntent.GENERAL;
        String lower = query.toLowerCase();
        if (lower.contains("가성비") || lower.contains("연식 오래") || lower.contains("오래된") || lower.contains("구형") || lower.contains("가격 대비")) return RecommendationIntent.VALUE;
        if (lower.contains("안전") || lower.contains("신생아") || lower.contains("아기") || lower.contains("가족") || lower.contains("패밀리") || lower.contains("아이")) return RecommendationIntent.SAFETY;
        if (lower.contains("데이트") || lower.contains("연인") || lower.contains("20대") || lower.contains("연비") || lower.contains("유지비")) return RecommendationIntent.DATE;
        if (lower.contains("패밀리") || lower.contains("가족") || lower.contains("캠핑") || lower.contains("공간")) return RecommendationIntent.FAMILY;
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
    
    /** 차종 필터 - 메타데이터 bodyTypeCategory와 동일한 값 사용 (소형, 경차, SUV, 세단, 미니밴, 해치백, 왜건) */
    private static String extractBodyTypeFilter(String query) {
        if (query == null || query.isBlank()) return null;
        String lower = query.toLowerCase();
        if (lower.contains("경차") || lower.contains("케이카")) return "경차";
        if (lower.contains("소형차") || lower.contains("소형")) return "소형";
        if (lower.contains("suv") || lower.contains("에스유비") || lower.contains("스포츠유틸리티")) return "SUV";
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
}

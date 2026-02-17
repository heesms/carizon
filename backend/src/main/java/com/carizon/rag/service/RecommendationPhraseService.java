package com.carizon.rag.service;

import com.carizon.rag.dto.RecommendationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 추천 평가 문구: 3줄(PRICE/CONDITION/MARKET/USABILITY + OVERALL) + 특히 한 줄(연차/주행/색상만, 연료·변속 제외).
 * 문구 조합만 사용하며, LLM(Ollama) 다듬기는 사용하지 않음.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationPhraseService {

    private static final int FRESH_YEAR = 2022;
    private static final int AVERAGE_YEAR_MIN = 2018;
    private static final int LOW_MILEAGE_MAX = 50_000;
    private static final int HIGH_MILEAGE_MIN = 100_000;
    private static final int MILEAGE_BAND_SIZE = 10_000;
    private static final int MILEAGE_BAND_COUNT = 10;
    private static final int MAX_YEAR_AGE = 20;

    private static final List<String> NON_OVERALL_CATEGORIES = Arrays.asList("PRICE", "CONDITION", "MARKET", "USABILITY");

    /** 두 번째 줄(보조 문장) 접두어: 첫 줄과 자연스럽게 이어지도록 */ 
    private static final List<String> SECOND_LINE_PREFIXES = Arrays.asList(
            "또한 ", "게다가 ", "아울러 ", "더해서 ", "무엇보다 ", "특히 ", "이와 함께 "
    );

    /** 세 번째 줄(OVERALL) 접두어: 종합·요약 느낌 */ 
    private static final List<String> THIRD_LINE_PREFIXES = Arrays.asList(
            "전체적으로 보면 ", "종합적으로 보면 ", "결과적으로 ", "종합하면 ", "요약하면 ", "한마디로 말해 "
    );

    /** 마지막 강조 문장 접두어 (여러 가지로 돌려가며 사용) */
    private static final List<String> EMPHASIS_PREFIXES = Arrays.asList(
            "특히 ", "마지막으로 ", "강조할 점은 ", "한편 ", "덧붙이면 ", "요약하면 ", "한마디로 ",
            "추가로 ", "또한 ", "그리고 ", "참고로 ", "요약하자면 ", "한 가지 더 ", "마지막으로 강조하면 "
    );

    private final RecommendationPhraseBank phraseBank;
    private final LlmConfigService llmConfigService;

    /**
     * 차량에 맞는 추천 평가 문구 반환 (3줄 + 특히 한 줄).
     * ①~② 두 카테고리 문구 + ③ OVERALL + ④ 특히 (연차/주행/색상 기반, 연료·변속 제외).
     */
    public String getReasonForCar(RecommendationResponse.RecommendedCar car) {
        Map<String, List<String>> cat = phraseBank.getCategoryPhrases();
        List<String> overallList = cat.get("OVERALL");
        if (overallList == null || overallList.isEmpty()) {
            return llmConfigService.getPrompt("default-message");
        }

        List<String> lines = new ArrayList<>();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        // 2개 카테고리 선택 (중복 없이)
        List<String> pool = new ArrayList<>(NON_OVERALL_CATEGORIES);
        Collections.shuffle(pool);
        for (int i = 0; i < 2 && i < pool.size(); i++) {
            String category = pool.get(i);
            List<String> list = cat.get(category);
            if (list != null && !list.isEmpty()) {
                lines.add(list.get(rnd.nextInt(list.size())));
            }
        }
        if (lines.size() < 2) {
            // 카테고리 문구가 하나 이하이면 OVERALL만 사용 (최소 한 줄 보장)
            lines.clear();
            lines.add(overallList.get(rnd.nextInt(overallList.size())));
        } else {
            // 3번째 줄은 OVERALL
            lines.add(overallList.get(rnd.nextInt(overallList.size())));
        }

        // 문장들이 너무 '툭툭' 끊어지지 않도록 2·3번째 줄에 접속사/접두어를 붙여 자연스럽게 연결
        if (lines.size() >= 2) {
            String original = lines.get(1);
            String prefix = SECOND_LINE_PREFIXES.get(rnd.nextInt(SECOND_LINE_PREFIXES.size()));
            lines.set(1, prefix + original);
        }
        if (lines.size() >= 3) {
            String original = lines.get(2);
            String prefix = THIRD_LINE_PREFIXES.get(rnd.nextInt(THIRD_LINE_PREFIXES.size()));
            lines.set(2, prefix + original);
        }

        // 마지막 강조 문장: 문구가 있을 때만 넣고, 접두어는 여러 가지 중 랜덤. 가끔은 3줄만 (강조 생략)
        String emphasis = getEmphasisPhrase(car);
        if (emphasis != null && !emphasis.isBlank() && rnd.nextInt(100) < 80) {
            String prefix = EMPHASIS_PREFIXES.get(rnd.nextInt(EMPHASIS_PREFIXES.size()));
            lines.add(prefix + emphasis.trim());
        }

        String raw = String.join("\n", lines);
        return raw;
    }

    /** 특히 한 줄: 연차·주행·색상만 사용 (연료·변속 제외), 기존 태그 문구에서 선택 */
    private String getEmphasisPhrase(RecommendationResponse.RecommendedCar car) {
        Set<String> carTags = getTagsForCarEmphasisOnly(car);
        List<RecommendationPhraseBank.PhraseEntry> all = phraseBank.getPhrases();
        if (all.isEmpty()) return "";

        List<RecommendationPhraseBank.PhraseEntry> matching = all.stream()
                .filter(p -> carTags.containsAll(p.getTags()))
                .sorted((a, b) -> Integer.compare(b.getTags().size(), a.getTags().size()))
                .collect(Collectors.toList());

        if (matching.isEmpty()) {
            matching = all.stream()
                    .filter(p -> p.getTags().size() == 1 && p.getTags().contains("DEFAULT"))
                    .collect(Collectors.toList());
        }
        if (matching.isEmpty()) return "";

        int maxSize = matching.get(0).getTags().size();
        List<RecommendationPhraseBank.PhraseEntry> best = matching.stream()
                .filter(p -> p.getTags().size() == maxSize)
                .collect(Collectors.toList());
        return best.get(ThreadLocalRandom.current().nextInt(best.size())).getText();
    }

    /**
     * 연식/주행/가격/색상만으로 추천 문구 생성 (블로그·API 공통, 3줄+특히).
     * 연료·변속은 호출부에서 넘겨도 특히 문구 선택 시에는 사용하지 않음.
     */
    public String getReason(Integer year,
                            Integer mileage,
                            Integer price,
                            String fuel,
                            String transmission,
                            String color) {
        RecommendationResponse.RecommendedCar car = RecommendationResponse.RecommendedCar.builder()
                .year(year)
                .mileage(mileage)
                .price(price)
                .fuel(fuel)
                .transmission(transmission)
                .color(color)
                .build();
        return getReasonForCar(car);
    }

    /** 특히 문구용: 연차·만 단위 주행·색상·가격만 (연료·변속 제외) */
    private Set<String> getTagsForCarEmphasisOnly(RecommendationResponse.RecommendedCar car) {
        Set<String> tags = new HashSet<>();
        int currentYear = Year.now().getValue();

        Integer year = car.getYear();
        if (year != null) {
            if (year >= FRESH_YEAR) tags.add("FRESH_YEAR");
            else if (year >= AVERAGE_YEAR_MIN) tags.add("AVERAGE_YEAR");
            else tags.add("OLD_YEAR");
            int yearAge = currentYear - year;
            if (yearAge >= 1 && yearAge <= MAX_YEAR_AGE) tags.add("YEAR_" + yearAge);
            else if (yearAge > MAX_YEAR_AGE) tags.add("YEAR_20_PLUS");
        }

        Integer mileage = car.getMileage();
        if (mileage != null) {
            if (mileage <= LOW_MILEAGE_MAX) tags.add("LOW_MILEAGE");
            else if (mileage < HIGH_MILEAGE_MIN) tags.add("AVERAGE_MILEAGE");
            else tags.add("HIGH_MILEAGE");
            String bandTag = mileageBandTag(mileage);
            if (bandTag != null) tags.add(bandTag);
        }

        addColorTags(car.getColor(), tags);
        if (car.getPrice() != null && car.getPrice() > 0) tags.add("REASONABLE_PRICE");
        if (tags.isEmpty()) tags.add("DEFAULT");
        return tags;
    }

    /** 주행거리 → 만 단위 구간 태그 (MILEAGE_0_10K, MILEAGE_10K_20K, ... MILEAGE_100K_PLUS) */
    private String mileageBandTag(int mileage) {
        if (mileage < 0) return null;
        if (mileage >= MILEAGE_BAND_SIZE * MILEAGE_BAND_COUNT) return "MILEAGE_100K_PLUS";
        int band = mileage / MILEAGE_BAND_SIZE;
        int low = band * MILEAGE_BAND_SIZE;
        int high = low + MILEAGE_BAND_SIZE;
        return "MILEAGE_" + (low / 1000) + "K_" + (high / 1000) + "K";
    }

    /** car_master color 값 기준 색상 태그 (POPULAR_COLOR + COLOR_*) */
    private void addColorTags(String color, Set<String> tags) {
        if (color == null || color.isBlank()) return;
        String n = color.trim();
        if (n.isEmpty()) return;
        // 인기 색상: 검정, 흰색, 회색, 은색, 진주
        if (containsAny(n, "검정", "흰색", "흰 ", "회색", "쥐색", "은회색", "은색", "명은색", "은하색", "진주색", "진주")) {
            tags.add("POPULAR_COLOR");
        }
        if (containsAny(n, "검정")) { tags.add("COLOR_BLACK"); return; }
        if (containsAny(n, "흰색")) { tags.add("COLOR_WHITE"); return; }
        if (containsAny(n, "회색", "쥐색", "은회색")) { tags.add("COLOR_GRAY"); return; }
        if (containsAny(n, "은색", "명은색", "은하색")) { tags.add("COLOR_SILVER"); return; }
        if (containsAny(n, "진주색", "진주")) { tags.add("COLOR_PEARL"); return; }
        if (containsAny(n, "파랑", "청색", "청옥색", "하늘색", "하늘", "파란색")) { tags.add("COLOR_BLUE"); return; }
        if (containsAny(n, "빨간색", "빨강색", "빨강")) { tags.add("COLOR_RED"); return; }
        if (containsAny(n, "녹색", "초록색", "연두색", "담녹색", "갈대색")) { tags.add("COLOR_GREEN"); return; }
        if (containsAny(n, "갈색", "연금색", "금색", "미색", "노랑", "주황", "보라색", "분홍색", "자주색", "남색")) {
            tags.add("COLOR_OTHER");
            return;
        }
        if (n.equalsIgnoreCase("인기색상")) {
            tags.add("POPULAR_COLOR");
            return;
        }
        tags.add("COLOR_OTHER");
    }

    private static boolean containsAny(String text, String... keywords) {
        String lower = text.toLowerCase();
        for (String k : keywords) {
            if (lower.contains(k.toLowerCase())) return true;
        }
        return false;
    }
}

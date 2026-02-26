package com.carizon.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 조건·조합별 추천 문구 + 카테고리 문구(PRICE/CONDITION/MARKET/USABILITY/OVERALL) 보관.
 * 태그 문구: "태그1,태그2|문구" → 특히 한 줄용. 카테고리: "PRICE|문구" → 3줄 조합용.
 */
@Slf4j
@Component
public class RecommendationPhraseBank {

    private static final String PHRASE_FILE = "rag/recommendation-phrases.txt";
    private static final String CATEGORY_FILE = "rag/recommendation-phrases-categories.txt";

    /** 태그 집합 → 해당 문구들 (특히 한 줄용, 연차/주행/색상만 사용) */
    private final List<PhraseEntry> phrases = new ArrayList<>();

    /** 카테고리별 문구 (3줄 조합용) */
    private final Map<String, List<String>> categoryPhrases = new HashMap<>();

    @PostConstruct
    public void init() {
        loadTagPhrases();
        loadCategoryPhrases();
    }

    private void loadTagPhrases() {
        try {
            ClassPathResource resource = new ClassPathResource(PHRASE_FILE);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int pipe = line.indexOf('|');
                    if (pipe <= 0) continue;
                    String tagPart = line.substring(0, pipe).trim();
                    String text = line.substring(pipe + 1).trim();
                    if (text.isEmpty()) continue;
                    Set<String> tags = Arrays.stream(tagPart.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toCollection(HashSet::new));
                    phrases.add(new PhraseEntry(tags, text));
                }
            }
            log.info("[phrase bank] loaded {} tag phrases from {}", phrases.size(), PHRASE_FILE);
        } catch (Exception e) {
            log.warn("[phrase bank] failed to load {}: {}", PHRASE_FILE, e.getMessage());
        }
    }

    private void loadCategoryPhrases() {
        List<String> categories = Arrays.asList("PRICE", "CONDITION", "MARKET", "USABILITY", "OVERALL");
        for (String cat : categories) {
            categoryPhrases.put(cat, new ArrayList<>());
        }
        try {
            ClassPathResource resource = new ClassPathResource(CATEGORY_FILE);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int pipe = line.indexOf('|');
                    if (pipe <= 0) continue;
                    String cat = line.substring(0, pipe).trim().toUpperCase();
                    String text = line.substring(pipe + 1).trim();
                    if (text.isEmpty() || !categoryPhrases.containsKey(cat)) continue;
                    categoryPhrases.get(cat).add(text);
                }
            }
            int total = categoryPhrases.values().stream().mapToInt(List::size).sum();
            log.info("[phrase bank] loaded {} category phrases from {}", total, CATEGORY_FILE);
        } catch (Exception e) {
            log.warn("[phrase bank] failed to load {}: {}", CATEGORY_FILE, e.getMessage());
        }
    }

    public List<PhraseEntry> getPhrases() {
        return Collections.unmodifiableList(phrases);
    }

    public Map<String, List<String>> getCategoryPhrases() {
        return Collections.unmodifiableMap(categoryPhrases);
    }

    @lombok.Value
    public static class PhraseEntry {
        Set<String> tags;
        String text;
    }
}

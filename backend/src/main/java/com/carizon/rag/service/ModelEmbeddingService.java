package com.carizon.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 모델코드별 임베딩: cz_model_embedding_source 테이블 → 모델 전용 Chroma 컬렉션.
 * AI 추천 시 모델 설명 검색에 활용.
 */
@Slf4j
@Service
public class ModelEmbeddingService {

    private final JdbcTemplate jdbc;
    private final EmbeddingService embeddingService;
    private final ChromaVectorStoreService chromaVectorStoreService;

    public ModelEmbeddingService(JdbcTemplate jdbc,
                                 EmbeddingService embeddingService,
                                 ChromaVectorStoreService chromaVectorStoreService) {
        this.jdbc = jdbc;
        this.embeddingService = embeddingService;
        this.chromaVectorStoreService = chromaVectorStoreService;
    }

    /**
     * 단일 모델코드: DB에서 3개 컬럼 조회 → 하나의 문서로 합쳐 임베딩 후 모델 컬렉션에 저장.
     */
    public void embedByModelCode(String modelCode) throws IOException {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT model_code, model_name, embed_text_1, embed_text_2, embed_text_3 " +
                        "FROM cz_model_embedding_source WHERE model_code = ?",
                modelCode);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("모델코드 없음: " + modelCode);
        }
        Map<String, Object> row = rows.get(0);
        String document = buildDocument(
                nullableString(row.get("embed_text_1")),
                nullableString(row.get("embed_text_2")),
                nullableString(row.get("embed_text_3")));
        if (document.isBlank()) {
            log.warn("[model embedding] model_code={} has no embed text, skip", modelCode);
            return;
        }
        float[] embedding = embeddingService.generateEmbedding(document);
        String modelName = nullableString(row.get("model_name"));
        chromaVectorStoreService.addModelEmbedding(
                modelCode,
                document,
                embedding,
                modelName != null && !modelName.isBlank() ? Map.of("modelName", modelName) : Map.of());
        log.info("[model embedding] model_code={} embedded", modelCode);
    }

    /**
     * 전체: cz_model_embedding_source 전건 조회 후 모델 컬렉션에 임베딩 저장.
     */
    public int embedAllFromSource() throws IOException {
        long start = System.currentTimeMillis();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT model_code, model_name, embed_text_1, embed_text_2, embed_text_3 " +
                        "FROM cz_model_embedding_source");
        int totalRows = rows.size();
        log.info("[model embedding] start totalRows={}", totalRows);
        int count = 0;
        int processed = 0;
        int progressUnit = Math.max(100, totalRows / 20); // 약 5% 단위
        for (Map<String, Object> row : rows) {
            processed++;
            String modelCode = nullableString(row.get("model_code"));
            if (modelCode == null || modelCode.isBlank()) continue;
            String document = buildDocument(
                    nullableString(row.get("embed_text_1")),
                    nullableString(row.get("embed_text_2")),
                    nullableString(row.get("embed_text_3")));
            if (document.isBlank()) {
                log.debug("[model embedding] skip model_code={} (no text)", modelCode);
                continue;
            }
            try {
                float[] embedding = embeddingService.generateEmbedding(document);
                String modelName = nullableString(row.get("model_name"));
                chromaVectorStoreService.addModelEmbedding(
                        modelCode,
                        document,
                        embedding,
                        modelName != null && !modelName.isBlank() ? Map.of("modelName", modelName) : Map.of());
                count++;
            } catch (Exception e) {
                log.warn("[model embedding] failed model_code={}: {}", modelCode, e.getMessage());
            }
            if (processed % progressUnit == 0 || processed == totalRows) {
                int percent = totalRows == 0 ? 100 : (int) Math.round(processed * 100.0 / totalRows);
                log.info("[model embedding] progress {}/{} ({}%) embedded={}", processed, totalRows, percent, count);
            }
        }
        log.info("[model embedding] batch done, embedded {} models (elapsedMs={})",
                count, System.currentTimeMillis() - start);
        return count;
    }

    private static String buildDocument(String t1, String t2, String t3) {
        return Stream.of(t1, t2, t3)
                .filter(s -> s != null && !s.isBlank())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private static String nullableString(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }
}

package com.carizon.rag.service;

import com.carizon.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 앱 기동 시 rag.model-embedding-source-file 경로의 TSV를 읽어
 * cz_model_embedding_source에 INSERT (ON DUPLICATE KEY UPDATE).
 * DDD.txt 형식: model_code \t model_name \t 임베딩용 요약텍스트 \t 임베딩용 요약 텍스트2 \t 임베딩용 요약 텍스트3
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelEmbeddingSourceLoader {

    private final RagProperties ragProperties;
    private final ResourceLoader resourceLoader;
    private final JdbcTemplate jdbc;

    private static final String INSERT_SQL =
            "INSERT INTO cz_model_embedding_source (model_code, model_name, embed_text_1, embed_text_2, embed_text_3) " +
                    "VALUES (?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "model_name = VALUES(model_name), " +
                    "embed_text_1 = VALUES(embed_text_1), " +
                    "embed_text_2 = VALUES(embed_text_2), " +
                    "embed_text_3 = VALUES(embed_text_3), " +
                    "updated_at = CURRENT_TIMESTAMP";

    /**
     * 설정된 파일 경로가 있으면 해당 TSV를 파싱해 테이블에 반영. 없거나 읽기 실패 시 스킵.
     */
    public int loadIfConfigured() {
        String path = ragProperties.getModelEmbeddingSourceFile();
        if (path == null || path.isBlank()) {
            log.debug("[model-embedding-source] no file path configured, skip load");
            return 0;
        }
        Resource resource = resourceLoader.getResource(path);
        if (!resource.exists()) {
            log.warn("[model-embedding-source] file not found: {}", path);
            return 0;
        }
        return loadFromResource(resource);
    }

    /**
     * 리소스(파일)에서 TSV 읽어 INSERT. 첫 줄은 헤더로 스킵.
     */
    public int loadFromResource(Resource resource) {
        List<Object[]> batch = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) {
                    first = false;
                    if (line.startsWith("model_code") || line.trim().isEmpty()) continue; // 헤더 스킵
                }
                Object[] row = parseTsvLine(line);
                if (row != null) batch.add(row);
            }
        } catch (Exception e) {
            log.error("[model-embedding-source] read failed: {}", resource, e);
            return 0;
        }
        if (batch.isEmpty()) {
            log.info("[model-embedding-source] no data rows in {}", resource);
            return 0;
        }
        try {
            jdbc.batchUpdate(INSERT_SQL, batch);
            log.info("[model-embedding-source] loaded {} rows from {}", batch.size(), resource);
            return batch.size();
        } catch (Exception e) {
            log.error("[model-embedding-source] insert failed", e);
            return 0;
        }
    }

    /** 한 줄을 \t 기준 5컬럼으로 파싱. 5개 미만이면 null(스킵). */
    private static Object[] parseTsvLine(String line) {
        if (line == null || line.isBlank()) return null;
        String[] parts = line.split("\t", 5);
        if (parts.length < 3) return null;
        String modelCode = parts[0].trim();
        if (modelCode.isEmpty()) return null;
        String modelName = parts.length > 1 ? parts[1].trim() : "";
        String t1 = parts.length > 2 ? nullToEmpty(parts[2]) : "";
        String t2 = parts.length > 3 ? nullToEmpty(parts[3]) : "";
        String t3 = parts.length > 4 ? nullToEmpty(parts[4]) : "";
        return new Object[]{modelCode, modelName, t1, t2, t3};
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}

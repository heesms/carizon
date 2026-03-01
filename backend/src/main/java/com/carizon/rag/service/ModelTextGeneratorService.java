package com.carizon.rag.service;

import com.carizon.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * cz_model_embedding_source의 #정보확인필요 플레이스홀더 행에 대해
 * Ollama LLM으로 모델 설명 텍스트를 자동 생성 후 DB 업데이트.
 * 로컬에서 강력한 모델(gemma3:27b 등)을 사용하도록 application-local.yaml의
 * rag.llm.model-text-gen-model 로 오버라이드.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelTextGeneratorService {

    private static final String PLACEHOLDER = "#정보확인필요";
    private static final int DEFAULT_TIMEOUT_MS = 120_000; // 대형 모델은 응답이 느릴 수 있음

    private final JdbcTemplate jdbc;
    private final LlmService llmService;
    private final ModelEmbeddingService modelEmbeddingService;
    private final RagProperties ragProperties;

    /**
     * 플레이스홀더 행을 LLM으로 채운 뒤 DB 업데이트.
     * dryRun=true이면 DB 변경 없이 생성 텍스트 미리보기만 반환.
     * reembed=true이면 DB 갱신 후 해당 모델을 Chroma에 재임베딩.
     *
     * @return 처리 결과 요약 맵
     */
    public Map<String, Object> generateAndSave(int limit, boolean dryRun, boolean reembed) {
        List<Map<String, Object>> rows = queryPlaceholderRows(limit);
        if (rows.isEmpty()) {
            return Map.of("message", "처리할 #정보확인필요 행이 없습니다.", "processed", 0, "failed", 0);
        }

        String modelOverride = ragProperties.getLlm().getModelTextGenModel();
        String baseUrlOverride = ragProperties.getLlm().getModelTextGenBaseUrl();
        String effectiveModel = modelOverride.isBlank()
                ? ragProperties.getLlm().getOllama().getModel() : modelOverride;
        String effectiveBaseUrl = baseUrlOverride.isBlank()
                ? ragProperties.getLlm().getOllama().getBaseUrl() : baseUrlOverride;

        log.info("[model-text-gen] start rows={} limit={} dryRun={} reembed={} model={} baseUrl={}",
                rows.size(), limit, dryRun, reembed, effectiveModel, effectiveBaseUrl);

        LlmService.GenerationOptions opts = new LlmService.GenerationOptions(
                600,
                0.3,
                DEFAULT_TIMEOUT_MS,
                "You are a Korean car data assistant. Output only the requested pipe-separated structured text. No greetings, no explanations.",
                modelOverride.isBlank() ? null : modelOverride,
                baseUrlOverride.isBlank() ? null : baseUrlOverride
        );

        int processed = 0, failed = 0, reembedCount = 0;
        List<Map<String, Object>> preview = new ArrayList<>();
        long start = System.currentTimeMillis();
        int total = rows.size();

        for (Map<String, Object> row : rows) {
            String modelCode = str(row.get("model_code"));
            String modelName = str(row.get("model_name"));
            if (modelCode == null) continue;

            try {
                String prompt = buildPrompt(modelName != null ? modelName : modelCode);
                String generated = llmService.generateResponse(prompt, opts);
                String cleanedText = extractStructuredLine(generated);

                if (cleanedText.isBlank()) {
                    log.warn("[model-text-gen] empty/unstructured result model_code={} model_name={}", modelCode, modelName);
                    failed++;
                    continue;
                }

                if (dryRun) {
                    if (preview.size() < 5) {
                        preview.add(Map.of(
                                "modelCode", modelCode,
                                "modelName", modelName != null ? modelName : "",
                                "generated", cleanedText));
                    }
                } else {
                    jdbc.update(
                            "UPDATE cz_model_embedding_source " +
                            "SET embed_text_1 = ?, embed_text_2 = ?, embed_text_3 = ?, updated_at = CURRENT_TIMESTAMP " +
                            "WHERE model_code = ?",
                            modelName != null ? modelName : modelCode,
                            cleanedText,
                            "",
                            modelCode);
                    if (reembed) {
                        try {
                            modelEmbeddingService.embedByModelCode(modelCode);
                            reembedCount++;
                        } catch (Exception e) {
                            log.warn("[model-text-gen] re-embed failed model_code={}: {}", modelCode, e.getMessage());
                        }
                    }
                }
                processed++;

            } catch (Exception e) {
                log.warn("[model-text-gen] failed model_code={}: {}", modelCode, e.getMessage());
                failed++;
            }

            if ((processed + failed) % 10 == 0 || (processed + failed) == total) {
                int pct = total == 0 ? 100 : (int) Math.round((processed + failed) * 100.0 / total);
                log.info("[model-text-gen] progress {}/{} ({}%) processed={} failed={} reembed={}",
                        processed + failed, total, pct, processed, failed, reembedCount);
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRows", total);
        result.put("processed", processed);
        result.put("failed", failed);
        result.put("reembedded", reembedCount);
        result.put("dryRun", dryRun);
        result.put("elapsedMs", elapsed);
        result.put("model", effectiveModel);
        if (dryRun && !preview.isEmpty()) {
            result.put("preview", preview);
        }
        log.info("[model-text-gen] done processed={} failed={} reembed={} elapsedMs={}", processed, failed, reembedCount, elapsed);
        return result;
    }

    /** 남아있는 플레이스홀더 행 수 반환 */
    public long countPlaceholders() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM cz_model_embedding_source " +
                "WHERE embed_text_1 LIKE ? OR embed_text_1 IS NULL OR embed_text_1 = ''",
                Long.class, "%" + PLACEHOLDER + "%");
        return count != null ? count : 0L;
    }

    private List<Map<String, Object>> queryPlaceholderRows(int limit) {
        return jdbc.queryForList(
                "SELECT model_code, model_name FROM cz_model_embedding_source " +
                "WHERE embed_text_1 LIKE ? OR embed_text_1 IS NULL OR embed_text_1 = '' " +
                "ORDER BY model_code LIMIT ?",
                "%" + PLACEHOLDER + "%", limit);
    }

    private static String buildPrompt(String modelName) {
        return """
아래 중고차 모델에 대해 임베딩용 설명 텍스트를 파이프(|) 구분으로 작성하세요.
한국어로만 작성하고, 형식 외 다른 내용은 절대 포함하지 마세요.

차량 모델: %s

출력 형식 (한 줄, 파이프 구분):
제조사={제조사} | 모델={모델명} | 구분={국산/수입} | 원산지={국가명} | 바디={바디타입} | 차급={차급} | 연료={주연료} | 타겟={주타겟연령대성별} | 스타일={감성키워드들} | 용도={주용도들} | 장점={장점1,장점2} | 한줄={차량 특성 한 문장}

스타일 예시: 럭셔리, 스포티, 간지나는, 실용적, 패밀리, 감성적, 클래식, 젊은감성
타겟 예시: 20대여성, 20대남성, 30대직장인, 40대가족, 전연령
용도 예시: 출퇴근, 도심주행, 장거리, 드라이빙취향, 가족여행, 취미/세컨카
""".formatted(modelName);
    }

    /**
     * LLM 응답에서 파이프(|) 구분 key=value 형식의 줄을 추출.
     * 여러 줄일 경우 가장 내용이 많은 줄 선택.
     */
    private static String extractStructuredLine(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String best = "";
        for (String line : raw.split("\n")) {
            String t = line.trim();
            if (t.contains("|") && t.contains("=") && t.length() > best.length()) {
                best = t;
            }
        }
        if (!best.isBlank()) return best;
        // 구조화된 줄이 없으면 가장 긴 non-empty 줄 반환
        String fallback = "";
        for (String line : raw.split("\n")) {
            String t = line.trim();
            if (t.length() > fallback.length()) fallback = t;
        }
        return fallback.length() > 10 ? fallback : "";
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }
}

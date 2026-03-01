package com.carizon.admin;

import com.carizon.batch.ApiRunRecorder;
import com.carizon.common.dto.ApiResponse;
import com.carizon.rag.config.RagProperties;
import com.carizon.rag.service.ChromaVectorStoreService;
import com.carizon.rag.service.ModelEmbeddingService;
import com.carizon.rag.service.ModelTextGeneratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 임베딩 배치 작업 관리 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/admin/embedding")
@RequiredArgsConstructor
@Tag(name = "임베딩 관리", description = "차량 데이터 임베딩 배치 작업 관리")
public class EmbeddingAdminController {
    
    private final com.carizon.rag.service.CarEmbeddingBatchService embeddingBatchService;
    private final ChromaVectorStoreService vectorStoreService;
    private final RagProperties ragProperties;
    private final ModelEmbeddingService modelEmbeddingService;
    private final ModelTextGeneratorService modelTextGeneratorService;
    private final ApiRunRecorder apiRunRecorder;
    private final JdbcTemplate jdbc;
    
    @PostMapping("/all")
    @Operation(summary = "전체 차량 임베딩", description = "DB 기준 ONSALE/ADVERTISE 차량을 Chroma에 임베딩 저장. 기존 데이터는 덮어쓰기(같은 car_id 업서트). 삭제/판매된 차량 ID는 Chroma에 남을 수 있음. '전체 날리고 다시'는 POST /admin/embedding/reset-and-all 사용.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> embedAllCars() {
        String runId = apiRunRecorder.recordStart("/admin/embedding/all", "POST", "embedding-all");
        try {
            int count = embeddingBatchService.embedAllCars();
            Map<String, Object> result = Map.of(
                    "message", "Embedding completed",
                    "embeddedCount", count
            );
            apiRunRecorder.recordSuccess(runId, count, result);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("[embedding] all failed", e);
            apiRunRecorder.recordFail(runId, 0, e);
            return ResponseEntity.ok(ApiResponse.error("실패: " + e.getMessage()));
        }
    }

    @PostMapping("/reset-and-all")
    @Operation(summary = "Chroma 전체 삭제 후 전체 재임베딩", description = "1) Chroma 컬렉션(car_listings) 삭제 2) DB 기준 전체 차량 임베딩. 10만 건은 Ollama 속도에 따라 수 시간 걸릴 수 있음.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resetAndEmbedAll() {
        String runId = apiRunRecorder.recordStart("/admin/embedding/reset-and-all", "POST", "embedding-reset-and-all");
        try {
            vectorStoreService.deleteCollection();
            int count = embeddingBatchService.embedAllCars();
            Map<String, Object> result = Map.of(
                    "message", "Reset and embedding completed",
                    "embeddedCount", count
            );
            apiRunRecorder.recordSuccess(runId, count, result);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("[embedding] reset-and-all failed", e);
            apiRunRecorder.recordFail(runId, 0, e);
            return ResponseEntity.ok(ApiResponse.error("실패: " + e.getMessage()));
        }
    }
    
    @PostMapping("/car/{carId}")
    @Operation(summary = "단일 차량 임베딩", description = "특정 차량을 벡터 DB에 임베딩으로 저장")
    public ResponseEntity<ApiResponse<String>> embedCar(@PathVariable Long carId) {
        String runId = apiRunRecorder.recordStart("/admin/embedding/car/" + carId, "POST", "embedding-car");
        try {
            embeddingBatchService.embedCar(carId);
            apiRunRecorder.recordSuccess(runId, 1, Map.of("carId", carId));
            return ResponseEntity.ok(ApiResponse.success("Car embedded: " + carId));
        } catch (Exception e) {
            log.error("[embedding] car {} failed", carId, e);
            apiRunRecorder.recordFail(runId, 0, e);
            return ResponseEntity.ok(ApiResponse.error("단일 임베딩 실패: " + e.getMessage()));
        }
    }
    
    @PostMapping("/range")
    @Operation(summary = "범위 차량 임베딩", description = "특정 범위의 차량만 임베딩")
    public ResponseEntity<ApiResponse<Map<String, Object>>> embedCarsInRange(
            @RequestParam Long fromCarId,
            @RequestParam Long toCarId) {
        String runId = apiRunRecorder.recordStart("/admin/embedding/range", "POST", "embedding-range");
        try {
            int count = embeddingBatchService.embedCarsInRange(fromCarId, toCarId);
            Map<String, Object> result = Map.of(
                    "message", "Embedding completed for range: " + fromCarId + " - " + toCarId,
                    "embeddedCount", count,
                    "fromCarId", fromCarId,
                    "toCarId", toCarId
            );
            apiRunRecorder.recordSuccess(runId, count, result);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("[embedding] range failed from={} to={}", fromCarId, toCarId, e);
            apiRunRecorder.recordFail(runId, 0, e);
            return ResponseEntity.ok(ApiResponse.error("범위 임베딩 실패: " + e.getMessage()));
        }
    }

    @PostMapping("/filter")
    @Operation(summary = "조건별 제한 임베딩", description = "제조사(메이커)·개수로 필터링해 해당 차량만 Chroma에 임베딩. 예: 볼보 100개 → maker=볼보&limit=100 또는 makerCode=VOLVO&limit=100")
    public ResponseEntity<ApiResponse<Map<String, Object>>> embedByFilter(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String makerCode,
            @RequestParam(required = false) String maker) {
        String runId = apiRunRecorder.recordStart("/admin/embedding/filter", "POST", "embedding-filter");
        try {
            int embeddedCount = embeddingBatchService.embedByFilter(limit, makerCode, maker);
            Map<String, Object> result = Map.of(
                    "message", "조건별 임베딩 완료",
                    "embeddedCount", embeddedCount,
                    "limit", limit,
                    "makerCode", makerCode != null ? makerCode : "",
                    "maker", maker != null ? maker : ""
            );
            apiRunRecorder.recordSuccess(runId, embeddedCount, result);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("[embedding] filter failed", e);
            apiRunRecorder.recordFail(runId, 0, e);
            return ResponseEntity.ok(ApiResponse.error("조건별 임베딩 실패: " + e.getMessage()));
        }
    }

    @PostMapping("/incremental")
    @Operation(summary = "증분 임베딩", description = "지정된 시간 이후에 업데이트된 차량만 임베딩합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> incrementalEmbed(
            @RequestParam(required = false) String since) {
        String runId = apiRunRecorder.recordStart("/admin/embedding/incremental", "POST", "embedding-incremental");
        try {
            java.time.LocalDateTime sinceTime = since != null && !since.isEmpty() 
                ? java.time.LocalDateTime.parse(since) 
                : java.time.LocalDateTime.now().minusHours(1); // 기본값: 1시간 전
            
            int count = embeddingBatchService.incrementalEmbed(sinceTime);
            Map<String, Object> result = Map.of(
                "message", "증분 임베딩 완료",
                "embeddedCount", count,
                "since", sinceTime.toString()
            );
            apiRunRecorder.recordSuccess(runId, count, result);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("[embedding] incremental embedding failed", e);
            apiRunRecorder.recordFail(runId, 0, e);
            return ResponseEntity.ok(ApiResponse.error("증분 임베딩 실패: " + e.getMessage()));
        }
    }

    @GetMapping("/status")
    @Operation(summary = "임베딩 상태 조회", description = "Chroma에 저장된 임베딩 개수 및 컬렉션 정보 조회")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEmbeddingStatus() {
        try {
            Map<String, Object> status = new HashMap<>();

            // 진행 중인 임베딩 작업 상태 (전체/증분/필터 배치 실행 시 갱신됨)
            status.put("progress", embeddingBatchService.getProgress());
            
            // 컬렉션 정보 및 개수 (getCollectionCount 사용 - 더 안정적)
            try {
                long count = vectorStoreService.getCollectionCount();
                String collectionName = ragProperties.getChroma().getCollectionName();
                
                Map<String, Object> collection = new HashMap<>();
                collection.put("name", collectionName);
                collection.put("count", count);
                status.put("collection", collection);
            } catch (Exception e) {
                log.debug("Chroma collection fetch failed (ignorable): {}", e.getMessage());
                Map<String, Object> collection = new HashMap<>();
                collection.put("name", ragProperties.getChroma().getCollectionName());
                collection.put("count", 0);
                status.put("collection", collection);
            }
            
            // 샘플 데이터 (메타데이터 확인용) - 실패해도 무시
            try {
                List<Map<String, Object>> samples = vectorStoreService.getSampleEmbeddings(5);
                status.put("samples", samples);
            } catch (Exception e) {
                log.debug("Sample data fetch failed (ignorable): {}", e.getMessage());
                status.put("samples", List.of());
            }

            List<Map<String, Object>> recentApiRuns = jdbc.queryForList("""
                SELECT id, run_id, source, status, total_items, started_at, ended_at, duration_ms, message
                FROM api_run
                WHERE source IN (
                    'embedding-all', 'embedding-reset-and-all', 'embedding-car',
                    'embedding-range', 'embedding-filter', 'embedding-incremental', 'embedding-metadata-search',
                    'embedding-model', 'embedding-model-all',
                    'embedding_incremental', 'embedding_all'
                )
                   OR source LIKE 'embedding_car_%'
                ORDER BY started_at DESC
                LIMIT 20
                """);
            status.put("recentApiRuns", recentApiRuns);
            status.put("latestApiRun", recentApiRuns.isEmpty() ? null : recentApiRuns.get(0));
            
            return ResponseEntity.ok(ApiResponse.success(status));
        } catch (Exception e) {
            log.error("[embedding status] fetch failed", e);
            return ResponseEntity.ok(ApiResponse.error("상태 조회 실패: " + e.getMessage()));
        }
    }

    @GetMapping("/samples")
    @Operation(summary = "샘플 임베딩 조회", description = "저장된 임베딩 샘플 조회 (메타데이터 확인용)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getSampleEmbeddings(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            List<Map<String, Object>> samples = vectorStoreService.getSampleEmbeddings(limit);
            return ResponseEntity.ok(ApiResponse.success(samples));
        } catch (Exception e) {
            log.error("[sample] fetch failed", e);
            return ResponseEntity.ok(ApiResponse.error("샘플 조회 실패: " + e.getMessage()));
        }
    }

    // ----- 모델 전용 컬렉션 (cz_model_embedding_source → model_descriptions, AI 추천 활용)
    @PostMapping("/model/{modelCode}")
    @Operation(summary = "모델코드별 임베딩 1건", description = "cz_model_embedding_source에서 해당 model_code의 3개 텍스트 컬럼을 합쳐 모델 전용 Chroma 컬렉션에 임베딩 저장.")
    public ResponseEntity<ApiResponse<String>> embedModel(@PathVariable String modelCode) {
        String runId = apiRunRecorder.recordStart("/admin/embedding/model/" + modelCode, "POST", "embedding-model");
        try {
            modelEmbeddingService.embedByModelCode(modelCode);
            apiRunRecorder.recordSuccess(runId, 1, Map.of("modelCode", modelCode));
            return ResponseEntity.ok(ApiResponse.success("모델 임베딩 완료: " + modelCode));
        } catch (IllegalArgumentException e) {
            apiRunRecorder.recordFail(runId, 0, e.getMessage());
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("[embedding] model {} failed", modelCode, e);
            apiRunRecorder.recordFail(runId, 0, e);
            return ResponseEntity.ok(ApiResponse.error("모델 임베딩 실패: " + e.getMessage()));
        }
    }

    @PostMapping("/model/all")
    @Operation(summary = "모델 임베딩 전체", description = "cz_model_embedding_source 전건을 읽어 모델 전용 Chroma 컬렉션에 임베딩 저장. DDD.txt 데이터 적재 후 실행.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> embedAllModels() {
        String runId = apiRunRecorder.recordStart("/admin/embedding/model/all", "POST", "embedding-model-all");
        try {
            int count = modelEmbeddingService.embedAllFromSource();
            Map<String, Object> result = Map.of(
                    "message", "모델 임베딩 전체 완료",
                    "embeddedCount", count
            );
            apiRunRecorder.recordSuccess(runId, count, result);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("[embedding] model all failed", e);
            apiRunRecorder.recordFail(runId, 0, e);
            return ResponseEntity.ok(ApiResponse.error("모델 전체 임베딩 실패: " + e.getMessage()));
        }
    }

    @PostMapping("/models/generate-texts")
    @Operation(summary = "모델 설명 텍스트 LLM 자동생성",
            description = "cz_model_embedding_source에서 #정보확인필요 플레이스홀더 행을 로컬 강력 Ollama 모델로 채웁니다. " +
                    "사용 모델은 application-local.yaml의 rag.llm.model-text-gen-model 로 설정. " +
                    "dryRun=true면 DB 변경 없이 미리보기만 반환. reembed=true면 생성 후 Chroma 재임베딩도 수행.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateModelTexts(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestParam(defaultValue = "false") boolean reembed) {
        String runId = apiRunRecorder.recordStart("/admin/embedding/models/generate-texts", "POST", "embedding-model-text-gen");
        try {
            long remaining = modelTextGeneratorService.countPlaceholders();
            Map<String, Object> result = new LinkedHashMap<>(modelTextGeneratorService.generateAndSave(limit, dryRun, reembed));
            result.put("remainingPlaceholders", remaining);
            apiRunRecorder.recordSuccess(runId, ((Number) result.getOrDefault("processed", 0)).intValue(), result);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("[embedding] models/generate-texts failed", e);
            apiRunRecorder.recordFail(runId, 0, e);
            return ResponseEntity.ok(ApiResponse.error("모델 텍스트 생성 실패: " + e.getMessage()));
        }
    }

    @GetMapping("/models/text-stats")
    @Operation(summary = "모델 텍스트 현황", description = "cz_model_embedding_source 플레이스홀더/완성 행 수 조회")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getModelTextStats() {
        try {
            long total = jdbc.queryForObject("SELECT COUNT(*) FROM cz_model_embedding_source", Long.class);
            long placeholders = modelTextGeneratorService.countPlaceholders();
            long completed = total - placeholders;
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                    "total", total,
                    "completed", completed,
                    "placeholders", placeholders,
                    "completionRate", total == 0 ? 0 : Math.round(completed * 100.0 / total) + "%"
            )));
        } catch (Exception e) {
            log.error("[embedding] models/text-stats failed", e);
            return ResponseEntity.ok(ApiResponse.error("현황 조회 실패: " + e.getMessage()));
        }
    }

    @GetMapping("/list")
    @Operation(summary = "RAG 임베딩 목록 조회", description = "Chroma에 들어가 있는 차량 목록. maker, model, modelGroup으로 필터 가능.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listEmbeddedCars(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String maker,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String modelGroup,
            @RequestParam(required = false) String platformName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String bodyType,
            @RequestParam(required = false) Long carId) {
        try {
            List<Map<String, Object>> list = vectorStoreService.listEmbeddings(
                    limit, maker, model, modelGroup, platformName, status, bodyType, carId
            );
            long totalInCollection = vectorStoreService.getCollectionCount();
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                    "totalInCollection", totalInCollection,
                    "returned", list.size(),
                    "makerFilter", maker != null ? maker : "",
                    "modelFilter", model != null ? model : "",
                    "modelGroupFilter", modelGroup != null ? modelGroup : "",
                    "platformNameFilter", platformName != null ? platformName : "",
                    "statusFilter", status != null ? status : "",
                    "bodyTypeFilter", bodyType != null ? bodyType : "",
                    "carIdFilter", carId != null ? carId : "",
                    "items", list
            )));
        } catch (Exception e) {
            log.error("[embedding list] failed", e);
            return ResponseEntity.ok(ApiResponse.error("목록 조회 실패: " + e.getMessage()));
        }
    }

    @PostMapping("/metadata-search")
    @Operation(summary = "메타데이터 where 검색", description = "Chroma where JSON을 그대로 전달해 임베딩 메타데이터를 직접 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> metadataSearch(
            @RequestBody(required = false) Map<String, Object> body) {
        String runId = apiRunRecorder.recordStart("/admin/embedding/metadata-search", "POST", "embedding-metadata-search");
        try {
            int limit = 100;
            Map<String, Object> where = null;
            if (body != null) {
                Object l = body.get("limit");
                if (l instanceof Number n) {
                    limit = n.intValue();
                } else if (l instanceof String s && !s.isBlank()) {
                    limit = Integer.parseInt(s);
                }
                Object w = body.get("where");
                if (w instanceof Map<?, ?> wm) {
                    Map<String, Object> whereMap = new LinkedHashMap<>();
                    wm.forEach((k, v) -> whereMap.put(String.valueOf(k), v));
                    where = whereMap;
                }
            }

            List<Map<String, Object>> list = vectorStoreService.searchEmbeddingsByMetadata(where, limit);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("returned", list.size());
            result.put("limit", limit);
            result.put("where", where != null ? where : Map.of());
            result.put("items", list);
            apiRunRecorder.recordSuccess(runId, list.size(), Map.of("returned", list.size(), "limit", limit));
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("[embedding metadata-search] failed", e);
            apiRunRecorder.recordFail(runId, 0, e);
            return ResponseEntity.ok(ApiResponse.error("메타데이터 where 검색 실패: " + e.getMessage()));
        }
    }
}

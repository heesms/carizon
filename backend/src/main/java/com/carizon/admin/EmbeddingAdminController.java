package com.carizon.admin;

import com.carizon.common.dto.ApiResponse;
import com.carizon.rag.config.RagProperties;
import com.carizon.rag.service.CarEmbeddingBatchService;
import com.carizon.rag.service.ChromaVectorStoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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
    
    private final CarEmbeddingBatchService embeddingBatchService;
    private final ChromaVectorStoreService vectorStoreService;
    private final RagProperties ragProperties;
    
    @PostMapping("/all")
    @Operation(summary = "전체 차량 임베딩", description = "모든 차량 데이터를 벡터 DB에 임베딩으로 저장. 배치 단위로 Chroma에 저장되므로 중간에 실패해도 이미 저장된 것은 유지됩니다.")
    public ResponseEntity<ApiResponse<String>> embedAllCars() throws Exception {
        embeddingBatchService.embedAllCars();
        return ResponseEntity.ok(ApiResponse.success("Embedding completed"));
    }
    
    @PostMapping("/car/{carId}")
    @Operation(summary = "단일 차량 임베딩", description = "특정 차량을 벡터 DB에 임베딩으로 저장")
    public ResponseEntity<ApiResponse<String>> embedCar(@PathVariable Long carId) throws Exception {
        embeddingBatchService.embedCar(carId);
        return ResponseEntity.ok(ApiResponse.success("Car embedded: " + carId));
    }
    
    @PostMapping("/range")
    @Operation(summary = "범위 차량 임베딩", description = "특정 범위의 차량만 임베딩")
    public ResponseEntity<ApiResponse<String>> embedCarsInRange(
            @RequestParam Long fromCarId,
            @RequestParam Long toCarId) throws Exception {
        embeddingBatchService.embedCarsInRange(fromCarId, toCarId);
        return ResponseEntity.ok(ApiResponse.success(
                "Embedding completed for range: " + fromCarId + " - " + toCarId));
    }

    @PostMapping("/incremental")
    @Operation(summary = "증분 임베딩", description = "지정된 시간 이후에 업데이트된 차량만 임베딩합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> incrementalEmbed(
            @RequestParam(required = false) String since) {
        try {
            java.time.LocalDateTime sinceTime = since != null && !since.isEmpty() 
                ? java.time.LocalDateTime.parse(since) 
                : java.time.LocalDateTime.now().minusHours(1); // 기본값: 1시간 전
            
            int count = embeddingBatchService.incrementalEmbed(sinceTime);
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                "message", "증분 임베딩 완료",
                "embeddedCount", count,
                "since", sinceTime.toString()
            )));
        } catch (Exception e) {
            log.error("[임베딩] 증분 임베딩 실패", e);
            return ResponseEntity.ok(ApiResponse.error("증분 임베딩 실패: " + e.getMessage()));
        }
    }

    @GetMapping("/status")
    @Operation(summary = "임베딩 상태 조회", description = "Chroma에 저장된 임베딩 개수 및 컬렉션 정보 조회")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEmbeddingStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            
            // 컬렉션 정보 및 개수 (getCollectionCount 사용 - 더 안정적)
            try {
                long count = vectorStoreService.getCollectionCount();
                String collectionName = ragProperties.getChroma().getCollectionName();
                
                Map<String, Object> collection = new HashMap<>();
                collection.put("name", collectionName);
                collection.put("count", count);
                status.put("collection", collection);
            } catch (Exception e) {
                log.debug("Chroma 컬렉션 정보 조회 실패 (무시 가능): {}", e.getMessage());
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
                log.debug("샘플 데이터 조회 실패 (무시 가능): {}", e.getMessage());
                status.put("samples", List.of());
            }
            
            return ResponseEntity.ok(ApiResponse.success(status));
        } catch (Exception e) {
            log.error("[임베딩 상태 조회] 실패", e);
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
            log.error("[샘플 조회] 실패", e);
            return ResponseEntity.ok(ApiResponse.error("샘플 조회 실패: " + e.getMessage()));
        }
    }
}

package com.carizon.admin;

import com.carizon.common.dto.ApiResponse;
import com.carizon.rag.service.CarEmbeddingBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    
    @PostMapping("/all")
    @Operation(summary = "전체 차량 임베딩", description = "모든 차량 데이터를 벡터 DB에 임베딩으로 저장")
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
}

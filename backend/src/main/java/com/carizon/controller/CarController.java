package com.carizon.controller;

import com.carizon.common.dto.ApiResponse;
import com.carizon.service.CarQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "차량 조회", description = "차량 목록 및 상세 조회 API")
public class  CCarController {
    
  private final CarQueryService service;
  
  @GetMapping("/cars")
    @Operation(summary = "차량 목록 조회", description = "검색 조건에 맞는 차량 목록을 페이징하여 조회")
    public ApiResponse<Map<String, Object>> list(@RequestParam Map<String, Object> q) {
        log.info("[car search] request start: params={}", q);
        try {
            long startTime = System.currentTimeMillis();
            Map<String, Object> result = service.search(q);
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[car search] request done: {}ms, count={}", 
                elapsed, 
                result.get("totalElements"));
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[car search] error: params={}", q, e);
            throw e; // GlobalExceptionHandler가 처리
        }
    }
    
  @GetMapping("/cars/{carId}")
    @Operation(summary = "차량 상세 조회", description = "차량 ID로 상세 정보 조회")
    public ApiResponse<Map<String, Object>> detail(@PathVariable long carId) {
        long apiStart = System.currentTimeMillis();
        log.info("[car detail] request start: carId={}", carId);
        try {
            Map<String, Object> result = service.detail(carId);
            long apiMs = System.currentTimeMillis() - apiStart;
            log.info("[car detail] request done: carId={}, total={}ms", carId, apiMs);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[car detail] error: carId={}, after {}ms", carId, System.currentTimeMillis() - apiStart, e);
            throw e;
        }
    }

  @GetMapping("/cars/{carId}/price-history")
    @Operation(summary = "차량 가격 이력 조회", description = "차량 ID 기준 가격 변동 이력 조회")
    public ApiResponse<Map<String, Object>> priceHistory(@PathVariable long carId) {
        long apiStart = System.currentTimeMillis();
        log.info("[car price-history] request start: carId={}", carId);
        try {
            Map<String, Object> result = service.priceHistory(carId);
            long apiMs = System.currentTimeMillis() - apiStart;
            log.info("[car price-history] request done: carId={}, total={}ms", carId, apiMs);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[car price-history] error: carId={}, after {}ms", carId, System.currentTimeMillis() - apiStart, e);
            throw e;
        }
    }
}

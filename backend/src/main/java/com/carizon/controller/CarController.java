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
public class CarController {
    
  private final CarQueryService service;
  
  @GetMapping("/cars")
    @Operation(summary = "차량 목록 조회", description = "검색 조건에 맞는 차량 목록을 페이징하여 조회")
    public ApiResponse<Map<String, Object>> list(@RequestParam Map<String, Object> q) {
        log.info("[차량검색] 요청 시작: 파라미터={}", q);
        try {
            long startTime = System.currentTimeMillis();
            Map<String, Object> result = service.search(q);
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[차량검색] 요청 완료: 소요시간={}ms, 결과수={}", 
                elapsed, 
                result.get("totalElements"));
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[차량검색] 오류 발생: 파라미터={}", q, e);
            throw e; // GlobalExceptionHandler가 처리
        }
    }
    
  @GetMapping("/cars/{carId}")
    @Operation(summary = "차량 상세 조회", description = "차량 ID로 상세 정보 조회")
    public ApiResponse<Map<String, Object>> detail(@PathVariable long carId) {
        log.info("[차량상세] 요청: carId={}", carId);
        try {
            Map<String, Object> result = service.detail(carId);
            log.info("[차량상세] 요청 완료: carId={}", carId);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[차량상세] 오류 발생: carId={}", carId, e);
            throw e;
        }
    }
}
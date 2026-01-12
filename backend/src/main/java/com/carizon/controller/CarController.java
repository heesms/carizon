package com.carizon.controller;

import com.carizon.common.dto.ApiResponse;
import com.carizon.service.CarQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "차량 조회", description = "차량 목록 및 상세 조회 API")
public class CarController {
    
    private final CarQueryService service;
    
    @GetMapping("/cars")
    @Operation(summary = "차량 목록 조회", description = "검색 조건에 맞는 차량 목록을 페이징하여 조회")
    public ApiResponse<Map<String, Object>> list(@RequestParam Map<String, Object> q) {
        return ApiResponse.success(service.search(q));
    }
    
    @GetMapping("/cars/{carId}")
    @Operation(summary = "차량 상세 조회", description = "차량 ID로 상세 정보 조회")
    public ApiResponse<Map<String, Object>> detail(@PathVariable long carId) {
        return ApiResponse.success(service.detail(carId));
    }
}
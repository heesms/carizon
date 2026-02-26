package com.carizon.controller;

import com.carizon.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {
    
  @GetMapping("/api/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.success(Map.of("ok", true, "service", "carizon-api"));
    }
}
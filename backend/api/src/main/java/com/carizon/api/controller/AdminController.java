package com.carizon.api.controller;

import com.carizon.api.dto.ApiResponse;
import com.carizon.core.service.AdminService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/ingest/status")
    public ApiResponse<Map<String, Object>> ingestStatus() {
        return ApiResponse.of(adminService.getIngestStatus());
    }

    @PostMapping("/ingest/run")
    public ApiResponse<Void> ingestRun(@RequestParam String platform) {
        adminService.triggerIngest(platform);
        return ApiResponse.of(null, Map.of("status", "ACCEPTED"));
    }

    @GetMapping("/mapping/failures")
    public ApiResponse<List<Map<String, Object>>> mappingFailures() {
        return ApiResponse.of(adminService.getMappingFailures());
    }

    @PostMapping("/mapping/resolve")
    public ApiResponse<Map<String, Object>> resolveMapping(@RequestBody Map<String, Object> body) {
        return ApiResponse.of(adminService.resolveMapping(body));
    }

    @GetMapping("/qc/anomalies")
    public ApiResponse<List<Map<String, Object>>> qcAnomalies() {
        return ApiResponse.of(adminService.getQcAnomalies());
    }
}

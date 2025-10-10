package com.carizon.core.service.impl;

import com.carizon.core.service.AdminService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AdminServiceImpl implements AdminService {
    @Override
    public Map<String, Object> getIngestStatus() {
        return Map.of("status", "IDLE");
    }

    @Override
    public void triggerIngest(String platform) {
        // stub
    }

    @Override
    public List<Map<String, Object>> getMappingFailures() {
        return List.of();
    }

    @Override
    public Map<String, Object> resolveMapping(Map<String, Object> payload) {
        return Map.of("resolved", true);
    }

    @Override
    public List<Map<String, Object>> getQcAnomalies() {
        return List.of();
    }
}

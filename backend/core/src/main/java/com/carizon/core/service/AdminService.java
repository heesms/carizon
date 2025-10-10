package com.carizon.core.service;

import java.util.List;
import java.util.Map;

public interface AdminService {
    Map<String, Object> getIngestStatus();
    void triggerIngest(String platform);
    List<Map<String, Object>> getMappingFailures();
    Map<String, Object> resolveMapping(Map<String, Object> payload);
    List<Map<String, Object>> getQcAnomalies();
}

package com.carizon.search.service;

import com.carizon.domain.mapper.CarMapper;
import com.carizon.search.service.ElasticsearchCarSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 차량 데이터를 Elasticsearch에 인덱싱하는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CarIndexingService {

    private final CarMapper mapper;
    private final ElasticsearchCarSearchService elasticsearchCarSearchService;

    @Value("${search.indexing.reindex-batch-size:5000}")
    private int reindexBatchSize;

    @Value("${search.indexing.incremental-batch-size:3000}")
    private int incrementalBatchSize;

    @Value("${search.indexing.batch-max-size:3000}")
    private int batchMaxSize;

    public ElasticsearchCarSearchService getCarSearchService() {
        return elasticsearchCarSearchService;
    }

    /**
     * 전체 차량 데이터를 Elasticsearch에 재인덱싱 (기존 인덱스 삭제 후 전체 재생성)
     */
    public int reindexAllCars() {
        log.info("[CarIndexingService] full reindex start");
        long totalStart = System.currentTimeMillis();

        try {
            elasticsearchCarSearchService.deleteAllDocuments();

            int batchSize = Math.max(500, reindexBatchSize);
            Long lastCarId = null;
            int totalIndexed = 0;

            while (true) {
                Map<String, Object> params = new HashMap<>();
                params.put("limit", batchSize);
                params.put("lastCarId", lastCarId);

                long dbStart = System.currentTimeMillis();
                List<Map<String, Object>> cars = mapper.selectCarsForIndexingAfterId(params);
                long dbMs = System.currentTimeMillis() - dbStart;

                if (cars == null || cars.isEmpty()) {
                    break;
                }

                List<Map<String, Object>> indexData = normalizeIndexData(cars);

                long esStart = System.currentTimeMillis();
                elasticsearchCarSearchService.indexCars(indexData);
                long esMs = System.currentTimeMillis() - esStart;

                totalIndexed += indexData.size();
                log.info("[CarIndexingService] reindex progress: {} done (DB {}ms, Elasticsearch {}ms)", totalIndexed, dbMs, esMs);

                if (cars.size() < batchSize) {
                    break;
                }
                Object lastId = cars.get(cars.size() - 1).get("carId");
                lastCarId = (lastId instanceof Number) ? ((Number) lastId).longValue() : null;
                if (lastCarId == null) break;
            }

            long totalMs = System.currentTimeMillis() - totalStart;
            log.info("[CarIndexingService] full reindex done: {} total ({}ms, batchSize={})", totalIndexed, totalMs, batchSize);
            return totalIndexed;
        } catch (Exception e) {
            log.error("[CarIndexingService] full reindex failed", e);
            throw new RuntimeException("차량 재인덱싱 실패", e);
        }
    }

    /**
     * 증분 인덱싱: 업데이트된 차량만 인덱싱 (업데이트 시간 기준)
     * @param since 이 시간 이후에 업데이트된 차량만 인덱싱
     */
    public int incrementalIndex(LocalDateTime since) {
        log.info("[CarIndexingService] incremental index start: since={}", since);

        try {
            int batchSize = Math.max(500, incrementalBatchSize);
            Long lastCarId = null;
            int totalIndexed = 0;

            while (true) {
                Map<String, Object> params = new HashMap<>();
                params.put("limit", batchSize);
                params.put("lastCarId", lastCarId);
                params.put("updatedSince", since);

                long dbStart = System.currentTimeMillis();
                List<Map<String, Object>> cars = mapper.selectCarsForIndexingUpdated(params);
                long dbMs = System.currentTimeMillis() - dbStart;

                if (cars == null || cars.isEmpty()) {
                    break;
                }

                List<Map<String, Object>> indexData = normalizeIndexData(cars);

                long esStart = System.currentTimeMillis();
                elasticsearchCarSearchService.indexCars(indexData);
                long esMs = System.currentTimeMillis() - esStart;

                totalIndexed += indexData.size();
                log.info("[CarIndexingService] incremental index progress: {} done (DB {}ms, ES {}ms)", totalIndexed, dbMs, esMs);

                if (cars.size() < batchSize) {
                    break;
                }
                Object lastId = cars.get(cars.size() - 1).get("carId");
                lastCarId = (lastId instanceof Number) ? ((Number) lastId).longValue() : null;
                if (lastCarId == null) break;
            }

            log.info("[CarIndexingService] incremental index done: {} total", totalIndexed);
            return totalIndexed;
        } catch (Exception e) {
            log.error("[CarIndexingService] incremental index failed", e);
            throw new RuntimeException("증분 인덱싱 실패", e);
        }
    }

    /**
     * 배치 인덱싱: 특정 개수만큼만 인덱싱 (스케줄러용)
     * @param limit 인덱싱할 최대 개수
     * @return 실제 인덱싱된 개수
     */
    public int batchIndex(int limit) {
        log.info("[CarIndexingService] batch index start: limit={}", limit);

        try {
            int batchSize = Math.min(Math.max(500, batchMaxSize), limit);
            Long lastCarId = null;
            int totalIndexed = 0;
            int remaining = limit;

            while (remaining > 0) {
                int currentBatchSize = Math.min(batchSize, remaining);

                Map<String, Object> params = new HashMap<>();
                params.put("limit", currentBatchSize);
                params.put("lastCarId", lastCarId);

                long dbStart = System.currentTimeMillis();
                List<Map<String, Object>> cars = mapper.selectCarsForIndexing(params);
                long dbMs = System.currentTimeMillis() - dbStart;

                if (cars == null || cars.isEmpty()) {
                    break;
                }

                List<Map<String, Object>> indexData = normalizeIndexData(cars);

                long esStart = System.currentTimeMillis();
                elasticsearchCarSearchService.indexCars(indexData);
                long esMs = System.currentTimeMillis() - esStart;

                int indexed = indexData.size();
                totalIndexed += indexed;
                remaining -= indexed;

                log.info("[CarIndexingService] batch index progress: {} done, remaining: {} (DB {}ms, ES {}ms)",
                    totalIndexed, Math.max(0, remaining), dbMs, esMs);

                if (cars.size() < currentBatchSize) {
                    break;
                }
                Object lastId = cars.get(cars.size() - 1).get("carId");
                lastCarId = (lastId instanceof Number) ? ((Number) lastId).longValue() : null;
                if (lastCarId == null) break;
            }

            log.info("[CarIndexingService] batch index done: {} total", totalIndexed);
            return totalIndexed;
        } catch (Exception e) {
            log.error("[CarIndexingService] batch index failed", e);
            throw new RuntimeException("배치 인덱싱 실패", e);
        }
    }

    /**
     * 특정 차량만 인덱싱 (단일 차량 업데이트)
     */
    public void indexCar(long carId) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("carId", carId);
            params.put("limit", 1);
            params.put("offset", 0);
            
            List<Map<String, Object>> cars = mapper.selectCarsForIndexingById(params);
            if (cars != null && !cars.isEmpty()) {
                List<Map<String, Object>> indexData = normalizeIndexData(cars);
                if (!indexData.isEmpty()) {
                    elasticsearchCarSearchService.indexCar(indexData.get(0));
                    log.debug("[CarIndexingService] car index done: carId={}", carId);
                }
            }
        } catch (Exception e) {
            log.error("[CarIndexingService] car index failed: carId={}", carId, e);
        }
    }

    /**
     * 다건 차량 인덱싱 (Kafka 배치 처리용)
     */
    public void indexCars(List<Long> carIds) {
        if (carIds == null || carIds.isEmpty()) return;
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("carIds", carIds);
            List<Map<String, Object>> cars = mapper.selectCarsForIndexingByIds(params);
            if (cars != null && !cars.isEmpty()) {
                List<Map<String, Object>> indexData = normalizeIndexData(cars);
                if (!indexData.isEmpty()) {
                    elasticsearchCarSearchService.indexCars(indexData);
                    log.debug("[CarIndexingService] batch car index done: {} cars", indexData.size());
                }
            }
        } catch (Exception e) {
            log.error("[CarIndexingService] batch car index failed: carIds={}", carIds, e);
        }
    }

    /**
     * 인덱싱 데이터 정규화 (필드명 및 타입 변환)
     */
    private List<Map<String, Object>> normalizeIndexData(List<Map<String, Object>> cars) {
        List<Map<String, Object>> indexData = new ArrayList<>();
        
        for (Map<String, Object> car : cars) {
            try {
                Map<String, Object> doc = new HashMap<>(car);
                
                // LocalDateTime을 문자열로 변환
                if (doc.containsKey("priceUpdatedAt") && doc.get("priceUpdatedAt") != null) {
                    Object dateTime = doc.get("priceUpdatedAt");
                    if (dateTime instanceof java.time.LocalDateTime) {
                        doc.put("priceUpdatedAt", ((java.time.LocalDateTime) dateTime).toString());
                    } else if (dateTime instanceof java.sql.Timestamp) {
                        doc.put("priceUpdatedAt", ((java.sql.Timestamp) dateTime).toLocalDateTime().toString());
                    }
                }
                
                // null 값 정리 (Meilisearch에서 null은 제거하는 것이 좋음)
                doc.entrySet().removeIf(entry -> entry.getValue() == null && 
                    !entry.getKey().equals("carId")); // carId는 필수
                
                indexData.add(doc);
            } catch (Exception e) {
                log.warn("[CarIndexingService] data normalize failed: {}", e.getMessage());
            }
        }
        
        return indexData;
    }

}

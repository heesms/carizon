package com.carizon.search.service;

import com.carizon.domain.mapper.CarMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 차량 데이터를 Meilisearch에 인덱싱하는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CarIndexingService {

    private final CarMapper mapper;
    private final MeilisearchService meilisearchService;

    public MeilisearchService getMeilisearchService() {
        return meilisearchService;
    }

    /**
     * 전체 차량 데이터를 Meilisearch에 재인덱싱 (기존 인덱스 삭제 후 전체 재생성)
     * 페이징 처리로 메모리 효율적으로 처리
     */
    public void reindexAllCars() {
        log.info("[CarIndexingService] 전체 차량 재인덱싱 시작");
        
        try {
            // 기존 인덱스 삭제
            meilisearchService.reindexAll(Collections.emptyList());
            
            int batchSize = 1000;
            int offset = 0;
            int totalIndexed = 0;
            
            while (true) {
                Map<String, Object> params = new HashMap<>();
                params.put("limit", batchSize);
                params.put("offset", offset);
                
                // 인덱싱 전용 쿼리 사용
                List<Map<String, Object>> cars = mapper.selectCarsForIndexing(params);
                if (cars == null || cars.isEmpty()) {
                    break;
                }
                
                // 데이터 정규화 및 인덱싱
                List<Map<String, Object>> indexData = normalizeIndexData(cars);
                meilisearchService.indexCars(indexData);
                
                totalIndexed += indexData.size();
                log.info("[CarIndexingService] 재인덱싱 진행: {}건 완료", totalIndexed);
                
                if (cars.size() < batchSize) {
                    break;
                }
                
                offset += batchSize;
            }
            
            log.info("[CarIndexingService] 전체 차량 재인덱싱 완료: 총 {}건", totalIndexed);
        } catch (Exception e) {
            log.error("[CarIndexingService] 전체 차량 재인덱싱 실패", e);
            throw new RuntimeException("차량 재인덱싱 실패", e);
        }
    }

    /**
     * 증분 인덱싱: 업데이트된 차량만 인덱싱 (업데이트 시간 기준)
     * @param since 이 시간 이후에 업데이트된 차량만 인덱싱
     */
    public int incrementalIndex(LocalDateTime since) {
        log.info("[CarIndexingService] 증분 인덱싱 시작: since={}", since);
        
        try {
            int batchSize = 1000;
            int offset = 0;
            int totalIndexed = 0;
            
            while (true) {
                Map<String, Object> params = new HashMap<>();
                params.put("limit", batchSize);
                params.put("offset", offset);
                params.put("updatedSince", since);
                
                // 업데이트된 차량만 조회
                List<Map<String, Object>> cars = mapper.selectCarsForIndexingUpdated(params);
                if (cars == null || cars.isEmpty()) {
                    break;
                }
                
                // 데이터 정규화 및 인덱싱
                List<Map<String, Object>> indexData = normalizeIndexData(cars);
                meilisearchService.indexCars(indexData);
                
                totalIndexed += indexData.size();
                log.info("[CarIndexingService] 증분 인덱싱 진행: {}건 완료", totalIndexed);
                
                if (cars.size() < batchSize) {
                    break;
                }
                
                offset += batchSize;
            }
            
            log.info("[CarIndexingService] 증분 인덱싱 완료: 총 {}건", totalIndexed);
            return totalIndexed;
        } catch (Exception e) {
            log.error("[CarIndexingService] 증분 인덱싱 실패", e);
            throw new RuntimeException("증분 인덱싱 실패", e);
        }
    }

    /**
     * 배치 인덱싱: 특정 개수만큼만 인덱싱 (스케줄러용)
     * @param limit 인덱싱할 최대 개수
     * @return 실제 인덱싱된 개수
     */
    public int batchIndex(int limit) {
        log.info("[CarIndexingService] 배치 인덱싱 시작: limit={}", limit);
        
        try {
            int batchSize = Math.min(1000, limit);
            int offset = 0;
            int totalIndexed = 0;
            int remaining = limit;
            
            while (remaining > 0) {
                int currentBatchSize = Math.min(batchSize, remaining);
                
                Map<String, Object> params = new HashMap<>();
                params.put("limit", currentBatchSize);
                params.put("offset", offset);
                
                // 인덱싱 전용 쿼리 사용
                List<Map<String, Object>> cars = mapper.selectCarsForIndexing(params);
                if (cars == null || cars.isEmpty()) {
                    break;
                }
                
                // 데이터 정규화 및 인덱싱
                List<Map<String, Object>> indexData = normalizeIndexData(cars);
                meilisearchService.indexCars(indexData);
                
                int indexed = indexData.size();
                totalIndexed += indexed;
                remaining -= indexed;
                
                log.info("[CarIndexingService] 배치 인덱싱 진행: {}건 완료 (남은 작업: {}건)", 
                    totalIndexed, Math.max(0, remaining));
                
                if (cars.size() < currentBatchSize) {
                    break;
                }
                
                offset += indexed;
            }
            
            log.info("[CarIndexingService] 배치 인덱싱 완료: 총 {}건", totalIndexed);
            return totalIndexed;
        } catch (Exception e) {
            log.error("[CarIndexingService] 배치 인덱싱 실패", e);
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
                    meilisearchService.indexCar(indexData.get(0));
                    log.debug("[CarIndexingService] 차량 인덱싱 완료: carId={}", carId);
                }
            }
        } catch (Exception e) {
            log.error("[CarIndexingService] 차량 인덱싱 실패: carId={}", carId, e);
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
                log.warn("[CarIndexingService] 데이터 정규화 실패: {}", e.getMessage());
            }
        }
        
        return indexData;
    }

}

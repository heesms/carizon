package com.carizon.rag.service;

import com.carizon.rag.dto.CarEmbeddingDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 차량 데이터를 벡터 DB에 임베딩으로 저장하는 배치 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarEmbeddingBatchService {
    
    private final JdbcTemplate jdbcTemplate;
    private final CarTextConverterService textConverterService;
    private final EmbeddingService embeddingService;
    private final ChromaVectorStoreService vectorStoreService;
    
    /**
     * 모든 차량을 벡터 DB에 임베딩으로 저장
     */
    @Transactional(readOnly = true)
    public void embedAllCars() {
        String sql = """
            SELECT DISTINCT cm.car_id
            FROM car_master cm
            INNER JOIN platform_car pc ON pc.car_id = cm.car_id
            WHERE pc.status = 'ONSALE'
            ORDER BY cm.car_id
            """;
        
        List<Long> carIds = jdbcTemplate.queryForList(sql, Long.class);
        log.info("Found {} cars to embed", carIds.size());
        
        int successCount = 0;
        int failCount = 0;
        
        for (Long carId : carIds) {
            try {
                embedCar(carId);
                successCount++;
                
                if (successCount % 100 == 0) {
                    log.info("Processed {}/{} cars", successCount, carIds.size());
                }
            } catch (Exception e) {
                log.warn("Failed to embed car {}: {}", carId, e.getMessage());
                failCount++;
            }
        }
        
        log.info("Embedding completed: {} success, {} failed", successCount, failCount);
    }
    
    /**
     * 특정 차량을 임베딩으로 변환하여 벡터 DB에 저장
     */
    public void embedCar(Long carId) throws Exception {
        // 차량 데이터를 텍스트로 변환
        CarEmbeddingDto carEmbedding = textConverterService.createCarEmbedding(carId);
        if (carEmbedding == null) {
            throw new IllegalArgumentException("Car not found: " + carId);
        }
        
        // 임베딩 생성
        float[] embedding = embeddingService.generateEmbedding(carEmbedding.getText());
        carEmbedding.setEmbedding(embedding);
        
        // 벡터 DB에 저장
        vectorStoreService.addCarEmbedding(carEmbedding);
    }
    
    /**
     * 특정 범위의 차량만 임베딩 (증분 업데이트용)
     */
    @Transactional(readOnly = true)
    public void embedCarsInRange(Long fromCarId, Long toCarId) {
        String sql = """
            SELECT DISTINCT cm.car_id
            FROM car_master cm
            INNER JOIN platform_car pc ON pc.car_id = cm.car_id
            WHERE pc.status = 'ONSALE'
              AND cm.car_id > ? AND cm.car_id <= ?
            ORDER BY cm.car_id
            """;
        
        List<Long> carIds = jdbcTemplate.queryForList(sql, Long.class, fromCarId, toCarId);
        log.info("Found {} cars to embed in range {} - {}", carIds.size(), fromCarId, toCarId);
        
        int successCount = 0;
        for (Long carId : carIds) {
            try {
                embedCar(carId);
                successCount++;
            } catch (Exception e) {
                log.warn("Failed to embed car {}: {}", carId, e.getMessage());
            }
        }
        
        log.info("Embedding completed for range: {} success", successCount);
    }
}

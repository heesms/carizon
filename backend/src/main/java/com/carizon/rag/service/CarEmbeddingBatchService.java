package com.carizon.rag.service;

import com.carizon.rag.dto.CarEmbeddingDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

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

    private static final int WORKERS = 6;          // 병렬 워커 수
    private static final int BATCH_SIZE_DB = 300;  // DB에서 끊어 처리할 청크 크기
    private static final int BATCH_SIZE_CHROMA = 50; // Chroma add 배치 크기
    
    private final AtomicInteger metadataLogCount = new AtomicInteger(0); // Metadata 로그 출력 카운터
    
    /**
     * 모든 차량을 벡터 DB에 임베딩으로 저장
     */
    @Transactional(readOnly = true)
    public void embedAllCars() {
        embedAllCarsParallel();
    }
    
    /**
     * 특정 차량을 임베딩으로 변환하여 벡터 DB에 저장
     */
    public void embedCar(Long carId) throws Exception {
        log.info("[임베딩] 시작: carId={}", carId);
        
        // 차량 데이터를 텍스트로 변환
        CarEmbeddingDto carEmbedding = textConverterService.createCarEmbedding(carId);
        if (carEmbedding == null) {
            throw new IllegalArgumentException("Car not found: " + carId);
        }
        
        // Metadata 정보 로그
        log.info("[임베딩] 정보 [carId={}]:", carId);
        log.info("   - Metadata: {}", carEmbedding.getMetadata());
        log.info("   - Text: {}", carEmbedding.getText().replace("\n", " | "));
        
        // 임베딩 생성
        float[] embedding = embeddingService.generateEmbedding(carEmbedding.getText());
        carEmbedding.setEmbedding(embedding);
        log.info("   - Embedding 크기: {}차원", embedding.length);
        
        // 벡터 DB에 저장
        vectorStoreService.addCarEmbedding(carEmbedding);
        log.info("[임베딩] 완료: carId={}", carId);
    }
    
    /**
     * 증분 임베딩: 업데이트된 차량만 임베딩 (updated_at 기준)
     */
    @Transactional(readOnly = true)
    public int incrementalEmbed(java.time.LocalDateTime since) {
        String sql = """
            SELECT DISTINCT cm.car_id
            FROM car_master cm
            INNER JOIN platform_car pc ON pc.car_id = cm.car_id
            WHERE (
              pc.status = 'ONSALE'
              OR (pc.platform_name = 'ENCAR' AND pc.status = 'ADVERTISE')
            )
              AND cm.updated_at >= ?
            ORDER BY cm.updated_at ASC
            """;
        
        List<Long> carIds = jdbcTemplate.queryForList(sql, Long.class, 
            java.sql.Timestamp.valueOf(since));
        int totalCount = carIds.size();
        log.info("========================================");
        log.info("[임베딩] 증분 작업 시작");
        log.info("[임베딩] 총 요청 개수: {}개 (since: {})", totalCount, since);
        log.info("========================================");
        
        int successCount = 0;
        int failCount = 0;
        for (Long carId : carIds) {
            try {
                embedCar(carId);
                successCount++;
                if ((successCount + failCount) % 10 == 0) {
                    log.info("[임베딩] 진행 상황: {}/{} (성공: {}, 실패: {})", 
                        successCount + failCount, totalCount, successCount, failCount);
                }
            } catch (Exception e) {
                failCount++;
                log.warn("[임베딩] 실패 [carId={}]: {}", carId, e.getMessage());
            }
        }
        
        log.info("========================================");
        log.info("[임베딩] 증분 작업 완료");
        log.info("[임베딩] 총 요청: {}개 | 성공: {}개 | 실패: {}개", totalCount, successCount, failCount);
        log.info("========================================");
        return successCount;
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
            WHERE (
              pc.status = 'ONSALE'
              OR (pc.platform_name = 'ENCAR' AND pc.status = 'ADVERTISE')
            )
              AND cm.car_id > ? AND cm.car_id <= ?
            ORDER BY cm.car_id
            """;
        
        List<Long> carIds = jdbcTemplate.queryForList(sql, Long.class, fromCarId, toCarId);
        int totalCount = carIds.size();
        log.info("========================================");
        log.info("[임베딩] 범위 작업 시작");
        log.info("[임베딩] 총 요청 개수: {}개 (범위: {} - {})", totalCount, fromCarId, toCarId);
        log.info("========================================");
        
        int successCount = 0;
        int failCount = 0;
        for (Long carId : carIds) {
            try {
                embedCar(carId);
                successCount++;
                log.info("[임베딩] 진행 상황: {}/{} (성공: {}, 실패: {})", 
                    successCount + failCount, totalCount, successCount, failCount);
            } catch (Exception e) {
                failCount++;
                log.warn("[임베딩] 실패 [carId={}]: {}", carId, e.getMessage());
            }
        }
        
        log.info("========================================");
        log.info("[임베딩] 범위 작업 완료");
        log.info("[임베딩] 총 요청: {}개 | 성공: {}개 | 실패: {}개", totalCount, successCount, failCount);
        log.info("========================================");
    }

    /**
     * 병렬 + 배치 처리로 전체 차량 임베딩
     */
    @Transactional(readOnly = true)
    public void embedAllCarsParallel() {
        String sql = """
            SELECT DISTINCT cm.car_id
            FROM car_master cm
            INNER JOIN platform_car pc ON pc.car_id = cm.car_id
            WHERE (
              pc.status = 'ONSALE'
              OR (pc.platform_name = 'ENCAR' AND pc.status = 'ADVERTISE')
            )
            ORDER BY cm.car_id
            """;

        List<Long> carIds = jdbcTemplate.queryForList(sql, Long.class);
        int totalCount = carIds.size();
        log.info("========================================");
        log.info("[임베딩] 작업 시작");
        log.info("[임베딩] 총 요청 개수: {}개", totalCount);
        log.info("========================================");

        // Metadata 로그 카운터 초기화
        metadataLogCount.set(0);

        ExecutorService executor = Executors.newFixedThreadPool(WORKERS);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        AtomicInteger processed = new AtomicInteger();

        try {
            // DB 청크 단위로 처리 (너무 많은 future 생성 방지)
            for (List<Long> chunk : partition(carIds, BATCH_SIZE_DB)) {
                List<Future<CarEmbeddingDto>> futures = new ArrayList<>();
                for (Long carId : chunk) {
                    futures.add(executor.submit(() -> buildEmbeddingDto(carId)));
                }

                List<CarEmbeddingDto> ready = new ArrayList<>();
                for (Future<CarEmbeddingDto> f : futures) {
                    try {
                        CarEmbeddingDto dto = f.get();
                        if (dto != null && dto.getEmbedding() != null) {
                            ready.add(dto);
                            int currentSuccess = success.incrementAndGet();
                            int currentProcessed = processed.incrementAndGet();
                            
                            // 진행 상황 로그 (10개마다 또는 마지막)
                            if (currentProcessed % 10 == 0 || currentProcessed == totalCount) {
                                log.info("[임베딩] 진행 상황: {}/{} (성공: {}, 실패: {})", 
                                    currentProcessed, totalCount, currentSuccess, fail.get());
                            }
                        } else {
                            fail.incrementAndGet();
                            processed.incrementAndGet();
                        }
                    } catch (Exception e) {
                        fail.incrementAndGet();
                        processed.incrementAndGet();
                        log.warn("[임베딩] 실패: {}", e.getMessage());
                    }
                }

                // Chroma 배치 전송
                for (List<CarEmbeddingDto> chromaBatch : partition(ready, BATCH_SIZE_CHROMA)) {
                    try {
                        vectorStoreService.addCarEmbeddingsBatch(chromaBatch);
                        log.debug("[ChromaDB] 배치 저장 완료: {}개", chromaBatch.size());
                    } catch (Exception e) {
                        int batchSize = chromaBatch.size();
                        fail.addAndGet(batchSize);
                        success.addAndGet(-batchSize); // 성공 카운트에서 제거
                        log.warn("[ChromaDB] 배치 저장 실패 ({}개): {}", batchSize, e.getMessage());
                    }
                }
            }
        } finally {
            executor.shutdown();
        }

        log.info("========================================");
        log.info("[임베딩] 작업 완료");
        log.info("[임베딩] 총 요청: {}개 | 성공: {}개 | 실패: {}개", 
            totalCount, success.get(), fail.get());
        log.info("========================================");
    }

    private CarEmbeddingDto buildEmbeddingDto(Long carId) throws Exception {
        CarEmbeddingDto dto = textConverterService.createCarEmbedding(carId);
        if (dto == null) {
            log.warn("[임베딩] 차량 데이터 없음: carId={}", carId);
            return null;
        }
        
        // Metadata 정보 로그 출력 (처음 3개만 상세 로그)
        int logCount = metadataLogCount.incrementAndGet();
        if (logCount <= 3) {
            log.info("[임베딩] 정보 [carId={}]:", carId);
            log.info("   - Metadata: {}", dto.getMetadata());
            log.info("   - Text: {}", dto.getText().replace("\n", " | "));
        }
        
        dto.setEmbedding(embeddingService.generateEmbedding(dto.getText()));
        return dto;
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> res = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            res.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return res;
    }
}

package com.carizon.rag.service;

import com.carizon.rag.dto.CarEmbeddingDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private static final int WORKERS = 10;         // 병렬 워커 수 (Ollama 병목 시 조정)
    private static final int BATCH_SIZE_DB = 400;  // DB에서 끊어 처리할 청크 크기
    private static final int BATCH_SIZE_CHROMA = 80; // Chroma add 배치 크기
    private static final int PROGRESS_LOG_INTERVAL = 50; // N건마다 진행 로그

    private final AtomicInteger metadataLogCount = new AtomicInteger(0); // Metadata 로그 출력 카운터

    /** API/로그용 진행 상황 (진행 중일 때만 갱신, 배치 종료 후 마지막 결과 유지) */
    private volatile boolean progressRunning = false;
    private volatile int progressTotal = 0;
    private volatile int progressProcessed = 0;
    private volatile int progressOk = 0;
    private volatile int progressFail = 0;
    private volatile long progressStartedAt = 0L;
    private volatile String progressMessage = "";

    public Map<String, Object> getProgress() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("running", progressRunning);
        m.put("total", progressTotal);
        m.put("processed", progressProcessed);
        m.put("ok", progressOk);
        m.put("fail", progressFail);
        m.put("startedAt", progressStartedAt > 0 ? progressStartedAt : null);
        m.put("message", progressMessage != null ? progressMessage : "");
        return m;
    }
    
    /**
     * 모든 차량을 벡터 DB에 임베딩으로 저장
     */
    @Transactional(readOnly = true)
    public int embedAllCars() {
        return embedAllCarsParallel();
    }
    
    /**
     * 특정 차량을 임베딩으로 변환하여 벡터 DB에 저장
     */
    public void embedCar(Long carId) throws Exception {
        log.info("[embedding] start: carId={}", carId);
        
        // 차량 데이터를 텍스트로 변환
        CarEmbeddingDto carEmbedding = textConverterService.createCarEmbedding(carId);
        if (carEmbedding == null) {
            throw new IllegalArgumentException("Car not found: " + carId);
        }
        
        // Metadata 정보 로그
        log.info("[embedding] info [carId={}]:", carId);
        log.info("   - Metadata: {}", carEmbedding.getMetadata());
        log.info("   - Text: {}", carEmbedding.getText().replace("\n", " | "));
        
        // 임베딩 생성
        float[] embedding = embeddingService.generateEmbedding(carEmbedding.getText());
        carEmbedding.setEmbedding(embedding);
        log.info("   - Embedding size: {} dims", embedding.length);
        
        // 벡터 DB에 저장
        vectorStoreService.addCarEmbedding(carEmbedding);
        log.info("[embedding] done: carId={}", carId);
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
              AND pc.price IS NOT NULL AND pc.price > 0
              AND cm.updated_at >= ?
            ORDER BY cm.updated_at ASC
            """;
        
        List<Long> carIds = jdbcTemplate.queryForList(sql, Long.class, 
            java.sql.Timestamp.valueOf(since));
        return embedCarIdsParallel(carIds, "INCREMENTAL JOB", "증분 임베딩 (since: " + since + ")");
    }

    /**
     * 특정 범위의 차량만 임베딩 (증분 업데이트용)
     */
    @Transactional(readOnly = true)
    public int embedCarsInRange(Long fromCarId, Long toCarId) {
        String sql = """
            SELECT DISTINCT cm.car_id
            FROM car_master cm
            INNER JOIN platform_car pc ON pc.car_id = cm.car_id
            WHERE (
              pc.status = 'ONSALE'
              OR (pc.platform_name = 'ENCAR' AND pc.status = 'ADVERTISE')
            )
              AND pc.price IS NOT NULL AND pc.price > 0
              AND cm.car_id > ? AND cm.car_id <= ?
            ORDER BY cm.car_id
            """;
        
        List<Long> carIds = jdbcTemplate.queryForList(sql, Long.class, fromCarId, toCarId);
        return embedCarIdsParallel(carIds, "RANGE JOB", "범위 임베딩 (" + fromCarId + " - " + toCarId + ")");
    }

    /**
     * 병렬 + 배치 처리로 전체 차량 임베딩
     */
    @Transactional(readOnly = true)
    public int embedAllCarsParallel() {
        String sql = """
            SELECT DISTINCT cm.car_id
            FROM car_master cm
            INNER JOIN platform_car pc ON pc.car_id = cm.car_id
            WHERE (
              pc.status = 'ONSALE'
              OR (pc.platform_name = 'ENCAR' AND pc.status = 'ADVERTISE')
            )
              AND pc.price IS NOT NULL AND pc.price > 0
            ORDER BY cm.car_id
            """;

        List<Long> carIds = jdbcTemplate.queryForList(sql, Long.class);
        return embedCarIdsParallel(carIds, "EMBEDDING JOB", "전체 임베딩");
    }

    private CarEmbeddingDto buildEmbeddingDto(Long carId) throws Exception {
        CarEmbeddingDto dto = textConverterService.createCarEmbedding(carId);
        if (dto == null) {
            log.warn("[embedding] no car data: carId={}", carId);
            return null;
        }
        
        // Metadata 정보 로그 출력 (처음 3개만 상세 로그)
        int logCount = metadataLogCount.incrementAndGet();
        if (logCount <= 3) {
            log.info("[embedding] info [carId={}]:", carId);
            log.info("   - Metadata: {}", dto.getMetadata());
            log.info("   - Text: {}", dto.getText().replace("\n", " | "));
        }
        
        dto.setEmbedding(embeddingService.generateEmbedding(dto.getText()));
        return dto;
    }

    /**
     * 조건별 제한 임베딩: 제조사(메이커)·개수로 필터링하여 해당 차량만 Chroma에 임베딩.
     * 예: 볼보 100개 → makerCode=VOLVO 또는 maker=볼보, limit=100
     *
     * @param limit     임베딩할 최대 건수 (기본 100, 최대 5000)
     * @param makerCode 제조사 코드 (예: VOLVO), null이면 무시
     * @param maker     제조사 한글명 부분 일치 (예: 볼보), null이면 무시. cz_maker.maker_name LIKE %maker%
     * @return 실제 임베딩 성공 건수
     */
    @Transactional(readOnly = true)
    public int embedByFilter(int limit, String makerCode, String maker) {
        int cappedLimit = Math.min(Math.max(limit, 1), 5000);

        StringBuilder sql = new StringBuilder("""
            SELECT DISTINCT cm.car_id
            FROM car_master cm
            INNER JOIN platform_car pc ON pc.car_id = cm.car_id
            """);
        if (maker != null && !maker.isBlank()) {
            sql.append(" LEFT JOIN cz_maker m ON m.maker_code = cm.maker_code ");
        }
        sql.append("""
            WHERE (
              pc.status = 'ONSALE'
              OR (pc.platform_name = 'ENCAR' AND pc.status = 'ADVERTISE')
            )
              AND pc.price IS NOT NULL AND pc.price > 0
            """);
        List<Object> params = new ArrayList<>();
        if (makerCode != null && !makerCode.isBlank()) {
            sql.append(" AND cm.maker_code = ? ");
            params.add(makerCode.trim());
        }
        if (maker != null && !maker.isBlank()) {
            sql.append(" AND m.maker_name LIKE ? ");
            params.add("%" + maker.trim() + "%");
        }
        sql.append(" ORDER BY cm.car_id LIMIT ? ");
        params.add(cappedLimit);

        List<Long> carIds = jdbcTemplate.queryForList(sql.toString(), Long.class, params.toArray());
        return embedCarIdsParallel(
                carIds,
                "FILTER EMBEDDING",
                "필터 임베딩 (limit=" + cappedLimit + ", maker=" + (maker != null ? maker : "") + ")"
        );
    }

    private int embedCarIdsParallel(List<Long> carIds, String bannerTitle, String progressMsg) {
        int totalCount = carIds != null ? carIds.size() : 0;

        progressRunning = true;
        progressTotal = totalCount;
        progressProcessed = 0;
        progressOk = 0;
        progressFail = 0;
        progressStartedAt = System.currentTimeMillis();
        progressMessage = progressMsg;

        log.info("************************************");
        log.info("***   {} START   ***  total={}", bannerTitle, totalCount);
        log.info("************************************");

        if (carIds == null || carIds.isEmpty()) {
            progressRunning = false;
            log.info("[embedding] no cars to process, skip.");
            return 0;
        }

        textConverterService.clearModelBasicInfoCache();
        metadataLogCount.set(0);
        ExecutorService executor = Executors.newFixedThreadPool(WORKERS);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        AtomicInteger processed = new AtomicInteger();

        try {
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
                            success.incrementAndGet();
                        } else {
                            fail.incrementAndGet();
                        }
                    } catch (Exception e) {
                        fail.incrementAndGet();
                        log.warn("[embedding] dto build fail: {}", e.getMessage());
                    } finally {
                        int cur = processed.incrementAndGet();
                        progressProcessed = cur;
                        progressOk = success.get();
                        progressFail = fail.get();
                        if (cur % PROGRESS_LOG_INTERVAL == 0 || cur == totalCount) {
                            log.info("[embedding] 진행: {}/{} 건 (성공: {}, 실패: {})", cur, totalCount, success.get(), fail.get());
                        }
                    }
                }

                for (List<CarEmbeddingDto> chromaBatch : partition(ready, BATCH_SIZE_CHROMA)) {
                    try {
                        vectorStoreService.addCarEmbeddingsBatch(chromaBatch);
                    } catch (Exception e) {
                        int batchSize = chromaBatch.size();
                        fail.addAndGet(batchSize);
                        success.addAndGet(-batchSize);
                        progressFail = fail.get();
                        progressOk = success.get();
                        log.warn("[ChromaDB] batch save failed ({}): {}", batchSize, e.getMessage());
                    }
                }
            }
        } finally {
            executor.shutdown();
            progressRunning = false;
            progressProcessed = processed.get();
            progressOk = success.get();
            progressFail = fail.get();
            textConverterService.clearModelBasicInfoCache();
        }

        log.info("************************************");
        log.info("***   {} DONE    ***  ok={} fail={}", bannerTitle, success.get(), fail.get());
        log.info("************************************");
        return success.get();
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> res = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            res.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return res;
    }
}

package com.carizon.recommendation.service;

import com.carizon.recommendation.dto.WeeklyBestCarDto;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * 메인 페이지용 주간 BEST 스냅샷을 Elasticsearch에 6시간 주기로 저장하고 조회하는 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyBestHomeSnapshotService {

    private static final String SNAPSHOT_INDEX = "weekly_best_home";
    private static final String SNAPSHOT_ID = "home:all";
    private static final int DEFAULT_LIMIT = 20;

    private final WeeklyBestCarRankingService rankingService;
    private final ElasticsearchClient elasticsearchClient;
    private final ObjectMapper objectMapper;

    private final AtomicBoolean refreshRunning = new AtomicBoolean(false);

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void refreshOnSchedule() {
        log.info("[weekly-best-home-snapshot] scheduled refresh start");
        refreshSnapshot(DEFAULT_LIMIT, true);
    }

    public List<WeeklyBestCarDto> getHomeSnapshot(int limit) {
        int normalized = Math.max(1, Math.min(limit, 100));
        List<WeeklyBestCarDto> cached = loadFromEs(normalized);
        if (!cached.isEmpty()) {
            return cached;
        }

        log.info("[weekly-best-home-snapshot] cache miss, fallback to live ranking (limit={})", normalized);
        return rankingService.getWeeklyBestCars(null, null, normalized);
    }

    public int refreshSnapshot(int limit) {
        return refreshSnapshot(Math.max(1, Math.min(limit, 100)), false);
    }

    private int refreshSnapshot(int limit, boolean scheduled) {
        if (!refreshRunning.compareAndSet(false, true)) {
            log.warn("[weekly-best-home-snapshot] refresh skipped (running)");
            return 0;
        }
        try {
            rankingService.evictCache(); // 캐시 제거 후 DB에서 신선한 데이터 조회
            List<WeeklyBestCarDto> cars = rankingService.getWeeklyBestCars(null, null, limit);
            upsertSnapshot(cars, limit, scheduled ? "scheduled" : "manual");
            return cars.size();
        } finally {
            refreshRunning.set(false);
        }
    }

    private List<WeeklyBestCarDto> loadFromEs(int limit) {
        try {
            ensureIndex();
            var getResponse = elasticsearchClient.get(g -> g
                    .index(SNAPSHOT_INDEX)
                    .id(SNAPSHOT_ID),
                Map.class
            );

            if (Boolean.FALSE.equals(getResponse.found()) || getResponse.source() == null) {
                return List.of();
            }

            Map<String, Object> source = getResponse.source();
            Object rawCars = source.get("cars");
            if (rawCars == null) {
                return List.of();
            }

            List<Map<String, Object>> carMaps = objectMapper.convertValue(rawCars, new TypeReference<List<Map<String, Object>>>() {});
            if (carMaps == null || carMaps.isEmpty()) {
                return List.of();
            }

            return carMaps.stream()
                    .filter(Objects::nonNull)
                    .map(this::toDto)
                    .filter(Objects::nonNull)
                    .limit(limit)
                    .toList();
        } catch (ElasticsearchException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("index_not_found_exception") || msg.contains("no such index")) {
                return List.of();
            }
            log.warn("[weekly-best-home-snapshot] failed to read snapshot: {}", msg);
            return List.of();
        } catch (IOException e) {
            log.warn("[weekly-best-home-snapshot] failed to read snapshot", e);
            return List.of();
        }
    }

    private void upsertSnapshot(List<WeeklyBestCarDto> cars, int limit, String source) {
        if (cars == null) {
            cars = new ArrayList<>();
        }

        try {
            ensureIndex();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("snapshotId", SNAPSHOT_ID);
            payload.put("source", source);
            payload.put("generatedAt", LocalDateTime.now().toString());
            payload.put("requestedLimit", limit);
            payload.put("carCount", cars.size());
            payload.put("cars", cars.stream().map(this::toMap).toList());

            elasticsearchClient.index(i -> i
                    .index(SNAPSHOT_INDEX)
                    .id(SNAPSHOT_ID)
                    .document(payload)
            );

            log.info("[weekly-best-home-snapshot] snapshot upserted: {} cars (limit={})", cars.size(), limit);
        } catch (Exception e) {
            log.warn("[weekly-best-home-snapshot] snapshot upsert failed", e);
            throw new RuntimeException("주간 BEST 홈 스냅샷 저장 실패: " + e.getMessage(), e);
        }
    }

    private void ensureIndex() throws IOException {
        boolean exists = elasticsearchClient.indices().exists(e -> e.index(SNAPSHOT_INDEX)).value();
        if (!exists) {
            elasticsearchClient.indices().create(c -> c.index(SNAPSHOT_INDEX));
            log.info("[weekly-best-home-snapshot] index created: {}", SNAPSHOT_INDEX);
        }
    }

    private WeeklyBestCarDto toDto(Map<String, Object> raw) {
        try {
            return objectMapper.convertValue(raw, WeeklyBestCarDto.class);
        } catch (Exception e) {
            log.warn("[weekly-best-home-snapshot] car map convert failed: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> toMap(WeeklyBestCarDto car) {
        return objectMapper.convertValue(car, new TypeReference<LinkedHashMap<String, Object>>() {});
    }
}

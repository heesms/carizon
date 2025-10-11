package com.carizon.api.service;

import com.carizon.api.entity.CarPriceHistory;
import com.carizon.api.entity.PlatformCar;
import com.carizon.api.repository.CarPriceHistoryRepository;
import com.carizon.api.repository.PlatformCarRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * UpsertService handles upsert logic for platform_car with UNIQUE(platform_name, platform_car_key)
 * and creates price snapshots as needed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpsertService {

    private final PlatformCarRepository platformCarRepository;
    private final CarPriceHistoryRepository priceHistoryRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Upsert platform car and create price snapshot if price changed.
     * Uses ON DUPLICATE KEY UPDATE for platform_car table.
     */
    @Transactional
    public void upsertPlatformCar(PlatformCar platformCar) {
        
        // Use JDBC for upsert with ON DUPLICATE KEY UPDATE
        String sql = """
            INSERT INTO platform_car 
            (platform_name, platform_car_key, car_id, car_no,
             maker_code, model_group_code, model_code, trim_code, grade_code,
             maker_name, model_group_name, model_name, trim_name,
             price, km, displacement, yymm, status, color, fuel, transmission, body_type, region,
             m_url, pc_url, first_ad_day, last_seen_date, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                car_id = COALESCE(VALUES(car_id), car_id),
                car_no = COALESCE(VALUES(car_no), car_no),
                maker_code = COALESCE(VALUES(maker_code), maker_code),
                model_group_code = COALESCE(VALUES(model_group_code), model_group_code),
                model_code = COALESCE(VALUES(model_code), model_code),
                trim_code = COALESCE(VALUES(trim_code), trim_code),
                grade_code = COALESCE(VALUES(grade_code), grade_code),
                maker_name = COALESCE(VALUES(maker_name), maker_name),
                model_group_name = COALESCE(VALUES(model_group_name), model_group_name),
                model_name = COALESCE(VALUES(model_name), model_name),
                trim_name = COALESCE(VALUES(trim_name), trim_name),
                price = VALUES(price),
                km = VALUES(km),
                displacement = VALUES(displacement),
                yymm = VALUES(yymm),
                status = VALUES(status),
                color = VALUES(color),
                fuel = VALUES(fuel),
                transmission = VALUES(transmission),
                body_type = VALUES(body_type),
                region = VALUES(region),
                m_url = VALUES(m_url),
                pc_url = VALUES(pc_url),
                last_seen_date = VALUES(last_seen_date),
                updated_at = VALUES(updated_at)
            """;

        LocalDateTime now = LocalDateTime.now();
        
        jdbcTemplate.update(sql,
            platformCar.getPlatformName(),
            platformCar.getPlatformCarKey(),
            platformCar.getCarId(),
            platformCar.getCarNo(),
            platformCar.getMakerCode(),
            platformCar.getModelGroupCode(),
            platformCar.getModelCode(),
            platformCar.getTrimCode(),
            platformCar.getGradeCode(),
            platformCar.getMakerName(),
            platformCar.getModelGroupName(),
            platformCar.getModelName(),
            platformCar.getTrimName(),
            platformCar.getPrice(),
            platformCar.getKm(),
            platformCar.getDisplacement(),
            platformCar.getYymm(),
            platformCar.getStatus(),
            platformCar.getColor(),
            platformCar.getFuel(),
            platformCar.getTransmission(),
            platformCar.getBodyType(),
            platformCar.getRegion(),
            platformCar.getMUrl(),
            platformCar.getPcUrl(),
            platformCar.getFirstAdDay(),
            platformCar.getLastSeenDate(),
            now,
            now
        );

        // Get the platform_car_id after upsert
        PlatformCar saved = platformCarRepository
            .findByPlatformNameAndPlatformCarKey(
                platformCar.getPlatformName(), 
                platformCar.getPlatformCarKey()
            )
            .orElseThrow(() -> new RuntimeException("Failed to retrieve upserted platform car"));

        // Create price snapshot
        if (platformCar.getPrice() != null) {
            createPriceSnapshot(saved.getPlatformCarId(), platformCar.getPrice());
        }
    }

    /**
     * Create a price snapshot. Mark previous as not current if price changed.
     */
    private void createPriceSnapshot(Long platformCarId, Integer newPrice) {
        
        // Check if price changed from current
        String checkSql = "SELECT price FROM car_price_history WHERE platform_car_id = ? AND is_current = 1 LIMIT 1";
        
        Integer currentPrice = jdbcTemplate.query(checkSql, 
            rs -> rs.next() ? rs.getInt("price") : null,
            platformCarId
        );

        LocalDateTime now = LocalDateTime.now();
        
        if (currentPrice == null || !currentPrice.equals(newPrice)) {
            // Mark previous as not current
            String updateSql = "UPDATE car_price_history SET is_current = 0 WHERE platform_car_id = ? AND is_current = 1";
            jdbcTemplate.update(updateSql, platformCarId);

            // Insert new snapshot
            CarPriceHistory history = CarPriceHistory.builder()
                .platformCarId(platformCarId)
                .price(newPrice)
                .checkedAt(now)
                .isCurrent(true)
                .lastSeenAt(now)
                .build();
            
            priceHistoryRepository.save(history);
            log.debug("Created price snapshot for platform_car_id={}, price={}", platformCarId, newPrice);
        } else {
            // Price unchanged, just update last_seen_at
            String updateSql = "UPDATE car_price_history SET last_seen_at = ? WHERE platform_car_id = ? AND is_current = 1";
            jdbcTemplate.update(updateSql, now, platformCarId);
        }
    }
}

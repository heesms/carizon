package com.carizon.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CarMasterIdSequenceService {

    private final JdbcTemplate jdbc;

    /**
     * car_master의 다음 AUTO_INCREMENT 값을 조회한다.
     */
    public long snapshotNextCarId() {
        try {
            Long next = jdbc.queryForObject("""
                SELECT AUTO_INCREMENT
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'car_master'
            """, Long.class);
            if (next == null || next <= 0) {
                throw new IllegalStateException("invalid AUTO_INCREMENT value for car_master: " + next);
            }
            return next;
        } catch (Exception e) {
            throw new IllegalStateException("failed to snapshot car_master AUTO_INCREMENT", e);
        }
    }

    /**
     * TRUNCATE 이후 AUTO_INCREMENT 시작값을 복구한다.
     */
    public void restoreNextCarId(long nextCarId) {
        long safeNext = Math.max(1L, nextCarId);
        if (safeNext <= 1L) {
            return;
        }
        try {
            jdbc.execute("ALTER TABLE car_master AUTO_INCREMENT = " + safeNext);
            log.info("[car-master-id] restored AUTO_INCREMENT to {}", safeNext);
        } catch (Exception e) {
            throw new IllegalStateException("failed to restore car_master AUTO_INCREMENT to " + safeNext, e);
        }
    }
}

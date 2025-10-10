package com.carizon.core.repository;

import com.carizon.core.domain.history.CarPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CarPriceHistoryRepository extends JpaRepository<CarPriceHistory, Long> {
    List<CarPriceHistory> findByPlatformCarIdOrderByCheckedAtAsc(Long platformCarId);
}

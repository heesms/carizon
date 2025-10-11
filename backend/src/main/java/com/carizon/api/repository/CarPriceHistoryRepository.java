package com.carizon.api.repository;

import com.carizon.api.entity.CarPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CarPriceHistoryRepository extends JpaRepository<CarPriceHistory, Long> {
    
    @Query("SELECT h FROM CarPriceHistory h WHERE h.platformCarId = :platformCarId ORDER BY h.checkedAt ASC")
    List<CarPriceHistory> findByPlatformCarIdOrderByCheckedAtAsc(@Param("platformCarId") Long platformCarId);
    
    @Query("SELECT h FROM CarPriceHistory h WHERE h.platformCarId IN :platformCarIds ORDER BY h.checkedAt ASC")
    List<CarPriceHistory> findByPlatformCarIdInOrderByCheckedAtAsc(@Param("platformCarIds") List<Long> platformCarIds);
}

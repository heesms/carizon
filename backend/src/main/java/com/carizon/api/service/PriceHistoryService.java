package com.carizon.api.service;

import com.carizon.api.dto.PriceHistoryPointDto;
import com.carizon.api.mapper.PriceHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PriceHistoryService {

    private final PriceHistoryMapper priceHistoryMapper;

    /**
     * Get price history for a car using MyBatis.
     * If platformCarId is specified, returns history for that specific platform listing.
     * Otherwise, returns aggregated history from all platforms.
     */
    public List<PriceHistoryPointDto> getHistory(Long carId, Long platformCarId) {
        
        if (platformCarId != null) {
            // Return history for specific platform car
            return priceHistoryMapper.getPriceHistoryByPlatformCarId(platformCarId);
        } else {
            // Return representative history (from all platforms)
            return priceHistoryMapper.getPriceHistoryByCarId(carId);
        }
    }
}

package com.carizon.api.service;

import com.carizon.api.dto.PriceHistoryPointDto;
import com.carizon.api.entity.CarPriceHistory;
import com.carizon.api.entity.PlatformCar;
import com.carizon.api.repository.CarPriceHistoryRepository;
import com.carizon.api.repository.PlatformCarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PriceHistoryService {

    private final CarPriceHistoryRepository priceHistoryRepository;
    private final PlatformCarRepository platformCarRepository;

    /**
     * Get price history for a car.
     * If platformCarId is specified, returns history for that specific platform listing.
     * Otherwise, returns aggregated history from all platforms (representative).
     */
    public List<PriceHistoryPointDto> getHistory(Long carId, Long platformCarId) {
        
        if (platformCarId != null) {
            // Return history for specific platform car
            List<CarPriceHistory> history = priceHistoryRepository
                .findByPlatformCarIdOrderByCheckedAtAsc(platformCarId);
            
            PlatformCar platformCar = platformCarRepository.findById(platformCarId)
                .orElseThrow(() -> new RuntimeException("Platform car not found: " + platformCarId));
            
            return history.stream()
                .map(h -> PriceHistoryPointDto.builder()
                    .checkedAt(h.getCheckedAt())
                    .price(h.getPrice())
                    .platformName(platformCar.getPlatformName())
                    .build())
                .collect(Collectors.toList());
        } else {
            // Return representative history (from all platforms)
            List<PlatformCar> platformCars = platformCarRepository.findByCarId(carId);
            List<Long> platformCarIds = platformCars.stream()
                .map(PlatformCar::getPlatformCarId)
                .collect(Collectors.toList());
            
            if (platformCarIds.isEmpty()) {
                return List.of();
            }
            
            List<CarPriceHistory> allHistory = priceHistoryRepository
                .findByPlatformCarIdInOrderByCheckedAtAsc(platformCarIds);
            
            // Create a map for platform names
            Map<Long, String> platformNames = platformCars.stream()
                .collect(Collectors.toMap(
                    PlatformCar::getPlatformCarId,
                    PlatformCar::getPlatformName
                ));
            
            return allHistory.stream()
                .map(h -> PriceHistoryPointDto.builder()
                    .checkedAt(h.getCheckedAt())
                    .price(h.getPrice())
                    .platformName(platformNames.get(h.getPlatformCarId()))
                    .build())
                .collect(Collectors.toList());
        }
    }
}

package com.carizon.dto;

import java.time.LocalDateTime;

public record CarListItemDto(long carId, String maker, String model, String trim, Integer year, Integer km,
                             Integer priceMin, Integer priceMax, LocalDateTime priceUpdatedAt,
                             String representativeImageUrl, String modelCode,
                             String fuel, String region) {
}
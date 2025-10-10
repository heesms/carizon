package com.carizon.core.dto;

import java.util.List;

public record CarDetailDto(
        Long id,
        String name,
        Integer year,
        Integer mileage,
        Integer price,
        String fuel,
        String transmission,
        String bodyType,
        String region,
        List<PlatformOfferDto> offers,
        List<PriceSnapshotDto> priceHistory
) {
    public record PlatformOfferDto(
            String platform,
            Integer price,
            Integer mileage,
            String url
    ) {}

    public record PriceSnapshotDto(
            String checkedAt,
            Integer price
    ) {}
}

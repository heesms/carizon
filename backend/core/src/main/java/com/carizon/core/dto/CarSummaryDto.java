package com.carizon.core.dto;

import java.util.List;

public record CarSummaryDto(
        Long id,
        String name,
        Integer year,
        Integer mileage,
        Integer price,
        String fuel,
        String transmission,
        List<String> platforms
) {}

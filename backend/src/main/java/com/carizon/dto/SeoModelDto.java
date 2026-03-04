package com.carizon.dto;

public record SeoModelDto(
        String modelCode,
        String modelName,
        String makerCode,
        String makerName,
        String description,
        String imageUrl,
        long carCount,
        Integer priceMin,
        Integer priceMax
) {}

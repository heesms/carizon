package com.carizon.api.dto;

public record ModelImageDto(
    Long id,
    String modelCode,
    String imageUrl,
    Integer sortOrder,
    Boolean isMain
) {
}

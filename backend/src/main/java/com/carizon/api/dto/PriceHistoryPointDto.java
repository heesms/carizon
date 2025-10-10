package com.carizon.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PriceHistoryPointDto {
    private LocalDateTime checkedAt;
    private Integer price;
    private String platformName;
}

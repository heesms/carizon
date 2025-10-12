package com.carizon.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class PlatformListingDto {
    private Long platformCarId;
    private String platformName;
    private String platformCarKey;
    private Integer price;
    private Integer km;
    private String status;
    private String mUrl;
    private String pcUrl;
    private LocalDate firstAdDay;
    private LocalDate lastSeenDate;
}

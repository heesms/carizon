package com.carizon.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CarDetailDto {
    private Long carId;
    private String carNo;
    private String makerCode;
    private String modelGroupCode;
    private String modelCode;
    private String trimCode;
    private String gradeCode;
    private String makerName;
    private String modelGroupName;
    private String modelName;
    private String trimName;
    private Short year;
    private Integer mileage;
    private String color;
    private String transmission;
    private String fuel;
    private Integer displacement;
    private String bodyType;
    private String region;
    private String advStatus;
    private String representativeImageUrl;
    private List<PlatformListingDto> platformListings;
}

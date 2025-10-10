package com.carizon.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CarListItemDto {
    private Long carId;
    private String carNo;
    private String makerName;
    private String modelGroupName;
    private String modelName;
    private String trimName;
    private Short year;
    private Integer mileage;
    private String color;
    private String transmission;
    private String fuel;
    private String region;
    private String advStatus;
    private Integer representativePrice;
    private String representativeImageUrl;
}

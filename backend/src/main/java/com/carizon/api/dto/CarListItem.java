package com.carizon.api.dto;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CarListItem {
    private Long platformCarId;
    private String platformName;
    private String platformCarKey;
    private String carNo;
    private Long carId;
    private String makerCode;
    private String modelGroupCode;
    private String modelCode;
    private String trimCode;
    private String gradeCode;
    private String makerName;
    private String modelGroupName;
    private String modelName;
    private String trimName;
    private String gradeName;
    private Integer price;
    private Integer km;
    private String yymm;
    private String status;
    private String color;
    private String fuel;
    private String transmission;
    private String bodyType;
    private String mUrl;
    private String pcUrl;
    private LocalDate firstAdDay;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String extra;
    private LocalDate lastSeenDate;
    private String region;
    private Integer displacement;
}

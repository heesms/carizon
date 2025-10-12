package com.carizon.api.dto;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CarMasterRow {
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
    private LocalDate lastSeenDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

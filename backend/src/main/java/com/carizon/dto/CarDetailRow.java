package com.carizon.dto;

/** 차량 상세 1행 (플랫폼별). representativeImageUrl은 플랫폼 우선순위 기반 platform_car.car_image_url */
public record CarDetailRow(
    long carId, String makerName, String modelGroupName, String modelName, String trimName,
    Integer year, Integer mileage, Integer displacement, String fuel, String transmission,
    String color, String bodyType, String region, Integer seatCount, Integer myAccidentCnt,
    Long platformCarId, String platformName, Integer price, String status, String pcUrl, String mUrl, String lastSeenDate,
    String optionArray, String representativeImageUrl
) {}

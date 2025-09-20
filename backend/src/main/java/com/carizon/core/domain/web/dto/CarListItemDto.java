package com.carizon.core.domain.web.dto;

import lombok.*;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class CarListItemDto {
    private String myCarKey;
    private String numberPlate;
    private String makerName;
    private String modelGroupName;
    private String modelName;
    private String trimName;
    private String gradeName;
    private String advertStatus;
    private Integer minPrice;
    private Integer maxPrice;
    private String cheapestPlatform; // "CHUTCHA:1500" 형태
}

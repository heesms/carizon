package com.carizon.core.domain.web.dto;

import lombok.*;
import java.util.List;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class CarDetailDto {
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
    private List<PlatformPrice> platforms;

    @Getter @Setter @Builder
    @NoArgsConstructor @AllArgsConstructor
    public static class PlatformPrice {
        private String source;     // ENCAR, CHACHACHA, KCAR, CHUTCHA
        private Integer priceNow;  // 만원
        private String detailUrl;  // 아래 규칙으로 조합
    }
}

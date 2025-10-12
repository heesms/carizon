package com.carizon.api.mapper;

import com.carizon.api.dto.PriceHistoryPointDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PriceHistoryMapper {
    List<PriceHistoryPointDto> getPriceHistoryByPlatformCarId(@Param("platformCarId") Long platformCarId);
    
    List<PriceHistoryPointDto> getPriceHistoryByCarId(@Param("carId") Long carId);
}

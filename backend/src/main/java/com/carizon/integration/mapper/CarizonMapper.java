package com.carizon.integration.mapper;

import com.carizon.integration.dto.CarPick;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CarizonMapper {
    List<CarPick> selectRepresentativeCars(@Param("limit") Integer limit);
    List<CarPick> selectRepresentativeCarsByModelCodes(@Param("modelCodes") List<String> modelCodes, @Param("limit") Integer limit);
    List<CarPick> selectRepresentativeCarsByMakers(@Param("makerCodes") List<String> makerCodes, @Param("limit") Integer limit);
}

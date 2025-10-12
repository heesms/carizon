package com.carizon.api.mapper;

import com.carizon.api.dto.CarMasterRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CarMasterMapper {
    CarMasterRow getMasterById(@Param("carId") Long carId);
    
    List<CarMasterRow> searchMaster(@Param("maker") String maker,
                                     @Param("modelGroup") String modelGroup,
                                     @Param("model") String model,
                                     @Param("trim") String trim,
                                     @Param("grade") String grade,
                                     @Param("year") Short year,
                                     @Param("region") String region,
                                     @Param("status") String status,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);
    
    int countMaster(@Param("maker") String maker,
                    @Param("modelGroup") String modelGroup,
                    @Param("model") String model,
                    @Param("trim") String trim,
                    @Param("grade") String grade,
                    @Param("year") Short year,
                    @Param("region") String region,
                    @Param("status") String status);
}

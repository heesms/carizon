package com.carizon.api.mapper;

import com.carizon.api.dto.CarListItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CarsMapper {
    List<CarListItem> searchCars(@Param("maker") String maker,
                                 @Param("modelGroup") String modelGroup,
                                 @Param("model") String model,
                                 @Param("trim") String trim,
                                 @Param("grade") String grade,
                                 @Param("q") String q,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);
    
    int countCars(@Param("maker") String maker,
                  @Param("modelGroup") String modelGroup,
                  @Param("model") String model,
                  @Param("trim") String trim,
                  @Param("grade") String grade,
                  @Param("q") String q);
}

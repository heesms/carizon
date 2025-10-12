package com.carizon.api.mapper;

import com.carizon.api.dto.CodeRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CodeMapper {
    List<CodeRow> getMakers();
    
    List<CodeRow> getModelGroups(@Param("maker") String maker);
    
    List<CodeRow> getModels(@Param("maker") String maker, 
                           @Param("modelGroup") String modelGroup);
    
    List<CodeRow> getTrims(@Param("maker") String maker, 
                          @Param("modelGroup") String modelGroup,
                          @Param("model") String model);
    
    List<CodeRow> getGrades(@Param("maker") String maker, 
                           @Param("modelGroup") String modelGroup,
                           @Param("model") String model,
                           @Param("trim") String trim);
}

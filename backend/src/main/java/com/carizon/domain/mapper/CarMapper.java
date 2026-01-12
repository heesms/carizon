package com.carizon.domain.mapper;
import com.carizon.dto.CarDetailRow;
import com.carizon.dto.CarListItemDto;
import com.carizon.dto.PricePoint;
import com.carizon.dto.ModelImageDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;
@Mapper
public interface CarMapper {
  List<CarListItemDto> selectCars(Map<String,Object> params);
  long countCars(Map<String,Object> params);
  List<CarDetailRow> selectCarDetail(@Param("carId") long carId);
  Long selectAnyPlatformCarId(@Param("carId") long carId);
  List<PricePoint> selectPriceHistory(@Param("platformCarId") long platformCarId);
  List<ModelImageDto> selectModelImages(@Param("modelCode") String modelCode);
  List<Map<String,Object>> selectMakers();
  List<Map<String,Object>> selectModelGroups(@Param("makerCode") String makerCode);
  List<Map<String,Object>> selectModels(@Param("makerCode") String makerCode, @Param("modelGroupCode") String modelGroupCode);
  List<Map<String,Object>> selectTrims(@Param("makerCode") String makerCode, @Param("modelGroupCode") String modelGroupCode, @Param("modelCode") String modelCode);
  List<Map<String,Object>> selectGrades(@Param("makerCode") String makerCode, @Param("modelGroupCode") String modelGroupCode, @Param("modelCode") String modelCode, @Param("trimCode") String trimCode);
}
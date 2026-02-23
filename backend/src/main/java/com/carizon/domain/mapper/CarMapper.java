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
  /** RAG/추천용: pc_url, m_url만 경량 조회 */
  Map<String, Object> selectCarUrl(@Param("carId") long carId);
  /** RAG 추천용: car_id 목록에 대한 model_code 매핑 (모델 컬렉션 매칭 보너스용) */
  List<Map<String, Object>> selectCarIdModelCodes(@Param("carIds") List<Long> carIds);
  /** RAG 추천용: 모델코드 목록에 해당하는 매물 후보 (스포츠카 등 쿼리 시 해당 모델 매물 확보) */
  List<Map<String, Object>> selectCarsByModelCodes(@Param("modelCodes") List<String> modelCodes, @Param("limit") int limit);
  String selectCarRepresentativeImageUrl(@Param("carId") long carId);
  Long selectAnyPlatformCarId(@Param("carId") long carId);
  List<PricePoint> selectPriceHistory(@Param("platformCarId") long platformCarId);
  List<ModelImageDto> selectModelImages(@Param("modelCode") String modelCode);
  List<Map<String,Object>> selectMakers();
  List<Map<String,Object>> selectModelGroups(@Param("makerCode") String makerCode);
  List<Map<String,Object>> selectModels(@Param("makerCode") String makerCode, @Param("modelGroupCode") String modelGroupCode);
  List<Map<String,Object>> selectTrims(@Param("makerCode") String makerCode, @Param("modelGroupCode") String modelGroupCode, @Param("modelCode") String modelCode);
  List<Map<String,Object>> selectGrades(@Param("makerCode") String makerCode, @Param("modelGroupCode") String modelGroupCode, @Param("modelCode") String modelCode, @Param("trimCode") String trimCode);
  List<Map<String,Object>> selectMakersWithCounts(@Param("filters") Map<String, Object> filters);
  List<Map<String,Object>> selectModelGroupsWithCounts(@Param("makerCode") String makerCode, @Param("filters") Map<String, Object> filters);
  List<Map<String,Object>> selectModelsWithCounts(@Param("makerCode") String makerCode, @Param("modelGroupCode") String modelGroupCode, @Param("filters") Map<String, Object> filters);
  List<Map<String,Object>> selectTrimsWithCounts(@Param("makerCode") String makerCode, @Param("modelGroupCode") String modelGroupCode, @Param("modelCode") String modelCode, @Param("filters") Map<String, Object> filters);
  List<Map<String,Object>> selectBodyTypesWithCounts(@Param("filters") Map<String, Object> filters);
  List<Map<String,Object>> selectFuelsWithCounts(@Param("filters") Map<String, Object> filters);
  List<Map<String,Object>> selectColorsWithCounts(@Param("filters") Map<String, Object> filters);
  List<Map<String,Object>> selectCarsForIndexing(Map<String,Object> params);
  /** Keyset pagination for reindex (faster than OFFSET). lastCarId=null for first page. */
  List<Map<String,Object>> selectCarsForIndexingAfterId(Map<String,Object> params);
  List<Map<String,Object>> selectCarsForIndexingUpdated(Map<String,Object> params);
  List<Map<String,Object>> selectCarsForIndexingById(Map<String,Object> params);
}

// backend/src/main/java/com/carizon/service/CarQueryService.java
package com.carizon.service;

import com.carizon.domain.mapper.CarMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 차량 검색/상세 조회 서비스.
 * - 요청 파라미터(Map<String, Object>)에서 page/size를 안전 파싱
 * - MyBatis Mapper로 전달할 파라미터에 offset/limit 추가
 * - 응답은 페이지 정보와 콘텐츠를 포함한 Map으로 반환
 *
 * NOTE:
 *  - Mapper 시그니처는 기존 것을 그대로 사용한다고 가정합니다.
 *    (selectCars(Map), countCars(Map), selectCarDetail(long))
 *  - DTO 타입이 무엇이든 컴파일 되도록 List<?>로 받아 Map에 담아 반환합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CarQueryService {

  private final CarMapper mapper;

  /**
   * 차량 목록 검색 + 페이지 정보.
   * 쿼리 파라미터 예시:
   *  - page (0-base), size
   *  - maker, modelGroup, model, trim, grade ...
   *  - yearFrom, yearTo, priceFrom, priceTo, mileageFrom, mileageTo ...
   */
  public Map<String, Object> search(Map<String, Object> query) {
    // 1) page/size 안전 파싱 및 보정
    int page = parseInt(query.get("page"), 0);
    int size = parseInt(query.get("size"), 20);
    if (page < 0) page = 0;
    if (size <= 0 || size > 200) size = 20;

    int offset = page * size;

    // 2) Mapper로 넘길 파라미터 구성 (원본 쿼리 복사 + 페이징 파라미터 주입)
    Map<String, Object> param = new HashMap<>();
    if (query != null) {
      param.putAll(query);
    }
    // SQL 호환성을 위해 여러 키를 함께 넣어둠 (각자의 XML에서 필요한 키 사용)
    param.put("page", page);
    param.put("size", size);
    param.put("limit", size);
    param.put("offset", offset);

    // 3) 조회
    List<?> content = mapper.selectCars(param);
    long total = mapper.countCars(param);

    // 4) 페이지 메타
    int totalPages = (int) Math.ceil(total / (double) Math.max(1, size));

    Map<String, Object> res = new LinkedHashMap<>();
    res.put("content", content);
    res.put("page", page);
    res.put("size", size);
    res.put("totalElements", total);
    res.put("totalPages", totalPages);
    return res;
  }

  /**
   * 차량 상세 조회.
   */
  public Map<String, Object> detail(long carId) {
    List<?> rows = mapper.selectCarDetail(carId);

    Map<String, Object> res = new LinkedHashMap<>();
    res.put("carId", carId);
    res.put("content", rows);
    return res;
  }

  // -------- utils --------

  private static int parseInt(Object v, int def) {
    if (v == null) return def;
    try {
      if (v instanceof Number) return ((Number) v).intValue();
      String s = String.valueOf(v).trim();
      if (s.isEmpty()) return def;
      return Integer.parseInt(s);
    } catch (Exception ignored) {
      return def;
    }
  }
}

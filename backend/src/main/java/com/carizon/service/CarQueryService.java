// backend/src/main/java/com/carizon/service/CarQueryService.java
package com.carizon.service;

import com.carizon.domain.mapper.CarMapper;
import com.carizon.search.service.MeilisearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 차량 검색/상세 조회 서비스.
 * - Meilisearch를 사용한 빠른 검색
 * - 상세 조회는 기존 MyBatis 사용
 *
 * NOTE:
 *  - 검색은 Meilisearch를 사용하여 성능 향상
 *  - 상세 조회는 기존 Mapper 사용
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CarQueryService {

  private final CarMapper mapper;
  private final MeilisearchService meilisearchService;

  /**
   * 차량 목록 검색 + 페이지 정보.
   * Meilisearch를 사용하여 빠른 검색 수행
   * 쿼리 파라미터 예시:
   *  - page (0-base), size
   *  - makerCode, modelGroupCode, modelCode, trimCode, gradeCode
   *  - yearMin, yearMax, priceMin, priceMax, kmMax
   *  - fuel, transmission, bodyType, region
   *  - sort: LOW_PRICE, LOW_KM, NEW_YEAR (기본: 최신순)
   */
  public Map<String, Object> search(Map<String, Object> query) {
    log.debug("[CarQueryService] search 시작: query={}", query);
    
    try {
      // 파라미터 정규화 (yearFrom -> yearMin 등)
      Map<String, Object> normalizedQuery = normalizeQueryParams(query);
      
      // Meilisearch로 검색
      long startTime = System.currentTimeMillis();
      Map<String, Object> result = meilisearchService.search(normalizedQuery);
      long elapsed = System.currentTimeMillis() - startTime;
      
      log.info("[CarQueryService] Meilisearch 검색 완료: {}ms, totalElements={}", 
          elapsed, result.get("totalElements"));
      
      return result;
    } catch (Exception e) {
      log.error("[CarQueryService] search 오류: query={}", query, e);
      // Meilisearch 실패 시 기존 MyBatis로 폴백 (선택사항)
      // return fallbackSearch(query);
      throw e;
    }
  }

  /**
   * 쿼리 파라미터 정규화
   */
  private Map<String, Object> normalizeQueryParams(Map<String, Object> query) {
    Map<String, Object> normalized = new HashMap<>();
    if (query != null) {
      normalized.putAll(query);
      
      // yearFrom -> yearMin
      if (query.containsKey("yearFrom") && !query.containsKey("yearMin")) {
        normalized.put("yearMin", query.get("yearFrom"));
      }
      // yearTo -> yearMax
      if (query.containsKey("yearTo") && !query.containsKey("yearMax")) {
        normalized.put("yearMax", query.get("yearTo"));
      }
      // priceFrom -> priceMin
      if (query.containsKey("priceFrom") && !query.containsKey("priceMin")) {
        normalized.put("priceMin", query.get("priceFrom"));
      }
      // priceTo -> priceMax
      if (query.containsKey("priceTo") && !query.containsKey("priceMax")) {
        normalized.put("priceMax", query.get("priceTo"));
      }
      // mileageFrom -> kmMin (필요시)
      if (query.containsKey("mileageFrom") && !query.containsKey("kmMin")) {
        normalized.put("kmMin", query.get("mileageFrom"));
      }
      // mileageTo -> kmMax
      if (query.containsKey("mileageTo") && !query.containsKey("kmMax")) {
        normalized.put("kmMax", query.get("mileageTo"));
      }
    }
    return normalized;
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

package com.carizon.search.service;

import com.carizon.dto.CarListItemDto;
import com.carizon.search.config.MeilisearchProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.model.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Meilisearch 검색 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeilisearchService {

    private final Client meilisearchClient;
    private final MeilisearchProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 차량 검색
     */
    public Map<String, Object> search(Map<String, Object> queryParams) {
        try {
            if (meilisearchClient == null) {
                log.warn("[Meilisearch] 클라이언트가 초기화되지 않았습니다. 빈 결과를 반환합니다.");
                return createEmptyResult(queryParams);
            }
            var index = meilisearchClient.index(properties.getIndexName());
            
            // 페이징 파라미터
            int page = parseInt(queryParams.get("page"), 0);
            int size = parseInt(queryParams.get("size"), 20);
            if (page < 0) page = 0;
            if (size <= 0 || size > 200) size = 20;
            int offset = page * size;
            
            // 검색 쿼리 구성
            String q = buildSearchQuery(queryParams);
            
            // 필터 구성
            String filter = buildFilter(queryParams);
            
            // 정렬 구성
            String[] sort = buildSort(queryParams);
            
            log.debug("[Meilisearch] 검색 요청: q={}, filter={}, sort={}, offset={}, limit={}", 
                q, filter, Arrays.toString(sort), offset, size);
            
            // Meilisearch 검색 실행
            // Meilisearch Java SDK는 간단한 검색만 지원하므로, 
            // 필터와 정렬은 검색 후 Java에서 처리하거나, 
            // 또는 Meilisearch HTTP API를 직접 호출해야 할 수 있습니다.
            // 여기서는 간단한 검색만 수행하고, 필터링은 결과에서 처리합니다.
            String searchQuery = q != null && !q.isEmpty() ? q : "";
            
            // 기본 검색 (필터와 정렬은 나중에 추가)
            SearchResult searchResult = index.search(searchQuery);
            
            // 결과 변환
            @SuppressWarnings("unchecked")
            List<HashMap<String, Object>> hitsList = (List<HashMap<String, Object>>) searchResult.getHits();
            List<Map<String, Object>> hits = new ArrayList<>(hitsList);
            
            // 필터링 적용 (Java에서)
            hits = applyFilters(hits, queryParams);
            
            // 정렬 적용 (Java에서)
            hits = applySort(hits, queryParams);
            
            // 페이징 적용
            int fromIndex = Math.min(offset, hits.size());
            int toIndex = Math.min(offset + size, hits.size());
            List<Map<String, Object>> pagedHits = hits.subList(fromIndex, toIndex);
            
            List<CarListItemDto> content = convertToCarListItems(pagedHits);
            long total = hits.size();
            
            int totalPages = (int) Math.ceil(total / (double) Math.max(1, size));
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", content);
            result.put("page", page);
            result.put("size", size);
            result.put("totalElements", total);
            result.put("totalPages", totalPages);
            
            log.debug("[Meilisearch] 검색 완료: totalElements={}, totalPages={}", total, totalPages);
            return result;
            
        } catch (Exception e) {
            log.error("[Meilisearch] 검색 오류: queryParams={}. 빈 결과를 반환합니다.", queryParams, e);
            // 예외를 던지지 않고 빈 결과 반환 (서버는 계속 실행)
            return createEmptyResult(queryParams);
        }
    }

    /**
     * 빈 검색 결과 생성
     */
    private Map<String, Object> createEmptyResult(Map<String, Object> queryParams) {
        int page = parseInt(queryParams.get("page"), 0);
        int size = parseInt(queryParams.get("size"), 20);
        if (page < 0) page = 0;
        if (size <= 0 || size > 200) size = 20;
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", new ArrayList<>());
        result.put("page", page);
        result.put("size", size);
        result.put("totalElements", 0L);
        result.put("totalPages", 0);
        return result;
    }

    /**
     * 검색 쿼리 문자열 생성 (텍스트 검색용)
     */
    private String buildSearchQuery(Map<String, Object> params) {
        // 텍스트 검색 파라미터가 있으면 사용
        if (params.containsKey("q") && params.get("q") != null) {
            return String.valueOf(params.get("q"));
        }
        // makerName, modelName 등으로 검색 가능
        List<String> searchTerms = new ArrayList<>();
        if (params.containsKey("makerName") && params.get("makerName") != null) {
            searchTerms.add(String.valueOf(params.get("makerName")));
        }
        if (params.containsKey("modelName") && params.get("modelName") != null) {
            searchTerms.add(String.valueOf(params.get("modelName")));
        }
        return searchTerms.isEmpty() ? null : String.join(" ", searchTerms);
    }

    /**
     * 필터 문자열 생성
     */
    private String buildFilter(Map<String, Object> params) {
        List<String> filters = new ArrayList<>();
        
        if (params.get("makerCode") != null) {
            filters.add("makerCode = " + escapeValue(params.get("makerCode")));
        }
        if (params.get("modelGroupCode") != null) {
            filters.add("modelGroupCode = " + escapeValue(params.get("modelGroupCode")));
        }
        if (params.get("modelCode") != null) {
            filters.add("modelCode = " + escapeValue(params.get("modelCode")));
        }
        if (params.get("trimCode") != null) {
            filters.add("trimCode = " + escapeValue(params.get("trimCode")));
        }
        if (params.get("gradeCode") != null) {
            filters.add("gradeCode = " + escapeValue(params.get("gradeCode")));
        }
        if (params.get("yearMin") != null) {
            filters.add("year >= " + params.get("yearMin"));
        }
        if (params.get("yearMax") != null) {
            filters.add("year <= " + params.get("yearMax"));
        }
        if (params.get("kmMax") != null) {
            filters.add("km <= " + params.get("kmMax"));
        }
        if (params.get("priceMin") != null) {
            filters.add("priceMin >= " + params.get("priceMin"));
        }
        if (params.get("priceMax") != null) {
            filters.add("priceMax <= " + params.get("priceMax"));
        }
        if (params.get("fuel") != null) {
            filters.add("fuel = " + escapeValue(params.get("fuel")));
        }
        if (params.get("transmission") != null) {
            filters.add("transmission = " + escapeValue(params.get("transmission")));
        }
        if (params.get("bodyType") != null) {
            filters.add("bodyType = " + escapeValue(params.get("bodyType")));
        }
        if (params.get("region") != null) {
            filters.add("region = " + escapeValue(params.get("region")));
        }
        
        return filters.isEmpty() ? null : String.join(" AND ", filters);
    }

    /**
     * 정렬 배열 생성
     */
    private String[] buildSort(Map<String, Object> params) {
        String sortParam = params.containsKey("sort") ? String.valueOf(params.get("sort")) : null;
        
        if ("LOW_PRICE".equals(sortParam)) {
            return new String[]{"priceMin:asc"};
        } else if ("LOW_KM".equals(sortParam)) {
            return new String[]{"km:asc"};
        } else if ("NEW_YEAR".equals(sortParam)) {
            return new String[]{"year:desc"};
        } else {
            return new String[]{"priceUpdatedAt:desc"};
        }
    }

    /**
     * 검색 결과를 CarListItemDto 리스트로 변환
     */
    private List<CarListItemDto> convertToCarListItems(List<Map<String, Object>> hits) {
        List<CarListItemDto> result = new ArrayList<>();
        for (Map<String, Object> hit : hits) {
            try {
                CarListItemDto dto = convertToCarListItem(hit);
                if (dto != null) {
                    result.add(dto);
                }
            } catch (Exception e) {
                log.warn("[Meilisearch] 결과 변환 실패: {}", e.getMessage());
            }
        }
        return result;
    }

    /**
     * 단일 검색 결과를 CarListItemDto로 변환
     */
    private CarListItemDto convertToCarListItem(Map<String, Object> hit) {
        try {
            long carId = parseLong(hit.get("carId"), 0);
            String maker = getString(hit, "makerName");
            String model = getString(hit, "modelName");
            String trim = getString(hit, "trimName");
            Integer year = getInteger(hit, "year");
            Integer km = getInteger(hit, "km");
            Integer priceMin = getInteger(hit, "priceMin");
            Integer priceMax = getInteger(hit, "priceMax");
            String priceUpdatedAtStr = getString(hit, "priceUpdatedAt");
            java.time.LocalDateTime priceUpdatedAt = parseDateTime(priceUpdatedAtStr);
            String representativeImageUrl = getString(hit, "representativeImageUrl");
            String modelCode = getString(hit, "modelCode");
            
            return new CarListItemDto(
                carId, maker, model, trim, year, km,
                priceMin, priceMax, priceUpdatedAt,
                representativeImageUrl, modelCode
            );
        } catch (Exception e) {
            log.warn("[Meilisearch] DTO 변환 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 문서 인덱싱 (단일)
     */
    public void indexCar(Map<String, Object> carData) {
        try {
            if (meilisearchClient == null) {
                log.warn("[Meilisearch] 클라이언트가 초기화되지 않았습니다. 인덱싱을 건너뜁니다.");
                return;
            }
            var index = meilisearchClient.index(properties.getIndexName());
            String json = objectMapper.writeValueAsString(Collections.singletonList(carData));
            index.addDocuments(json);
            log.debug("[Meilisearch] 차량 인덱싱 완료: carId={}", carData.get("carId"));
        } catch (Exception e) {
            log.error("[Meilisearch] 차량 인덱싱 실패: carId={}", carData.get("carId"), e);
        }
    }

    /**
     * 문서 일괄 인덱싱
     */
    public void indexCars(List<Map<String, Object>> carsData) {
        if (carsData == null || carsData.isEmpty()) {
            return;
        }
        try {
            if (meilisearchClient == null) {
                log.warn("[Meilisearch] 클라이언트가 초기화되지 않았습니다. 인덱싱을 건너뜁니다.");
                return;
            }
            var index = meilisearchClient.index(properties.getIndexName());
            String json = objectMapper.writeValueAsString(carsData);
            index.addDocuments(json);
            log.info("[Meilisearch] 차량 일괄 인덱싱 완료: {}건", carsData.size());
        } catch (Exception e) {
            log.error("[Meilisearch] 차량 일괄 인덱싱 실패: {}건", carsData.size(), e);
        }
    }

    /**
     * 인덱스 전체 삭제 후 재인덱싱
     */
    public void reindexAll(List<Map<String, Object>> carsData) {
        try {
            if (meilisearchClient == null) {
                log.warn("[Meilisearch] 클라이언트가 초기화되지 않았습니다. 재인덱싱을 건너뜁니다.");
                return;
            }
            var index = meilisearchClient.index(properties.getIndexName());
            index.deleteAllDocuments();
            if (carsData != null && !carsData.isEmpty()) {
                String json = objectMapper.writeValueAsString(carsData);
                index.addDocuments(json);
                log.info("[Meilisearch] 전체 재인덱싱 완료: {}건", carsData.size());
            }
        } catch (Exception e) {
            log.error("[Meilisearch] 전체 재인덱싱 실패. 서버는 계속 실행됩니다.", e);
            // 예외를 던지지 않고 로그만 남김 (서버는 계속 실행)
        }
    }

    // -------- 유틸리티 메서드 --------

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

    private static long parseLong(Object v, long def) {
        if (v == null) return def;
        try {
            if (v instanceof Number) return ((Number) v).longValue();
            String s = String.valueOf(v).trim();
            if (s.isEmpty()) return def;
            return Long.parseLong(s);
        } catch (Exception ignored) {
            return def;
        }
    }

    private static Integer getInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private static String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private static java.time.LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        try {
            // ISO 8601 형식 파싱
            return java.time.LocalDateTime.parse(dateTimeStr.replace(" ", "T"));
        } catch (Exception e) {
            try {
                // 다른 형식 시도
                return java.time.LocalDateTime.parse(dateTimeStr, 
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private static String escapeValue(Object value) {
        if (value == null) return "null";
        String str = String.valueOf(value);
        // 문자열 값은 따옴표로 감싸기
        if (str.matches("^\\d+$")) {
            return str; // 숫자는 그대로
        }
        return "\"" + str.replace("\"", "\\\"") + "\"";
    }

    /**
     * Java에서 필터링 적용
     */
    private List<Map<String, Object>> applyFilters(List<Map<String, Object>> hits, Map<String, Object> params) {
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> hit : hits) {
            if (matchesFilters(hit, params)) {
                filtered.add(hit);
            }
        }
        return filtered;
    }

    /**
     * 필터 조건 확인
     */
    private boolean matchesFilters(Map<String, Object> hit, Map<String, Object> params) {
        if (params.get("makerCode") != null) {
            if (!String.valueOf(params.get("makerCode")).equals(getString(hit, "makerCode"))) {
                return false;
            }
        }
        if (params.get("modelGroupCode") != null) {
            if (!String.valueOf(params.get("modelGroupCode")).equals(getString(hit, "modelGroupCode"))) {
                return false;
            }
        }
        if (params.get("modelCode") != null) {
            if (!String.valueOf(params.get("modelCode")).equals(getString(hit, "modelCode"))) {
                return false;
            }
        }
        if (params.get("trimCode") != null) {
            if (!String.valueOf(params.get("trimCode")).equals(getString(hit, "trimCode"))) {
                return false;
            }
        }
        if (params.get("gradeCode") != null) {
            if (!String.valueOf(params.get("gradeCode")).equals(getString(hit, "gradeCode"))) {
                return false;
            }
        }
        Integer year = getInteger(hit, "year");
        if (params.get("yearMin") != null && year != null) {
            if (year < parseInt(params.get("yearMin"), 0)) {
                return false;
            }
        }
        if (params.get("yearMax") != null && year != null) {
            if (year > parseInt(params.get("yearMax"), Integer.MAX_VALUE)) {
                return false;
            }
        }
        Integer km = getInteger(hit, "km");
        if (params.get("kmMax") != null && km != null) {
            if (km > parseInt(params.get("kmMax"), Integer.MAX_VALUE)) {
                return false;
            }
        }
        Integer priceMin = getInteger(hit, "priceMin");
        if (params.get("priceMin") != null && priceMin != null) {
            if (priceMin < parseInt(params.get("priceMin"), 0)) {
                return false;
            }
        }
        Integer priceMax = getInteger(hit, "priceMax");
        if (params.get("priceMax") != null && priceMax != null) {
            if (priceMax > parseInt(params.get("priceMax"), Integer.MAX_VALUE)) {
                return false;
            }
        }
        if (params.get("fuel") != null) {
            if (!String.valueOf(params.get("fuel")).equals(getString(hit, "fuel"))) {
                return false;
            }
        }
        if (params.get("transmission") != null) {
            if (!String.valueOf(params.get("transmission")).equals(getString(hit, "transmission"))) {
                return false;
            }
        }
        if (params.get("bodyType") != null) {
            if (!String.valueOf(params.get("bodyType")).equals(getString(hit, "bodyType"))) {
                return false;
            }
        }
        if (params.get("region") != null) {
            if (!String.valueOf(params.get("region")).equals(getString(hit, "region"))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Java에서 정렬 적용
     */
    private List<Map<String, Object>> applySort(List<Map<String, Object>> hits, Map<String, Object> params) {
        String sortParam = params.containsKey("sort") ? String.valueOf(params.get("sort")) : null;
        
        hits.sort((a, b) -> {
            if ("LOW_PRICE".equals(sortParam)) {
                Integer priceA = getInteger(a, "priceMin");
                Integer priceB = getInteger(b, "priceMin");
                if (priceA == null && priceB == null) return 0;
                if (priceA == null) return 1;
                if (priceB == null) return -1;
                return priceA.compareTo(priceB);
            } else if ("LOW_KM".equals(sortParam)) {
                Integer kmA = getInteger(a, "km");
                Integer kmB = getInteger(b, "km");
                if (kmA == null && kmB == null) return 0;
                if (kmA == null) return 1;
                if (kmB == null) return -1;
                return kmA.compareTo(kmB);
            } else if ("NEW_YEAR".equals(sortParam)) {
                Integer yearA = getInteger(a, "year");
                Integer yearB = getInteger(b, "year");
                if (yearA == null && yearB == null) return 0;
                if (yearA == null) return 1;
                if (yearB == null) return -1;
                return yearB.compareTo(yearA); // 내림차순
            } else {
                // 기본: priceUpdatedAt DESC
                String dateA = getString(a, "priceUpdatedAt");
                String dateB = getString(b, "priceUpdatedAt");
                if (dateA == null && dateB == null) return 0;
                if (dateA == null) return 1;
                if (dateB == null) return -1;
                return dateB.compareTo(dateA); // 내림차순
            }
        });
        
        return hits;
    }
}

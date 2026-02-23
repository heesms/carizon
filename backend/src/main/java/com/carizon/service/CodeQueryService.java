package com.carizon.service;

import com.carizon.domain.mapper.CarMapper;
import com.carizon.search.service.ElasticsearchCarSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CodeQueryService {
  private final CarMapper mapper;
  private final ElasticsearchCarSearchService searchService;

  public CodeQueryService(CarMapper mapper, ElasticsearchCarSearchService searchService) {
    this.mapper = mapper;
    this.searchService = searchService;
  }

  public List<Map<String, Object>> makers() { return mapper.selectMakers(); }

  public List<Map<String, Object>> makers(Map<String, Object> filters) {
    Map<String, Object> normalized = normalize(filters);
    List<Map<String, Object>> dbRows = mapper.selectMakers();
    try {
      Map<String, Long> counts = searchService.countTermsByField(normalized, "makerCode");
      return enrichWithCounts(dbRows, counts);
    } catch (Exception e) {
      log.warn("makers count from elasticsearch failed, fallback to db count", e);
      return sortByCountThenName(mapper.selectMakersWithCounts(normalized));
    }
  }

  public List<Map<String, Object>> modelGroups(String makerCode) {
    return mapper.selectModelGroups(makerCode);
  }

  public List<Map<String, Object>> modelGroups(String makerCode, Map<String, Object> filters) {
    Map<String, Object> normalized = normalize(filters);
    normalized.put("makerCode", makerCode);
    List<Map<String, Object>> dbRows = mapper.selectModelGroups(makerCode);
    try {
      Map<String, Long> counts = searchService.countTermsByField(normalized, "modelGroupCode");
      return enrichWithCounts(dbRows, counts);
    } catch (Exception e) {
      log.warn("modelGroups count from elasticsearch failed, fallback to db count: makerCode={}", makerCode, e);
      return sortByCountThenName(mapper.selectModelGroupsWithCounts(makerCode, normalized));
    }
  }

  public List<Map<String, Object>> models(String makerCode, String modelGroupCode) {
    return mapper.selectModels(makerCode, modelGroupCode);
  }

  public List<Map<String, Object>> models(String makerCode, String modelGroupCode, Map<String, Object> filters) {
    Map<String, Object> normalized = normalize(filters);
    normalized.put("makerCode", makerCode);
    normalized.put("modelGroupCode", modelGroupCode);
    List<Map<String, Object>> dbRows = mapper.selectModels(makerCode, modelGroupCode);
    try {
      Map<String, Long> counts = searchService.countTermsByField(normalized, "modelCode");
      return enrichWithCounts(dbRows, counts);
    } catch (Exception e) {
      log.warn("models count from elasticsearch failed, fallback to db count: makerCode={}, modelGroupCode={}", makerCode, modelGroupCode, e);
      return sortByCountThenName(mapper.selectModelsWithCounts(makerCode, modelGroupCode, normalized));
    }
  }

  public List<Map<String, Object>> trims(String makerCode, String modelGroupCode, String modelCode) {
    return mapper.selectTrims(makerCode, modelGroupCode, modelCode);
  }

  public List<Map<String, Object>> trims(String makerCode, String modelGroupCode, String modelCode, Map<String, Object> filters) {
    Map<String, Object> normalized = normalize(filters);
    normalized.put("makerCode", makerCode);
    normalized.put("modelGroupCode", modelGroupCode);
    normalized.put("modelCode", modelCode);
    List<Map<String, Object>> dbRows = mapper.selectTrims(makerCode, modelGroupCode, modelCode);
    try {
      Map<String, Long> counts = searchService.countTermsByField(normalized, "trimCode");
      return enrichWithCounts(dbRows, counts);
    } catch (Exception e) {
      log.warn("trims count from elasticsearch failed, fallback to db count: makerCode={}, modelGroupCode={}, modelCode={}", makerCode, modelGroupCode, modelCode, e);
      return sortByCountThenName(mapper.selectTrimsWithCounts(makerCode, modelGroupCode, modelCode, normalized));
    }
  }

  public List<Map<String, Object>> grades(String makerCode, String modelGroupCode, String modelCode, String trimCode) {
    return mapper.selectGrades(makerCode, modelGroupCode, modelCode, trimCode);
  }

  public List<Map<String, Object>> bodyTypes(Map<String, Object> filters) {
    Map<String, Object> normalized = normalize(filters);
    List<Map<String, Object>> dbRows = mapper.selectBodyTypesWithCounts(normalized);
    try {
      Map<String, Long> counts = searchService.countTermsByField(normalized, "bodyType");
      return enrichWithCounts(dbRows, counts);
    } catch (Exception e) {
      log.warn("bodyTypes count from elasticsearch failed, fallback to db count", e);
      return sortByCountThenName(dbRows);
    }
  }

  public List<Map<String, Object>> fuels(Map<String, Object> filters) {
    Map<String, Object> normalized = normalize(filters);
    List<Map<String, Object>> dbRows = mapper.selectFuelsWithCounts(normalized);
    try {
      Map<String, Long> counts = searchService.countTermsByField(normalized, "fuel");
      return enrichWithCounts(dbRows, counts);
    } catch (Exception e) {
      log.warn("fuels count from elasticsearch failed, fallback to db count", e);
      return sortByCountThenName(dbRows);
    }
  }

  private List<Map<String, Object>> enrichWithCounts(List<Map<String, Object>> source, Map<String, Long> counts) {
    List<Map<String, Object>> rows = source.stream()
        .map(item -> {
          Map<String, Object> row = new LinkedHashMap<>(item);
          String code = asString(item.get("code"));
          row.put("carCount", counts.getOrDefault(code, 0L));
          return row;
        })
        .collect(Collectors.toList());
    return sortByCountThenName(rows);
  }

  private List<Map<String, Object>> sortByCountThenName(List<Map<String, Object>> rows) {
    rows.sort((a, b) -> {
      long ca = parseCount(a.get("carCount"));
      long cb = parseCount(b.get("carCount"));
      int countDiff = Long.compare(cb, ca);
      if (countDiff != 0) return countDiff;
      String na = asString(a.get("name"));
      String nb = asString(b.get("name"));
      return na.compareToIgnoreCase(nb);
    });
    return rows;
  }

  private Map<String, Object> normalize(Map<String, Object> source) {
    return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
  }

  private String asString(Object v) {
    return v == null ? "" : String.valueOf(v);
  }

  private long parseCount(Object value) {
    if (value == null) return 0;
    if (value instanceof Number) return ((Number) value).longValue();
    try {
      return Long.parseLong(String.valueOf(value).replace(",", ""));
    } catch (Exception e) {
      return 0;
    }
  }
}

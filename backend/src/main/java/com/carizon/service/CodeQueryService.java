package com.carizon.service;

import com.carizon.domain.mapper.CarMapper;
import com.carizon.search.service.ElasticsearchCarSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CodeQueryService {
  private final CarMapper mapper;
  private final ElasticsearchCarSearchService searchService;

  private static final int FUEL_OTHER_THRESHOLD = 50;
  private static final String OTHER_LABEL = "기타";
  private static final String BODY_ORDER_OTHER = "기타";
  private static final List<String> BODY_TYPE_ORDER = List.of(
      "경차",
      "소형",
      "준중형",
      "중형",
      "대형",
      "스포츠카",
      "RV",
      "SUV",
      "승합",
      "버스",
      "화물",
      BODY_ORDER_OTHER
  );
  private static final int BODY_TYPE_OTHER_INDEX = BODY_TYPE_ORDER.size() - 1;

  private static final List<String> LPG_NORMALIZED = List.of("LPG(일반인)", "LPG(일반인 구입)");
  private static final List<String> ELECTRIC_NORMALIZED = List.of("전기", "EV", "전기(EV)", "전기 EV");

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
    Set<String> selectedBodyTypes = parseFilterSet(normalized.get("bodyType"), this::normalizeBodyTypeForDisplay);
    try {
      Map<String, Long> counts = searchService.countTermsByField(normalized, "bodyType");
      return applyBodyTypeBuckets(counts, selectedBodyTypes);
    } catch (Exception e) {
      log.warn("bodyTypes count from elasticsearch failed, fallback to db count", e);
      return sortBodyTypesByPriority(mapper.selectBodyTypesWithCounts(normalized));
    }
  }

  public List<Map<String, Object>> fuels(Map<String, Object> filters) {
    Map<String, Object> normalized = normalize(filters);
    Set<String> selectedFuels = parseFilterSet(normalized.get("fuel"), this::normalizeFuelForDisplay);
    try {
      Map<String, Long> counts = searchService.countTermsByField(normalized, "fuel");
      return applyFuelBuckets(counts, selectedFuels);
    } catch (Exception e) {
      log.warn("fuels count from elasticsearch failed, fallback to db count", e);
      Map<String, Long> counts = mapper.selectFuelsWithCounts(normalized)
          .stream()
          .collect(Collectors.toMap(
              row -> asString(row.get("code")),
              row -> parseCount(row.get("carCount")),
              Long::sum,
              LinkedHashMap::new
          ));
      return applyFuelBuckets(counts, selectedFuels);
    }
  }

  public List<Map<String, Object>> colors(Map<String, Object> filters) {
    Map<String, Object> normalized = normalize(filters);
    List<Map<String, Object>> dbRows = mapper.selectColorsWithCounts(normalized);
    try {
      Map<String, Long> counts = searchService.countTermsByField(normalized, "color");
      return enrichWithCounts(dbRows, counts);
    } catch (Exception e) {
      log.warn("colors count from elasticsearch failed, fallback to db count", e);
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

  private List<Map<String, Object>> applyBodyTypeBuckets(Map<String, Long> rawCounts, Set<String> selectedBodyTypes) {
    Map<String, Long> normalized = new LinkedHashMap<>();
    rawCounts.forEach((rawCode, count) -> {
      String normalizedCode = normalizeBodyTypeForDisplay(rawCode);
      if (normalizedCode == null || normalizedCode.isEmpty()) return;
      normalized.merge(normalizedCode, count, Long::sum);
    });

    List<Map<String, Object>> rows = new ArrayList<>();
    Set<String> used = new LinkedHashSet<>();

    for (String orderedCode : BODY_TYPE_ORDER) {
      Long count = normalized.remove(orderedCode);
      used.add(orderedCode);
      if ((count != null && count > 0) || selectedBodyTypes.contains(orderedCode)) {
        rows.add(buildCodeItem(orderedCode, count == null ? 0L : count));
      }
    }

    long etc = 0L;
    for (Map.Entry<String, Long> e : normalized.entrySet()) {
      etc += e.getValue() == null ? 0L : e.getValue();
      used.add(e.getKey());
    }
    if (etc > 0 || selectedBodyTypes.contains(BODY_ORDER_OTHER)) {
      rows.add(buildCodeItem(BODY_ORDER_OTHER, etc));
    }

    return rows.stream()
        .sorted(Comparator
            .comparingInt((Map<String, Object> row) -> {
              String code = asString(row.get("code"));
              int idx = BODY_TYPE_ORDER.indexOf(code);
              return idx >= 0 ? idx : BODY_TYPE_OTHER_INDEX;
            })
            .thenComparingLong(r -> -parseCount(r.get("carCount"))))
        .collect(Collectors.toList());
  }

  private List<Map<String, Object>> applyFuelBuckets(Map<String, Long> rawCounts, Set<String> selectedFuels) {
    Map<String, Long> normalized = new LinkedHashMap<>();
    rawCounts.forEach((rawCode, count) -> {
      String normalizedCode = normalizeFuelForDisplay(rawCode);
      if (normalizedCode == null || normalizedCode.isEmpty()) return;
      normalized.merge(normalizedCode, count, Long::sum);
    });

    long other = 0L;
    List<Map<String, Object>> rows = new ArrayList<>();
    for (Map.Entry<String, Long> e : normalized.entrySet()) {
      String code = e.getKey();
      long count = e.getValue() == null ? 0L : e.getValue();
      if (OTHER_LABEL.equals(code) || (count < FUEL_OTHER_THRESHOLD && !selectedFuels.contains(code))) {
        other += count;
        continue;
      }
      rows.add(buildCodeItem(code, count));
    }

    selectedFuels.stream()
        .filter(f -> !normalized.containsKey(f))
        .filter(f -> rows.stream().noneMatch(r -> f.equals(asString(r.get("code"))))
        .forEach(f -> rows.add(buildCodeItem(f, 0L)));

    rows = sortByCountThenName(rows);

    if (other > 0) {
      rows.add(buildCodeItem(OTHER_LABEL, other));
    }
    return rows;
  }

  private Map<String, Object> buildCodeItem(String code, long carCount) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("code", code);
    row.put("name", code);
    row.put("carCount", carCount);
    return row;
  }

  private List<Map<String, Object>> sortBodyTypesByPriority(List<Map<String, Object>> rows) {
    return rows.stream()
        .filter(Objects::nonNull)
        .sorted(Comparator
            .comparingInt((Map<String, Object> row) -> {
              String code = asString(row.get("code"));
              int idx = BODY_TYPE_ORDER.indexOf(code);
              return idx >= 0 ? idx : BODY_TYPE_OTHER_INDEX;
            })
            .thenComparing((Map<String, Object> a, Map<String, Object> b) -> {
              int ca = parseCount(a.get("carCount"));
              int cb = parseCount(b.get("carCount"));
              return Integer.compare(cb, ca);
            })
            .thenComparing(a -> asString(a.get("name")), String::compareTo))
        .collect(Collectors.toList());
  }

  private String normalizeFuelForDisplay(Object raw) {
    String value = asString(raw);
    if (value == null || value.isBlank()) return "";
    String normalized = value.trim();
    String upper = normalized.toUpperCase(Locale.ROOT);
    if (upper.contains("LPG") && upper.contains("일반인")) return LPG_NORMALIZED.get(0);
    if (upper.equals("EV") || upper.contains("전기")) return ELECTRIC_NORMALIZED.get(0);
    return normalized;
  }

  private String normalizeBodyTypeForDisplay(Object raw) {
    String value = asString(raw);
    if (value == null || value.isBlank()) return "";
    String normalized = value.replaceAll("\\s+", "");
    if (normalized.contains("경차")) return "경차";
    if (normalized.contains("소형")) return "소형";
    if (normalized.contains("준중형")) return "준중형";
    if (normalized.contains("중형")) return "중형";
    if (normalized.contains("대형")) return "대형";
    if (normalized.contains("스포츠카")) return "스포츠카";
    if (normalized.contains("RV")) return "RV";
    if (normalized.equalsIgnoreCase("SUV") || normalized.toUpperCase(Locale.ROOT).contains("SUV")) return "SUV";
    if (normalized.contains("승합")) return "승합";
    if (normalized.contains("버스")) return "버스";
    if (normalized.contains("트럭") || normalized.contains("화물") || normalized.contains("상용")) return "화물";
    return BODY_ORDER_OTHER;
  }

  private Set<String> parseFilterSet(Object value) {
    return parseFilterSet(value, null);
  }

  private Set<String> parseFilterSet(Object value, Function<String, String> normalizer) {
    if (value == null) return Set.of();
    return Arrays.stream(String.valueOf(value).split(","))
        .map(String::trim)
        .filter(v -> !v.isEmpty())
        .map(v -> normalizer == null ? v : normalizer.apply(v))
        .filter(v -> v != null && !v.isEmpty())
        .collect(Collectors.toCollection(LinkedHashSet::new));
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
    if (source == null) return new LinkedHashMap<>();
    Map<String, Object> normalized = new LinkedHashMap<>(source);
    normalized.put("fuel", normalizeFuelFiltersForQuery(normalized.get("fuel")));
    normalized.put("bodyType", normalizeBodyTypeFiltersForQuery(normalized.get("bodyType")));
    if (normalized.containsKey("color")) {
      String color = normalizeFilterValueList(normalized.get("color"));
      if (color == null || color.isBlank()) normalized.remove("color");
      else normalized.put("color", color);
    }
    return normalized;
  }

  private String normalizeFuelFiltersForQuery(Object raw) {
    return normalizeFilterValueListByAliases(raw, this::normalizeFuelFilterAliases);
  }

  private String normalizeBodyTypeFiltersForQuery(Object raw) {
    return normalizeFilterValueListByAliases(raw, this::normalizeBodyTypeFilterAliases);
  }

  private String normalizeFilterValueList(Object raw) {
    return normalizeFilterValueListByAliases(raw, v -> v == null ? Collections.emptyList() : List.of(v));
  }

  private String normalizeFilterValueListByAliases(Object raw, Function<String, List<String>> normalizer) {
    if (raw == null) return null;
    String original = String.valueOf(raw);
    List<String> tokens = Arrays.stream(original.split(","))
        .map(String::trim)
        .filter(v -> !v.isBlank())
        .flatMap(v -> normalizer.apply(v).stream())
        .map(String::trim)
        .filter(v -> !v.isBlank())
        .distinct()
        .toList();

    if (tokens.isEmpty()) return null;
    return String.join(",", tokens);
  }

  private List<String> normalizeFuelFilterAliases(String raw) {
    String value = raw == null ? "" : raw.trim();
    if (value.isBlank()) return List.of();
    String upper = value.toUpperCase(Locale.ROOT);
    if (upper.equals("EV") || upper.contains("전기")) return ELECTRIC_NORMALIZED;
    if (upper.contains("LPG") && upper.contains("일반인")) return LPG_NORMALIZED;
    return List.of(value);
  }

  private List<String> normalizeBodyTypeFilterAliases(String raw) {
    String value = raw == null ? "" : raw.trim();
    if (value.isBlank()) return List.of();
    String normalized = normalizeBodyTypeForDisplay(value);
    if ("화물".equals(normalized)) return List.of("화물", "트럭", "상용", "화물차");
    if ("SUV".equals(normalized)) return List.of("SUV");
    if ("RV".equals(normalized)) return List.of("RV");
    return List.of(normalized);
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

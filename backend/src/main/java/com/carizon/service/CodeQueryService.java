package com.carizon.service;

import com.carizon.domain.mapper.CarMapper;
import com.carizon.search.config.ElasticsearchConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Slf4j
@Service
public class CodeQueryService {
  private static final long CACHE_TTL_MS = 600_000L;
  private static final int AGG_BUCKET_SIZE = 5000;
  private static final Set<String> ALLOWED_CONTEXT_KEYS = Set.of(
      "q", "makerCode", "modelGroupCode", "modelCode", "trimCode",
      "yearMin", "yearMax", "kmMin", "kmMax", "priceMin", "priceMax",
      "fuel", "bodyType", "region", "transmission", "carNo"
  );

  private final CarMapper mapper;
  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  private volatile CacheEntry makersCache;
  private volatile CacheEntry bodyTypesCache;
  private volatile CacheEntry fuelsCache;
  private final Map<String, CacheEntry> modelGroupsCache = new ConcurrentHashMap<>();
  private final Map<String, CacheEntry> modelsCache = new ConcurrentHashMap<>();

  public CodeQueryService(CarMapper mapper, RestClient restClient, ObjectMapper objectMapper) {
    this.mapper = mapper;
    this.restClient = restClient;
    this.objectMapper = objectMapper;
  }

  public List<Map<String, Object>> makers(Map<String, String> context) {
    long start = System.currentTimeMillis();
    Map<String, String> ctx = sanitizeContext(context, Set.of("makerCode", "makerCodes"));
    boolean dynamicContext = !ctx.isEmpty();

    long cacheCheckStart = System.currentTimeMillis();
    CacheEntry cache = makersCache;
    boolean cacheHit = !dynamicContext && isValid(cache);
    long cacheCheckMs = System.currentTimeMillis() - cacheCheckStart;
    if (cacheHit) {
      long totalMs = System.currentTimeMillis() - start;
      log.info("[codes.makers] cacheHit=true, rows={}, cacheCheckMs={}ms, totalMs={}ms",
          cache.data().size(), cacheCheckMs, totalMs);
      return cache.data();
    }

    List<Map<String, Object>> rows = loadRows("codes.makers", mapper::selectMakers);
    long esStart = System.currentTimeMillis();
    Map<String, Long> counts = fetchEsCounts(
        "codes.makers",
        List.of("makerCode.keyword", "makerCode"),
        Collections.emptyMap(),
        Function.identity(),
        ctx
    );
    long esMs = System.currentTimeMillis() - esStart;
    List<Map<String, Object>> merged = mergeCounts(rows, counts);
    merged.sort((a, b) -> {
      int ad = parseInt(a.get("domestic"), 0);
      int bd = parseInt(b.get("domestic"), 0);
      if (ad != bd) return Integer.compare(bd, ad);
      long ac = parseLong(a.get("carCount"), 0L);
      long bc = parseLong(b.get("carCount"), 0L);
      if (ac != bc) return Long.compare(bc, ac);
      return String.valueOf(a.getOrDefault("name", "")).compareTo(String.valueOf(b.getOrDefault("name", "")));
    });

    long cacheStoreMs = 0L;
    if (!dynamicContext) {
      long cacheStoreStart = System.currentTimeMillis();
      makersCache = new CacheEntry(System.currentTimeMillis(), merged);
      cacheStoreMs = System.currentTimeMillis() - cacheStoreStart;
    }

    long totalMs = System.currentTimeMillis() - start;
    log.info("[codes.makers] cacheHit=false, rows={}, cacheCheckMs={}ms, esMs={}ms, cacheStoreMs={}ms, totalMs={}ms",
        merged.size(), cacheCheckMs, esMs, cacheStoreMs, totalMs);

    return merged;
  }

  public List<Map<String, Object>> bodyTypes(Map<String, String> context) {
    long start = System.currentTimeMillis();
    Map<String, String> ctx = sanitizeContext(context, Set.of("bodyType"));
    boolean dynamicContext = !ctx.isEmpty();
    CacheEntry cache = bodyTypesCache;
    if (!dynamicContext && isValid(cache)) {
      log.info("[codes.body-types] cacheHit=true, rows={}, totalMs={}ms",
          cache.data().size(), System.currentTimeMillis() - start);
      return cache.data();
    }

    List<Map<String, Object>> rows = loadRows("codes.body-types", mapper::selectBodyTypes);
    long esStart = System.currentTimeMillis();
    Map<String, Long> counts = fetchEsCounts(
        "codes.body-types",
        List.of("bodyType.keyword", "bodyType"),
        Collections.emptyMap(),
        CodeQueryService::normalizeBodyType,
        ctx
    );
    long esMs = System.currentTimeMillis() - esStart;
    List<Map<String, Object>> merged = mergeCounts(rows, counts, CodeQueryService::normalizeBodyType);
    if (!dynamicContext) {
      bodyTypesCache = new CacheEntry(System.currentTimeMillis(), merged);
    }
    log.info("[codes.body-types] cacheHit=false, rows={}, esMs={}ms, totalMs={}ms",
        merged.size(), esMs, System.currentTimeMillis() - start);
    return merged;
  }

  public List<Map<String, Object>> fuels(Map<String, String> context) {
    long start = System.currentTimeMillis();
    Map<String, String> ctx = sanitizeContext(context, Set.of("fuel"));
    boolean dynamicContext = !ctx.isEmpty();
    CacheEntry cache = fuelsCache;
    if (!dynamicContext && isValid(cache)) {
      log.info("[codes.fuels] cacheHit=true, rows={}, totalMs={}ms",
          cache.data().size(), System.currentTimeMillis() - start);
      return cache.data();
    }

    long esStart = System.currentTimeMillis();
    Map<String, Long> counts = fetchEsCounts(
        "codes.fuels",
        List.of("fuel.keyword", "fuel"),
        Collections.emptyMap(),
        CodeQueryService::normalizeFuelType,
        ctx
    );
    long esMs = System.currentTimeMillis() - esStart;

    List<Map<String, Object>> rows = new ArrayList<>();
    List<String> ordered = List.of("가솔린", "디젤", "하이브리드", "전기", "LPG");
    for (String fuel : ordered) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("code", fuel);
      row.put("name", fuel);
      row.put("carCount", counts.getOrDefault(fuel, 0L));
      rows.add(row);
    }

    for (Map.Entry<String, Long> entry : counts.entrySet()) {
      String fuel = entry.getKey();
      if (ordered.contains(fuel)) continue;
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("code", fuel);
      row.put("name", fuel);
      row.put("carCount", entry.getValue());
      rows.add(row);
    }

    if (!dynamicContext) {
      fuelsCache = new CacheEntry(System.currentTimeMillis(), rows);
    }

    log.info("[codes.fuels] cacheHit=false, rows={}, esMs={}ms, totalMs={}ms",
        rows.size(), esMs, System.currentTimeMillis() - start);
    return rows;
  }

  public List<Map<String, Object>> modelGroups(String makerCode, Map<String, String> context) {
    long start = System.currentTimeMillis();
    String key = makerCode == null ? "" : makerCode.trim();
    Map<String, String> ctx = sanitizeContext(context, Set.of("makerCode", "makerCodes", "modelGroupCode"));
    boolean dynamicContext = !ctx.isEmpty();

    long cacheCheckStart = System.currentTimeMillis();
    CacheEntry cache = modelGroupsCache.get(key);
    boolean cacheHit = !dynamicContext && isValid(cache);
    long cacheCheckMs = System.currentTimeMillis() - cacheCheckStart;
    if (cacheHit) {
      long totalMs = System.currentTimeMillis() - start;
      log.info("[codes.model-groups] cacheHit=true, makerCode={}, rows={}, cacheCheckMs={}ms, totalMs={}ms",
          key, cache.data().size(), cacheCheckMs, totalMs);
      return cache.data();
    }

    List<Map<String, Object>> rows = loadRows("codes.model-groups", () -> mapper.selectModelGroups(makerCode));
    long esStart = System.currentTimeMillis();
    Map<String, Long> counts = fetchEsCounts(
        "codes.model-groups",
        List.of("modelGroupCode.keyword", "modelGroupCode"),
        Map.of("makerCode", key),
        Function.identity(),
        ctx
    );
    long esMs = System.currentTimeMillis() - esStart;
    List<Map<String, Object>> merged = mergeCounts(rows, counts);

    long cacheStoreMs = 0L;
    if (!dynamicContext) {
      long cacheStoreStart = System.currentTimeMillis();
      modelGroupsCache.put(key, new CacheEntry(System.currentTimeMillis(), merged));
      cacheStoreMs = System.currentTimeMillis() - cacheStoreStart;
    }

    long totalMs = System.currentTimeMillis() - start;
    log.info("[codes.model-groups] cacheHit=false, makerCode={}, rows={}, cacheCheckMs={}ms, esMs={}ms, cacheStoreMs={}ms, totalMs={}ms",
        key, merged.size(), cacheCheckMs, esMs, cacheStoreMs, totalMs);

    return merged;
  }

  public List<Map<String, Object>> models(String makerCode, String modelGroupCode, Map<String, String> context) {
    long start = System.currentTimeMillis();
    String makerKey = makerCode == null ? "" : makerCode.trim();
    String modelGroupKey = modelGroupCode == null ? "" : modelGroupCode.trim();
    String key = makerKey + "|" + modelGroupKey;
    Map<String, String> ctx = sanitizeContext(context, Set.of("makerCode", "makerCodes", "modelGroupCode", "modelCode"));
    boolean dynamicContext = !ctx.isEmpty();

    long cacheCheckStart = System.currentTimeMillis();
    CacheEntry cache = modelsCache.get(key);
    boolean cacheHit = !dynamicContext && isValid(cache);
    long cacheCheckMs = System.currentTimeMillis() - cacheCheckStart;
    if (cacheHit) {
      long totalMs = System.currentTimeMillis() - start;
      log.info("[codes.models] cacheHit=true, key={}, rows={}, cacheCheckMs={}ms, totalMs={}ms",
          key, cache.data().size(), cacheCheckMs, totalMs);
      return cache.data();
    }

    List<Map<String, Object>> rows = loadRows("codes.models", () -> mapper.selectModels(makerCode, modelGroupCode));
    long esStart = System.currentTimeMillis();
    Map<String, Long> counts = fetchEsCounts(
        "codes.models",
        List.of("modelCode.keyword", "modelCode"),
        Map.of("makerCode", makerKey, "modelGroupCode", modelGroupKey),
        Function.identity(),
        ctx
    );
    long esMs = System.currentTimeMillis() - esStart;
    List<Map<String, Object>> merged = mergeCounts(rows, counts);

    long cacheStoreMs = 0L;
    if (!dynamicContext) {
      long cacheStoreStart = System.currentTimeMillis();
      modelsCache.put(key, new CacheEntry(System.currentTimeMillis(), merged));
      cacheStoreMs = System.currentTimeMillis() - cacheStoreStart;
    }

    long totalMs = System.currentTimeMillis() - start;
    log.info("[codes.models] cacheHit=false, key={}, rows={}, cacheCheckMs={}ms, esMs={}ms, cacheStoreMs={}ms, totalMs={}ms",
        key, merged.size(), cacheCheckMs, esMs, cacheStoreMs, totalMs);

    return merged;
  }

  public List<Map<String, Object>> trims(String makerCode, String modelGroupCode, String modelCode, Map<String, String> context) {
    long start = System.currentTimeMillis();
    String makerKey = makerCode == null ? "" : makerCode.trim();
    String modelGroupKey = modelGroupCode == null ? "" : modelGroupCode.trim();
    String modelKey = modelCode == null ? "" : modelCode.trim();
    Map<String, String> ctx = sanitizeContext(
        context,
        Set.of("makerCode", "makerCodes", "modelGroupCode", "modelCode", "trimCode")
    );

    List<Map<String, Object>> rows = loadRows("codes.trims", () -> mapper.selectTrims(makerCode, modelGroupCode, modelCode));
    long esStart = System.currentTimeMillis();
    Map<String, Long> counts = fetchEsCounts(
        "codes.trims",
        List.of("trimCode.keyword", "trimCode"),
        Map.of("makerCode", makerKey, "modelGroupCode", modelGroupKey, "modelCode", modelKey),
        Function.identity(),
        ctx
    );
    long esMs = System.currentTimeMillis() - esStart;
    List<Map<String, Object>> merged = mergeCounts(rows, counts);

    long totalMs = System.currentTimeMillis() - start;
    log.info("[codes.trims] rows={}, esMs={}ms, totalMs={}ms, makerCode={}, modelGroupCode={}, modelCode={}",
        merged.size(), esMs, totalMs, makerKey, modelGroupKey, modelKey);
    return merged;
  }

  public List<Map<String, Object>> grades(String makerCode, String modelGroupCode, String modelCode, String trimCode) {
    return mapper.selectGrades(makerCode, modelGroupCode, modelCode, trimCode);
  }

  private static boolean isValid(CacheEntry cache) {
    return cache != null && (System.currentTimeMillis() - cache.cachedAtMs()) < CACHE_TTL_MS;
  }

  private List<Map<String, Object>> loadRows(String tag, RowLoader loader) {
    long dbStart = System.currentTimeMillis();
    List<Map<String, Object>> rows = loader.load();
    long dbMs = System.currentTimeMillis() - dbStart;
    log.info("[{}] dbListMs={}ms, rows={}", tag, dbMs, rows != null ? rows.size() : 0);
    return rows == null ? List.of() : rows;
  }

  private Map<String, Long> fetchEsCounts(
      String tag,
      List<String> aggFieldCandidates,
      Map<String, String> termFilters,
      Function<String, String> keyNormalizer,
      Map<String, String> contextFilters
  ) {
    List<Map<String, Object>> additionalFilters = buildContextFilterClauses(contextFilters);
    List<Map<String, Object>> mustClauses = buildContextMustClauses(contextFilters);
    for (String aggField : aggFieldCandidates) {
      long esStart = System.currentTimeMillis();
      try {
        String payload = buildAggPayload(aggField, termFilters, mustClauses, additionalFilters);
        Request request = new Request("POST", "/" + ElasticsearchConfig.CARS_INDEX + "/_search");
        request.setJsonEntity(payload);
        Response response = restClient.performRequest(request);
        String responseBody = EntityUtils.toString(response.getEntity());

        JsonNode buckets = objectMapper.readTree(responseBody)
            .path("aggregations")
            .path("codes")
            .path("buckets");

        Map<String, Long> counts = new HashMap<>();
        if (buckets.isArray()) {
          for (JsonNode bucket : buckets) {
            String key = bucket.path("key").asText("");
            if (key == null || key.isBlank()) continue;
            String normalizedKey = keyNormalizer.apply(key);
            if (normalizedKey == null || normalizedKey.isBlank()) continue;
            long count = bucket.path("doc_count").asLong(0L);
            counts.merge(normalizedKey, count, Long::sum);
          }
        }

        long esMs = System.currentTimeMillis() - esStart;
        log.info("[{}] esAggMs={}ms, field={}, termFilters={}, contextFilters={}, buckets={}",
            tag, esMs, aggField, termFilters, contextFilters, counts.size());
        return counts;
      } catch (Exception e) {
        long esMs = System.currentTimeMillis() - esStart;
        log.warn("[{}] esAgg failed: field={}, termFilters={}, contextFilters={}, esMs={}ms, reason={}",
            tag, aggField, termFilters, contextFilters, esMs, e.getMessage());
      }
    }
    return Collections.emptyMap();
  }

  private String buildAggPayload(
      String aggField,
      Map<String, String> termFilters,
      List<Map<String, Object>> mustClauses,
      List<Map<String, Object>> additionalFilters
  ) throws Exception {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("size", 0);

    List<Map<String, Object>> filters = new ArrayList<>();
    for (Map.Entry<String, String> entry : termFilters.entrySet()) {
      String value = entry.getValue();
      if (value == null || value.isBlank()) continue;
      filters.add(Map.of("term", Map.of(entry.getKey(), value)));
    }

    if (additionalFilters != null && !additionalFilters.isEmpty()) {
      filters.addAll(additionalFilters);
    }

    List<Map<String, Object>> must = mustClauses == null ? List.of() : mustClauses;
    if (must.isEmpty() && filters.isEmpty()) {
      root.put("query", Map.of("match_all", Map.of()));
    } else {
      Map<String, Object> bool = new LinkedHashMap<>();
      if (!must.isEmpty()) bool.put("must", must);
      if (!filters.isEmpty()) bool.put("filter", filters);
      root.put("query", Map.of("bool", bool));
    }

    root.put("aggs", Map.of(
        "codes", Map.of(
            "terms", Map.of(
                "field", aggField,
                "size", AGG_BUCKET_SIZE
            )
        )
    ));
    return objectMapper.writeValueAsString(root);
  }

  private static Map<String, String> sanitizeContext(Map<String, String> context, Set<String> excludeKeys) {
    Map<String, String> out = new LinkedHashMap<>();
    if (context == null || context.isEmpty()) return out;
    for (Map.Entry<String, String> entry : context.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (key == null || value == null) continue;
      String trimmedKey = key.trim();
      String trimmedValue = value.trim();
      if (trimmedKey.isEmpty() || trimmedValue.isEmpty()) continue;
      if (!ALLOWED_CONTEXT_KEYS.contains(trimmedKey)) continue;
      if (excludeKeys != null && excludeKeys.contains(trimmedKey)) continue;
      out.put(trimmedKey, trimmedValue);
    }
    return out;
  }

  private static List<Map<String, Object>> buildContextMustClauses(Map<String, String> context) {
    String q = context != null ? context.get("q") : null;
    if (q == null || q.isBlank()) return List.of();
    String query = q.trim();
    return List.of(
        Map.of("bool", Map.of(
            "should", List.of(
                Map.of("multi_match", Map.of("query", query, "fields", List.of("makerName", "modelName", "trimName", "modelCode"))),
                Map.of("wildcard", Map.of("makerName", Map.of("value", "*" + query + "*"))),
                Map.of("wildcard", Map.of("modelName", Map.of("value", "*" + query + "*")))
            ),
            "minimum_should_match", "1"
        ))
    );
  }

  private static List<Map<String, Object>> buildContextFilterClauses(Map<String, String> context) {
    if (context == null || context.isEmpty()) return List.of();
    List<Map<String, Object>> filters = new ArrayList<>();

    addTermFilter(filters, "makerCode", context.get("makerCode"));
    addTermFilter(filters, "modelGroupCode", context.get("modelGroupCode"));
    addTermsShouldFilter(filters, "modelCode", context.get("modelCode"));
    addTermFilter(filters, "trimCode", context.get("trimCode"));
    addRangeGte(filters, "year", parseInteger(context.get("yearMin")));
    addRangeLte(filters, "year", parseInteger(context.get("yearMax")));
    addRangeGte(filters, "km", parseInteger(context.get("kmMin")));
    addRangeLte(filters, "km", parseInteger(context.get("kmMax")));
    addRangeGte(filters, "priceMin", parseInteger(context.get("priceMin")));
    addRangeLte(filters, "priceMax", parseInteger(context.get("priceMax")));
    addTermsShouldFilter(filters, "fuel.keyword", context.get("fuel"));
    addBodyTypeShouldFilter(filters, context.get("bodyType"));
    addTermFilter(filters, "region.keyword", context.get("region"));
    addTermFilter(filters, "transmission.keyword", context.get("transmission"));
    addTermFilter(filters, "carNo.keyword", context.get("carNo"));
    return filters;
  }

  private static void addTermFilter(List<Map<String, Object>> filters, String field, String value) {
    if (value == null || value.isBlank()) return;
    filters.add(Map.of("term", Map.of(field, value.trim())));
  }

  private static void addTermsShouldFilter(List<Map<String, Object>> filters, String field, String csv) {
    List<String> values = parseCsvValues(csv);
    if (values.isEmpty()) return;
    if (values.size() == 1) {
      filters.add(Map.of("term", Map.of(field, values.get(0))));
      return;
    }
    List<Map<String, Object>> should = new ArrayList<>();
    for (String value : values) {
      should.add(Map.of("term", Map.of(field, value)));
    }
    filters.add(Map.of("bool", Map.of(
        "should", should,
        "minimum_should_match", "1"
    )));
  }

  private static void addBodyTypeShouldFilter(List<Map<String, Object>> filters, String csv) {
    List<String> values = parseCsvValues(csv);
    if (values.isEmpty()) return;
    List<Map<String, Object>> should = new ArrayList<>();
    for (String value : values) {
      String normalized = normalizeBodyType(value);
      if (normalized == null || normalized.isBlank()) continue;
      should.add(Map.of("term", Map.of("bodyType.keyword", normalized)));
    }
    if (should.isEmpty()) return;
    filters.add(Map.of("bool", Map.of(
        "should", should,
        "minimum_should_match", "1"
    )));
  }

  private static List<String> parseCsvValues(String raw) {
    if (raw == null || raw.isBlank()) return List.of();
    String[] arr = raw.split(",");
    List<String> out = new ArrayList<>();
    for (String item : arr) {
      if (item == null) continue;
      String value = item.trim();
      if (value.isEmpty()) continue;
      out.add(value);
    }
    return out;
  }

  private static Integer parseInteger(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return Integer.parseInt(raw.trim());
    } catch (Exception e) {
      return null;
    }
  }

  private static void addRangeGte(List<Map<String, Object>> filters, String field, Integer value) {
    if (value == null) return;
    filters.add(Map.of("range", Map.of(field, Map.of("gte", value))));
  }

  private static void addRangeLte(List<Map<String, Object>> filters, String field, Integer value) {
    if (value == null) return;
    filters.add(Map.of("range", Map.of(field, Map.of("lte", value))));
  }

  private static List<Map<String, Object>> mergeCounts(List<Map<String, Object>> rows, Map<String, Long> counts) {
    return mergeCounts(rows, counts, Function.identity());
  }

  private static List<Map<String, Object>> mergeCounts(
      List<Map<String, Object>> rows,
      Map<String, Long> counts,
      Function<String, String> keyNormalizer
  ) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (rows == null) return out;
    for (Map<String, Object> row : rows) {
      Map<String, Object> copy = new LinkedHashMap<>(row);
      String code = String.valueOf(copy.getOrDefault("code", ""));
      String normalizedCode = keyNormalizer.apply(code);
      long count = counts.getOrDefault(normalizedCode, 0L);
      copy.put("carCount", count);
      out.add(copy);
    }
    return out;
  }

  private static String normalizeBodyType(String raw) {
    if (raw == null) return "";
    String v = raw.trim();
    if ("suv".equalsIgnoreCase(v)) return "SUV";
    if ("rv".equalsIgnoreCase(v)) return "RV";
    return v;
  }

  private static String normalizeFuelType(String raw) {
    if (raw == null) return "";
    String v = raw.trim().toLowerCase();
    if (v.isBlank()) return "";
    if (v.contains("lpg")) return "LPG";
    if (v.contains("전기") || v.contains("electric") || "ev".equals(v)) return "전기";
    if (v.contains("하이브리드") || v.contains("hybrid")) return "하이브리드";
    if (v.contains("디젤") || v.contains("경유") || v.contains("diesel")) return "디젤";
    if (v.contains("가솔린") || v.contains("휘발유") || v.contains("gasoline") || v.contains("petrol")) return "가솔린";
    return raw.trim();
  }

  private static int parseInt(Object v, int def) {
    if (v == null) return def;
    if (v instanceof Number n) return n.intValue();
    try {
      return Integer.parseInt(String.valueOf(v).trim());
    } catch (Exception e) {
      return def;
    }
  }

  private static long parseLong(Object v, long def) {
    if (v == null) return def;
    if (v instanceof Number n) return n.longValue();
    try {
      return Long.parseLong(String.valueOf(v).trim());
    } catch (Exception e) {
      return def;
    }
  }

  @FunctionalInterface
  private interface RowLoader {
    List<Map<String, Object>> load();
  }

  private record CacheEntry(long cachedAtMs, List<Map<String, Object>> data) {}
}

package com.carizon.search.service;

import com.carizon.dto.CarListItemDto;
import com.carizon.search.config.ElasticsearchConfig;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Elasticsearch 기반 차량 검색/인덱싱 서비스.
 * 기존 MeilisearchService와 동일한 search/index API를 제공.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchCarSearchService {

    private final ElasticsearchClient client;
    private final ObjectMapper objectMapper;
    private static final String INDEX = ElasticsearchConfig.CARS_INDEX;

    /**
     * 차량 검색 (페이징, 필터, 정렬, 전체 건수 포함)
     */
    public Map<String, Object> search(Map<String, Object> queryParams) {
        try {
            int page = parseInt(queryParams.get("page"), 0);
            int size = parseInt(queryParams.get("size"), 20);
            if (page < 0) page = 0;
            if (size <= 0 || size > 200) size = 20;
            int from = page * size;

            Query query = buildQuery(queryParams);
            String sortField = getSortField(queryParams);
            SortOrder sortOrder = getSortOrder(queryParams);

            SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
                    .index(INDEX)
                    .query(query)
                    .from(from)
                    .size(size)
                    .trackTotalHits(t -> t.enabled(true));
            searchBuilder.sort(s -> s.field(f -> f.field(sortField).order(sortOrder)));

            Map<String, Object> dslForLog = buildDslForLog(queryParams, from, size, sortField, sortOrder);
            if (log.isDebugEnabled()) {
                try {
                    log.debug("[Elasticsearch] search DSL: {}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(dslForLog));
                } catch (JsonProcessingException e) {
                    log.debug("[Elasticsearch] search DSL (raw): {}", dslForLog);
                }
            }

            SearchResponse<Map> response = client.search(searchBuilder.build(), Map.class);
            long total = response.hits().total() != null
                ? response.hits().total().value()
                : 0L;
            List<CarListItemDto> content = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source != null) {
                    CarListItemDto dto = convertToCarListItem(source);
                    if (dto != null) content.add(dto);
                }
            }

            int totalPages = (int) Math.ceil(total / (double) Math.max(1, size));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", content);
            result.put("page", page);
            result.put("size", size);
            result.put("totalElements", total);
            result.put("totalPages", totalPages);
            log.debug("[Elasticsearch] search done: totalElements={}, totalPages={}", total, totalPages);
            return result;
        } catch (ElasticsearchException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("index_not_found") || msg.contains("no such index")) {
                log.debug("[Elasticsearch] index [{}] does not exist yet, return empty result", INDEX);
                return createEmptyResult(queryParams);
            }
            log.error("[Elasticsearch] search error: queryParams={}", queryParams, e);
            return createEmptyResult(queryParams);
        } catch (Exception e) {
            log.error("[Elasticsearch] search error: queryParams={}", queryParams, e);
            return createEmptyResult(queryParams);
        }
    }

    public void indexCar(Map<String, Object> carData) {
        try {
            Object id = carData.get("carId");
            client.index(i -> i.index(INDEX).id(String.valueOf(id)).document(carData));
            log.debug("[Elasticsearch] car index done: carId={}", id);
        } catch (Exception e) {
            log.error("[Elasticsearch] car index failed: carId={}", carData.get("carId"), e);
        }
    }

    public void indexCars(List<Map<String, Object>> carsData) {
        if (carsData == null || carsData.isEmpty()) return;
        try {
            List<BulkOperation> ops = new ArrayList<>();
            for (Map<String, Object> doc : carsData) {
                Object id = doc.get("carId");
                Map<String, Object> clean = normalizeForIndex(doc);
                ops.add(BulkOperation.of(b -> b.index(IndexOperation.of(i -> i.index(INDEX).id(String.valueOf(id)).document(clean)))));
            }
            BulkResponse resp = client.bulk(BulkRequest.of(r -> r.operations(ops)));
            if (resp.errors()) {
                resp.items().stream()
                    .filter(i -> i.error() != null)
                    .forEach(i -> log.warn("[Elasticsearch] bulk item error: {}", i.error() != null ? i.error().reason() : ""));
            }
            log.info("[Elasticsearch] car batch index done: {} rows", carsData.size());
        } catch (Exception e) {
            log.error("[Elasticsearch] car batch index failed: {} rows", carsData.size(), e);
        }
    }

    /**
     * 인덱스 전체 삭제 (재인덱싱 전 호출). 인덱스가 없으면 무시하고 진행.
     */
    public void deleteAllDocuments() {
        try {
            client.deleteByQuery(d -> d.index(INDEX).query(QueryBuilders.matchAll(m -> m)));
            log.info("[Elasticsearch] delete all documents done");
        } catch (ElasticsearchException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("index_not_found") || msg.contains("no such index")) {
                log.info("[Elasticsearch] index [{}] does not exist, skip delete (first reindex)", INDEX);
                return;
            }
            log.error("[Elasticsearch] delete all failed", e);
            throw e;
        } catch (Exception e) {
            log.error("[Elasticsearch] delete all failed", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 전체 문서 수 (대시보드 등)
     */
    public long count() {
        try {
            return client.count(c -> c.index(INDEX)).count();
        } catch (Exception e) {
            log.warn("[Elasticsearch] count failed", e);
            return 0;
        }
    }

    /**
     * 검색 조건과 동일한 쿼리로 매칭 건수만 조회 (Count API, 10k 상한 없음)
     */
    public long countByQuery(Map<String, Object> queryParams) {
        try {
            Query query = buildQuery(queryParams);
            long cnt = client.count(c -> c.index(INDEX).query(query)).count();
            log.debug("[Elasticsearch] countByQuery: {}", cnt);
            return cnt;
        } catch (ElasticsearchException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("index_not_found") || msg.contains("no such index")) {
                return 0;
            }
            log.warn("[Elasticsearch] countByQuery failed", e);
            return 0;
        } catch (Exception e) {
            log.warn("[Elasticsearch] countByQuery failed", e);
            return 0;
        }
    }

    private Query buildQuery(Map<String, Object> params) {
        List<Query> must = new ArrayList<>();
        List<Query> filter = new ArrayList<>();
        List<String> modelCodes = parseCsvValues(params.get("modelCode"));
        List<String> makerCodes = parseCsvValues(params.get("makerCodes"));
        List<String> excludeMakerCodes = parseCsvValues(params.get("excludeMakerCodes"));

        String q = buildSearchQuery(params);
        if (q != null && !q.isEmpty()) {
            must.add(QueryBuilders.bool(b -> b
                .should(QueryBuilders.multiMatch(m -> m.query(q).fields("makerName", "modelName", "trimName", "modelCode")))
                .should(QueryBuilders.wildcard(w -> w.field("makerName").value("*" + q + "*")))
                .should(QueryBuilders.wildcard(w -> w.field("modelName").value("*" + q + "*")))
                .minimumShouldMatch("1")
            ));
        }
        if (params.get("makerCode") != null) {
            filter.add(QueryBuilders.term(t -> t.field("makerCode").value(String.valueOf(params.get("makerCode")))));
        }
        if (!makerCodes.isEmpty()) {
            if (makerCodes.size() == 1) {
                filter.add(QueryBuilders.term(t -> t.field("makerCode").value(makerCodes.get(0))));
            } else {
                filter.add(QueryBuilders.bool(b -> {
                    List<Query> should = new ArrayList<>();
                    for (String code : makerCodes) {
                        should.add(QueryBuilders.term(t -> t.field("makerCode").value(code)));
                    }
                    b.should(should);
                    b.minimumShouldMatch("1");
                    return b;
                }));
            }
        }
        if (!excludeMakerCodes.isEmpty()) {
            filter.add(QueryBuilders.bool(b -> {
                List<Query> mustNot = new ArrayList<>();
                for (String code : excludeMakerCodes) {
                    mustNot.add(QueryBuilders.term(t -> t.field("makerCode").value(code)));
                }
                b.mustNot(mustNot);
                return b;
            }));
        }
        if (params.get("modelGroupCode") != null && (modelCodes.isEmpty() || modelCodes.size() == 1)) {
            filter.add(QueryBuilders.term(t -> t.field("modelGroupCode").value(String.valueOf(params.get("modelGroupCode")))));
        }
        if (!modelCodes.isEmpty()) {
            if (modelCodes.size() == 1) {
                filter.add(QueryBuilders.term(t -> t.field("modelCode").value(modelCodes.get(0))));
            } else if (!modelCodes.isEmpty()) {
                filter.add(QueryBuilders.bool(b -> {
                    List<Query> should = new ArrayList<>();
                    for (String code : modelCodes) {
                        should.add(QueryBuilders.term(t -> t.field("modelCode").value(code)));
                    }
                    b.should(should);
                    b.minimumShouldMatch("1");
                    return b;
                }));
            }
        }
        if (params.get("trimCode") != null) {
            filter.add(QueryBuilders.term(t -> t.field("trimCode").value(String.valueOf(params.get("trimCode")))));
        }
        if (params.get("gradeCode") != null) {
            filter.add(QueryBuilders.term(t -> t.field("gradeCode").value(String.valueOf(params.get("gradeCode")))));
        }
        if (params.get("yearMin") != null) {
            filter.add(QueryBuilders.range(r -> r.field("year").gte(JsonData.of(parseInt(params.get("yearMin"), 0)))));
        }
        if (params.get("yearMax") != null) {
            filter.add(QueryBuilders.range(r -> r.field("year").lte(JsonData.of(parseInt(params.get("yearMax"), Integer.MAX_VALUE)))));
        }
        if (params.get("kmMin") != null) {
            filter.add(QueryBuilders.range(r -> r.field("km").gte(JsonData.of(parseInt(params.get("kmMin"), 0)))));
        }
        if (params.get("kmMax") != null) {
            filter.add(QueryBuilders.range(r -> r.field("km").lte(JsonData.of(parseInt(params.get("kmMax"), Integer.MAX_VALUE)))));
        }
        Integer priceMin = sanitizePriceMin(params.get("priceMin"));
        Integer priceMax = sanitizePriceMax(params.get("priceMax"));
        if (priceMin != null && priceMax != null && priceMax < priceMin) {
            // 잘못된 상한(예: 0)으로 결과를 0건으로 만드는 상황 방지
            priceMax = null;
        }
        if (priceMin != null) {
            filter.add(QueryBuilders.range(r -> r.field("priceMin").gte(JsonData.of(priceMin))));
        }
        if (priceMax != null) {
            final int finalPriceMax = priceMax;
            filter.add(QueryBuilders.range(r -> r.field("priceMax").lte(JsonData.of(finalPriceMax))));
        }
        List<String> fuels = parseCsvValues(params.get("fuel"));
        if (!fuels.isEmpty()) {
            if (fuels.size() == 1) {
                filter.add(QueryBuilders.term(t -> t.field("fuel.keyword").value(fuels.get(0))));
            } else {
                filter.add(QueryBuilders.bool(b -> {
                    List<Query> should = new ArrayList<>();
                    for (String fuel : fuels) {
                        should.add(QueryBuilders.term(t -> t.field("fuel.keyword").value(fuel)));
                    }
                    b.should(should);
                    b.minimumShouldMatch("1");
                    return b;
                }));
            }
        }
        if (params.get("transmission") != null && !String.valueOf(params.get("transmission")).isEmpty()) {
            filter.add(QueryBuilders.term(t -> t.field("transmission.keyword").value(String.valueOf(params.get("transmission")))));
        }
        List<String> bodyTypes = parseBodyTypeTokens(params.get("bodyType"));
        if (!bodyTypes.isEmpty()) {
            filter.add(QueryBuilders.bool(b -> {
                List<Query> should = new ArrayList<>();
                for (String bt : bodyTypes) {
                    should.add(QueryBuilders.term(t -> t.field("bodyType.keyword").value(bt)));
                }
                b.should(should);
                b.minimumShouldMatch("1");
                return b;
            }));
        }
        if (params.get("region") != null && !String.valueOf(params.get("region")).isEmpty()) {
            filter.add(QueryBuilders.term(t -> t.field("region.keyword").value(String.valueOf(params.get("region")))));
        }
        if (params.get("carNo") != null && !String.valueOf(params.get("carNo")).trim().isEmpty()) {
            filter.add(QueryBuilders.term(t -> t.field("carNo.keyword").value(String.valueOf(params.get("carNo")).trim())));
        }

        // 기본 필터: 가격이 0보다 큰 매물만
        filter.add(QueryBuilders.range(r -> r.field("priceMin").gt(JsonData.of(0))));

        return QueryBuilders.bool(b -> {
            if (!must.isEmpty()) b.must(must);
            b.filter(filter);
            return b;
        });
    }

    private String getSortField(Map<String, Object> params) {
        String sort = params.containsKey("sort") ? String.valueOf(params.get("sort")) : null;
        if (sort == null || sort.isEmpty() || "null".equals(sort)) return "priceUpdatedAt";
        if ("LOW_PRICE".equals(sort)) return "priceMin";
        if ("LOW_KM".equals(sort)) return "km";
        if ("NEW_YEAR".equals(sort)) return "year";
        if ("RECENT".equals(sort)) return "priceUpdatedAt";
        return "priceUpdatedAt";
    }

    private SortOrder getSortOrder(Map<String, Object> params) {
        String sort = params.containsKey("sort") ? String.valueOf(params.get("sort")) : null;
        if ("NEW_YEAR".equals(sort)) return SortOrder.Desc;
        if ("LOW_PRICE".equals(sort) || "LOW_KM".equals(sort)) return SortOrder.Asc;
        if ("RECENT".equals(sort)) return SortOrder.Desc;
        return SortOrder.Desc;
    }

    private String buildSearchQuery(Map<String, Object> params) {
        if (params.containsKey("q") && params.get("q") != null) {
            return String.valueOf(params.get("q"));
        }
        List<String> terms = new ArrayList<>();
        if (params.get("makerName") != null) terms.add(String.valueOf(params.get("makerName")));
        if (params.get("modelName") != null) terms.add(String.valueOf(params.get("modelName")));
        return terms.isEmpty() ? "" : String.join(" ", terms);
    }

    /** 로그용: 실제로 날리는 검색 요청과 동일한 구조의 DSL 맵 생성 */
    private Map<String, Object> buildDslForLog(Map<String, Object> params, int from, int size, String sortField, SortOrder sortOrder) {
        Map<String, Object> dsl = new LinkedHashMap<>();
        dsl.put("index", INDEX);
        dsl.put("from", from);
        dsl.put("size", size);
        if (sortField != null) {
            dsl.put("sort", List.of(Map.of(sortField, Map.of("order", sortOrder == SortOrder.Asc ? "asc" : "desc"))));
        }
        Map<String, Object> query = buildQueryAsMapForLog(params);
        dsl.put("query", query);
        return dsl;
    }

    private Map<String, Object> buildQueryAsMapForLog(Map<String, Object> params) {
        String q = buildSearchQuery(params);
        List<Map<String, Object>> must = new ArrayList<>();
        List<Map<String, Object>> filter = new ArrayList<>();
        List<String> modelCodes = parseCsvValues(params.get("modelCode"));
        List<String> makerCodes = parseCsvValues(params.get("makerCodes"));
        List<String> excludeMakerCodes = parseCsvValues(params.get("excludeMakerCodes"));
        if (q != null && !q.isEmpty()) {
            must.add(Map.of("bool", Map.of(
                "should", List.of(
                    Map.of("multi_match", Map.of("query", q, "fields", List.of("makerName", "modelName", "trimName", "modelCode"))),
                    Map.of("wildcard", Map.of("makerName", Map.of("value", "*" + q + "*"))),
                    Map.of("wildcard", Map.of("modelName", Map.of("value", "*" + q + "*")))
                ),
                "minimum_should_match", "1"
            )));
        }
        if (params.get("makerCode") != null) filter.add(Map.of("term", Map.of("makerCode", params.get("makerCode"))));
        if (!makerCodes.isEmpty()) {
            if (makerCodes.size() == 1) {
                filter.add(Map.of("term", Map.of("makerCode", makerCodes.get(0))));
            } else {
                filter.add(Map.of("terms", Map.of("makerCode", makerCodes)));
            }
        }
        if (!excludeMakerCodes.isEmpty()) {
            List<Map<String, Object>> mustNot = new ArrayList<>();
            for (String code : excludeMakerCodes) {
                mustNot.add(Map.of("term", Map.of("makerCode", code)));
            }
            filter.add(Map.of("bool", Map.of("must_not", mustNot)));
        }
        if (params.get("modelGroupCode") != null && (modelCodes.isEmpty() || modelCodes.size() == 1)) {
            filter.add(Map.of("term", Map.of("modelGroupCode", params.get("modelGroupCode"))));
        }
        if (!modelCodes.isEmpty()) {
            if (modelCodes.size() == 1) {
                filter.add(Map.of("term", Map.of("modelCode", modelCodes.get(0))));
            } else if (!modelCodes.isEmpty()) {
                filter.add(Map.of("terms", Map.of("modelCode", modelCodes)));
            }
        }
        if (params.get("trimCode") != null) filter.add(Map.of("term", Map.of("trimCode", params.get("trimCode"))));
        if (params.get("gradeCode") != null) filter.add(Map.of("term", Map.of("gradeCode", params.get("gradeCode"))));
        if (params.get("yearMin") != null) filter.add(Map.of("range", Map.of("year", Map.of("gte", parseInt(params.get("yearMin"), 0)))));
        if (params.get("yearMax") != null) filter.add(Map.of("range", Map.of("year", Map.of("lte", parseInt(params.get("yearMax"), Integer.MAX_VALUE)))));
        if (params.get("kmMin") != null) filter.add(Map.of("range", Map.of("km", Map.of("gte", parseInt(params.get("kmMin"), 0)))));
        if (params.get("kmMax") != null) filter.add(Map.of("range", Map.of("km", Map.of("lte", parseInt(params.get("kmMax"), Integer.MAX_VALUE)))));
        Integer priceMin = sanitizePriceMin(params.get("priceMin"));
        Integer priceMax = sanitizePriceMax(params.get("priceMax"));
        if (priceMin != null && priceMax != null && priceMax < priceMin) {
            priceMax = null;
        }
        if (priceMin != null) filter.add(Map.of("range", Map.of("priceMin", Map.of("gte", priceMin))));
        if (priceMax != null) filter.add(Map.of("range", Map.of("priceMax", Map.of("lte", priceMax))));
        List<String> fuels = parseCsvValues(params.get("fuel"));
        if (!fuels.isEmpty()) {
            if (fuels.size() == 1) {
                filter.add(Map.of("term", Map.of("fuel.keyword", fuels.get(0))));
            } else {
                filter.add(Map.of("terms", Map.of("fuel.keyword", fuels)));
            }
        }
        if (params.get("transmission") != null && !String.valueOf(params.get("transmission")).isEmpty()) filter.add(Map.of("term", Map.of("transmission.keyword", params.get("transmission"))));
        List<String> bodyTypes = parseBodyTypeTokens(params.get("bodyType"));
        if (!bodyTypes.isEmpty()) {
            filter.add(Map.of("terms", Map.of("bodyType.keyword", bodyTypes)));
        }
        if (params.get("region") != null && !String.valueOf(params.get("region")).isEmpty()) filter.add(Map.of("term", Map.of("region.keyword", params.get("region"))));
        if (params.get("carNo") != null && !String.valueOf(params.get("carNo")).trim().isEmpty()) filter.add(Map.of("term", Map.of("carNo.keyword", String.valueOf(params.get("carNo")).trim())));

        // 기본 필터: 가격이 0보다 큰 매물만
        filter.add(Map.of("range", Map.of("priceMin", Map.of("gt", 0))));

        Map<String, Object> bool = new LinkedHashMap<>();
        if (!must.isEmpty()) bool.put("must", must);
        bool.put("filter", filter);
        return Map.of("bool", bool);
    }

    private Map<String, Object> createEmptyResult(Map<String, Object> queryParams) {
        int page = parseInt(queryParams.get("page"), 0);
        int size = parseInt(queryParams.get("size"), 20);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", new ArrayList<>());
        result.put("page", page);
        result.put("size", size);
        result.put("totalElements", 0L);
        result.put("totalPages", 0);
        return result;
    }

    private Map<String, Object> normalizeForIndex(Map<String, Object> doc) {
        Map<String, Object> out = new HashMap<>(doc);
        if (out.containsKey("priceUpdatedAt") && out.get("priceUpdatedAt") != null) {
            Object v = out.get("priceUpdatedAt");
            if (v instanceof LocalDateTime) {
                out.put("priceUpdatedAt", ((LocalDateTime) v).toString());
            } else if (v instanceof java.sql.Timestamp) {
                out.put("priceUpdatedAt", ((java.sql.Timestamp) v).toLocalDateTime().toString());
            }
        }
        out.entrySet().removeIf(e -> e.getValue() == null && !"carId".equals(e.getKey()));
        return out;
    }

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
            LocalDateTime priceUpdatedAt = parseDateTime(priceUpdatedAtStr);
            String representativeImageUrl = getString(hit, "representativeImageUrl");
            String modelCode = getString(hit, "modelCode");
            String fuel = getString(hit, "fuel");
            String region = getString(hit, "region");
            return new CarListItemDto(carId, maker, model, trim, year, km, priceMin, priceMax, priceUpdatedAt, representativeImageUrl, modelCode, fuel, region);
        } catch (Exception e) {
            log.warn("[Elasticsearch] DTO convert failed: {}", e.getMessage());
            return null;
        }
    }

    private static int parseInt(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static long parseLong(Object v, long def) {
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static List<String> parseCsvValues(Object value) {
        if (value == null) return List.of();
        String raw = String.valueOf(value).trim();
        if (raw.isEmpty()) return List.of();

        LinkedHashSet<String> values = new LinkedHashSet<>();
        String[] parts = raw.split(",");
        for (String part : parts) {
            String item = part.trim();
            if (!item.isEmpty()) values.add(item);
        }
        return new ArrayList<>(values);
    }

    private static List<String> parseBodyTypeTokens(Object value) {
        List<String> values = parseCsvValues(value);
        if (values.isEmpty()) return List.of();

        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String item : values) {
            String trimmed = item == null ? "" : item.trim();
            if (trimmed.isEmpty()) continue;

            if ("스포츠카".equalsIgnoreCase(trimmed) || "sportscar".equalsIgnoreCase(trimmed) || "sports car".equalsIgnoreCase(trimmed)) {
                tokens.add("스포츠카");
                tokens.add("쿠페");
                tokens.add("컨버터블");
            }

            // 영문 차종(SUV/RV)은 소문자 우선 + 대문자 호환 둘 다 지원
            String lower = trimmed.toLowerCase(Locale.ROOT);
            String upper = trimmed.toUpperCase(Locale.ROOT);
            tokens.add(lower);
            tokens.add(upper);
            tokens.add(trimmed);
        }
        return new ArrayList<>(tokens);
    }

    private static Integer sanitizePriceMin(Object value) {
        Integer parsed = parseNullableInt(value);
        if (parsed == null) return null;
        return Math.max(parsed, 0);
    }

    private static Integer sanitizePriceMax(Object value) {
        Integer parsed = parseNullableInt(value);
        if (parsed == null) return null;
        return parsed > 0 ? parsed : null;
    }

    private static Integer parseNullableInt(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        try {
            String raw = String.valueOf(value).trim();
            if (raw.isEmpty() || "null".equalsIgnoreCase(raw)) return null;
            return Integer.parseInt(raw);
        } catch (Exception ignored) {
            return null;
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

    private static LocalDateTime parseDateTime(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return LocalDateTime.parse(s.replace(" ", "T"));
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(s, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception e2) {
                return null;
            }
        }
    }
}

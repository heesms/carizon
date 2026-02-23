package com.carizon.search.service;

import com.carizon.dto.CarListItemDto;
import com.carizon.search.config.ElasticsearchConfig;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScoreBuilders;
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
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

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
    private static final List<String> FUEL_LPG_ALIASES = List.of("LPG(일반인)", "LPG(일반인 구입)");
    private static final List<String> FUEL_ELECTRIC_ALIASES = List.of("전기", "EV", "전기(EV)", "전기 EV");

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

            Query baseQuery = buildQuery(queryParams);
            String sortField = getSortField(queryParams);
            SortOrder sortOrder = getSortOrder(queryParams);
            // 정렬 없으면 무작위 노출 (random_score)
            Query query = sortField == null
                    ? QueryBuilders.functionScore(fs -> fs
                            .query(baseQuery)
                            .functions(FunctionScoreBuilders.randomScore(rs -> rs.seed(String.valueOf(System.currentTimeMillis())).field("_seq_no")))
                            .boostMode(FunctionBoostMode.Replace))
                    : baseQuery;

            SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
                    .index(INDEX)
                    .query(query)
                    .from(from)
                    .size(size);
            if (sortField != null) {
                searchBuilder.sort(s -> s.field(f -> f.field(sortField).order(sortOrder)));
            }

            Map<String, Object> dslForLog = buildDslForLog(queryParams, from, size, sortField, sortOrder);
            try {
                log.info("[Elasticsearch] search DSL: {}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(dslForLog));
            } catch (JsonProcessingException e) {
                log.info("[Elasticsearch] search DSL (raw): {}", dslForLog);
            }

            // 매칭 건수는 Count API로 별도 조회 (검색 결과 total 상한 10k 없이 정확한 값)
            long total = countByQuery(queryParams);

            SearchResponse<Map> response = client.search(searchBuilder.build(), Map.class);
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

    /**
     * 필드별 terms 집계를 통해 카운트 계산 (필터 조건 포함).
     * text 타입 필드인 경우 자동으로 .keyword 서브필드를 재시도한다.
     * 집계 실패 시 예외를 throw하여 호출자가 DB fallback을 사용하게 한다.
     */
    public Map<String, Long> countTermsByField(Map<String, Object> queryParams, String field) {
        return countTermsByField(queryParams, field, 10000);
    }

    public Map<String, Long> countTermsByField(Map<String, Object> queryParams, String field, int size) {
        Query query = buildQuery(queryParams);
        try {
            return doTermsAgg(query, field, size);
        } catch (ElasticsearchException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("index_not_found") || msg.contains("no such index")) {
                return Map.of(); // 인덱스 없음 — 조용히 빈 맵 반환
            }
            // text 타입 필드 또는 all shards failed: .keyword 서브필드로 재시도
            if (!field.endsWith(".keyword")) {
                try {
                    Map<String, Long> result = doTermsAgg(query, field + ".keyword", size);
                    log.debug("[Elasticsearch] countTermsByField using '{}.keyword' succeeded", field);
                    return result;
                } catch (Exception e2) {
                    log.debug("[Elasticsearch] countTermsByField .keyword fallback also failed: field={}, err={}", field, e2.getMessage());
                }
            }
            log.warn("[Elasticsearch] countTermsByField failed: field={}, queryParams={}", field, queryParams, e);
            throw new RuntimeException("[ES] countTermsByField failed: " + field, e);
        } catch (Exception e) {
            log.warn("[Elasticsearch] countTermsByField failed: field={}, queryParams={}", field, queryParams, e);
            throw new RuntimeException("[ES] countTermsByField failed: " + field, e);
        }
    }

    private Map<String, Long> doTermsAgg(Query query, String field, int size) throws java.io.IOException {
        SearchResponse<Map> response = client.search(s -> s
                .index(INDEX)
                .size(0)
                .query(query)
                .aggregations("codes", a -> a.terms(t -> t.field(field).size(Math.max(1, size))))
                , Map.class);

        if (response.aggregations() == null || response.aggregations().get("codes") == null) {
            return Map.of();
        }

        Map<String, Long> result = new LinkedHashMap<>();
        Aggregate agg = response.aggregations().get("codes");

        if (agg.isSterms()) {
            // 문자열 필드 (keyword / text.keyword)
            StringTermsAggregate sterms = agg.sterms();
            if (sterms.buckets() == null || sterms.buckets().array() == null) return Map.of();
            for (StringTermsBucket bucket : sterms.buckets().array()) {
                String key = bucket.key().stringValue();
                if (key == null || key.trim().isEmpty()) continue;
                result.put(key, bucket.docCount());
            }
        } else if (agg.isLterms()) {
            // 숫자처럼 생긴 코드(101, 102...)가 long으로 dynamic mapping된 경우
            LongTermsAggregate lterms = agg.lterms();
            if (lterms.buckets() == null || lterms.buckets().array() == null) return Map.of();
            for (LongTermsBucket bucket : lterms.buckets().array()) {
                result.put(String.valueOf(bucket.key()), bucket.docCount());
            }
        } else {
            return Map.of();
        }

        return result;
    }

    private Query buildQuery(Map<String, Object> params) {
        List<Query> must = new ArrayList<>();
        List<Query> filter = new ArrayList<>();

        String q = buildSearchQuery(params);
        if (q != null && !q.isEmpty()) {
            must.add(QueryBuilders.multiMatch(m -> m.query(q).fields("makerName", "modelName", "trimName", "modelCode")));
        }
        if (params.get("makerCode") != null) {
            filter.add(QueryBuilders.term(t -> t.field("makerCode").value(String.valueOf(params.get("makerCode")))));
        }
        if (params.get("modelGroupCode") != null) {
            filter.add(QueryBuilders.term(t -> t.field("modelGroupCode").value(String.valueOf(params.get("modelGroupCode")))));
        }
        if (params.get("modelCode") != null) {
            filter.add(QueryBuilders.term(t -> t.field("modelCode").value(String.valueOf(params.get("modelCode")))));
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
        if (params.get("kmMax") != null) {
            filter.add(QueryBuilders.range(r -> r.field("km").lte(JsonData.of(parseInt(params.get("kmMax"), Integer.MAX_VALUE)))));
        }
        if (params.get("priceMin") != null) {
            filter.add(QueryBuilders.range(r -> r.field("priceMin").gte(JsonData.of(parseInt(params.get("priceMin"), 0)))));
        }
        if (params.get("priceMax") != null) {
            filter.add(QueryBuilders.range(r -> r.field("priceMax").lte(JsonData.of(parseInt(params.get("priceMax"), Integer.MAX_VALUE)))));
        }
        if (params.get("fuel") != null && !String.valueOf(params.get("fuel")).isEmpty()) {
            Query query = buildMultiValueFilter("fuel", normalizeQueryListValue(params.get("fuel"), this::normalizeFuelQueryValues));
            if (query != null) filter.add(query);
        }
        if (params.get("transmission") != null && !String.valueOf(params.get("transmission")).isEmpty()) {
            filter.add(QueryBuilders.term(t -> t.field("transmission").value(String.valueOf(params.get("transmission")))));
        }
        if (params.get("bodyType") != null && !String.valueOf(params.get("bodyType")).isEmpty()) {
            Query query = buildMultiValueFilter("bodyType", normalizeQueryListValue(params.get("bodyType"), this::normalizeBodyTypeQueryValues));
            if (query != null) filter.add(query);
        }
        if (params.get("color") != null && !String.valueOf(params.get("color")).isEmpty()) {
            Query query = buildMultiValueFilter("color", params.get("color"));
            if (query != null) filter.add(query);
        }
        if (params.get("region") != null && !String.valueOf(params.get("region")).isEmpty()) {
            filter.add(QueryBuilders.term(t -> t.field("region").value(String.valueOf(params.get("region")))));
        }
        if (params.get("carNo") != null && !String.valueOf(params.get("carNo")).trim().isEmpty()) {
            filter.add(QueryBuilders.term(t -> t.field("carNo").value(String.valueOf(params.get("carNo")).trim())));
        }

        if (must.isEmpty() && filter.isEmpty()) {
            return QueryBuilders.matchAll(m -> m);
        }
        return QueryBuilders.bool(b -> {
            if (!must.isEmpty()) b.must(must);
            if (!filter.isEmpty()) b.filter(filter);
            return b;
        });
    }

    private String getSortField(Map<String, Object> params) {
        String sort = params.containsKey("sort") ? String.valueOf(params.get("sort")) : null;
        if (sort == null || sort.isEmpty() || "null".equals(sort)) return null;
        if ("LOW_PRICE".equals(sort)) return "priceMin";
        if ("LOW_KM".equals(sort)) return "km";
        if ("NEW_YEAR".equals(sort)) return "year";
        if ("RECENT".equals(sort)) return "priceUpdatedAt";
        return null;
    }

    private SortOrder getSortOrder(Map<String, Object> params) {
        String sort = params.containsKey("sort") ? String.valueOf(params.get("sort")) : null;
        if ("NEW_YEAR".equals(sort)) return SortOrder.Desc;
        if ("LOW_PRICE".equals(sort) || "LOW_KM".equals(sort)) return SortOrder.Asc;
        if ("RECENT".equals(sort)) return SortOrder.Desc;
        return SortOrder.Desc;
    }

    private Query buildMultiValueFilter(String field, Object value) {
        List<String> values = splitCsvParams(value);
        if (values.isEmpty()) return null;
        if (values.size() == 1) return QueryBuilders.term(t -> t.field(field).value(values.get(0)));
        List<Query> shoulds = values.stream()
                .map(v -> QueryBuilders.term(t -> t.field(field).value(v)))
                .toList();
        return QueryBuilders.bool(b -> b.should(shoulds).minimumShouldMatch("1"));
    }

    private List<String> splitCsvParams(Object value) {
        if (value == null) return List.of();
        return Arrays.stream(String.valueOf(value).split(","))
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .toList();
    }

    private Object normalizeQueryListValue(Object value, Function<String, List<String>> normalizer) {
        if (value == null) return null;
        String raw = String.valueOf(value);
        List<String> normalized = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .flatMap(v -> normalizer.apply(v).stream())
                .filter(v -> v != null && !v.isBlank())
                .distinct()
                .toList();
        if (normalized.isEmpty()) return null;
        return String.join(",", normalized);
    }

    private List<String> normalizeFuelQueryValues(String value) {
        if (value == null || value.isBlank()) return List.of();
        String normalized = value.trim();
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (upper.equals("EV") || upper.contains("전기")) return FUEL_ELECTRIC_ALIASES;
        if (upper.contains("LPG") && upper.contains("일반인")) return FUEL_LPG_ALIASES;
        return List.of(normalized);
    }

    private List<String> normalizeBodyTypeQueryValues(String value) {
        if (value == null || value.isBlank()) return List.of();
        String normalized = value.replaceAll("\\s+", "");
        if (normalized.contains("경차")) return List.of("경차");
        if (normalized.contains("소형")) return List.of("소형");
        if (normalized.contains("준중형")) return List.of("준중형");
        if (normalized.contains("중형")) return List.of("중형");
        if (normalized.contains("대형")) return List.of("대형");
        if (normalized.contains("스포츠카")) return List.of("스포츠카");
        if (normalized.equalsIgnoreCase("RV")) return List.of("RV");
        if (normalized.equalsIgnoreCase("SUV")) return List.of("SUV");
        if (normalized.contains("승합")) return List.of("승합");
        if (normalized.contains("버스")) return List.of("버스");
        if (normalized.contains("화물") || normalized.contains("트럭") || normalized.contains("상용")) {
            return List.of("화물", "트럭", "상용", "화물차");
        }
        return List.of("기타");
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
        if (q != null && !q.isEmpty()) {
            must.add(Map.of("multi_match", Map.of("query", q, "fields", List.of("makerName", "modelName", "trimName", "modelCode"))));
        }
        if (params.get("makerCode") != null) filter.add(Map.of("term", Map.of("makerCode", params.get("makerCode"))));
        if (params.get("modelGroupCode") != null) filter.add(Map.of("term", Map.of("modelGroupCode", params.get("modelGroupCode"))));
        if (params.get("modelCode") != null) filter.add(Map.of("term", Map.of("modelCode", params.get("modelCode"))));
        if (params.get("trimCode") != null) filter.add(Map.of("term", Map.of("trimCode", params.get("trimCode"))));
        if (params.get("gradeCode") != null) filter.add(Map.of("term", Map.of("gradeCode", params.get("gradeCode"))));
        if (params.get("yearMin") != null) filter.add(Map.of("range", Map.of("year", Map.of("gte", parseInt(params.get("yearMin"), 0)))));
        if (params.get("yearMax") != null) filter.add(Map.of("range", Map.of("year", Map.of("lte", parseInt(params.get("yearMax"), Integer.MAX_VALUE)))));
        if (params.get("kmMax") != null) filter.add(Map.of("range", Map.of("km", Map.of("lte", parseInt(params.get("kmMax"), Integer.MAX_VALUE)))));
        if (params.get("priceMin") != null) filter.add(Map.of("range", Map.of("priceMin", Map.of("gte", parseInt(params.get("priceMin"), 0)))));
        if (params.get("priceMax") != null) filter.add(Map.of("range", Map.of("priceMax", Map.of("lte", parseInt(params.get("priceMax"), Integer.MAX_VALUE)))));
        if (params.get("fuel") != null && !String.valueOf(params.get("fuel")).isEmpty()) {
            List<String> fuels = splitCsvParams(params.get("fuel"));
            if (fuels.size() == 1) {
                filter.add(Map.of("term", Map.of("fuel", fuels.get(0))));
            } else if (fuels.size() > 1) {
                filter.add(buildOrTermFilterForLog("fuel", fuels));
            }
        }
        if (params.get("transmission") != null && !String.valueOf(params.get("transmission")).isEmpty()) filter.add(Map.of("term", Map.of("transmission", params.get("transmission"))));
        if (params.get("bodyType") != null && !String.valueOf(params.get("bodyType")).isEmpty()) {
            List<String> bodyTypes = splitCsvParams(params.get("bodyType"));
            if (bodyTypes.size() == 1) {
                filter.add(Map.of("term", Map.of("bodyType", bodyTypes.get(0))));
            } else if (bodyTypes.size() > 1) {
                filter.add(buildOrTermFilterForLog("bodyType", bodyTypes));
            }
        }
        if (params.get("region") != null && !String.valueOf(params.get("region")).isEmpty()) filter.add(Map.of("term", Map.of("region", params.get("region"))));
        if (params.get("carNo") != null && !String.valueOf(params.get("carNo")).trim().isEmpty()) filter.add(Map.of("term", Map.of("carNo", String.valueOf(params.get("carNo")).trim())));

        if (must.isEmpty() && filter.isEmpty()) {
            return Map.of("match_all", Map.of());
        }
        Map<String, Object> bool = new LinkedHashMap<>();
        if (!must.isEmpty()) bool.put("must", must);
        if (!filter.isEmpty()) bool.put("filter", filter);
        return Map.of("bool", bool);
    }

    private Map<String, Object> buildOrTermFilterForLog(String field, List<String> values) {
        List<Map<String, Object>> should = new ArrayList<>();
        for (String value : values) {
            should.add(Map.of("term", Map.of(field, value)));
        }
        return Map.of("bool", Map.of("should", should, "minimum_should_match", 1));
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

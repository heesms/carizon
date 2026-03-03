package com.carizon.search.service;

import com.carizon.dto.CarListItemDto;
import com.carizon.search.config.ElasticsearchConfig;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode;
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
import co.elastic.clients.elasticsearch._types.Time;
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
    private static final List<String> TEXT_SEARCH_FIELDS = List.of(
        "makerName", "modelName", "trimName", "modelCode",
        "fuel", "color", "bodyType", "region", "transmission", "carNo"
    );
    private static final List<String> TEXT_SEARCH_WILDCARD_FIELDS = List.of(
        "makerName", "modelName", "trimName", "modelCode",
        "fuel.keyword", "color.keyword", "bodyType.keyword", "region.keyword", "transmission.keyword", "carNo.keyword"
    );

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

            // 추천순(기본): function_score + random_score (하루 단위 seed로 페이지네이션 일관성 유지)
            Query effectiveQuery = query;
            if ("random".equals(sortField)) {
                long dailySeed = System.currentTimeMillis() / 86400000L;
                effectiveQuery = QueryBuilders.functionScore(fs -> fs
                    .query(query)
                    .functions(fn -> fn.randomScore(rs -> rs.seed(String.valueOf(dailySeed)).field("_seq_no")))
                    .boostMode(FunctionBoostMode.Replace)
                );
            }

            SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
                    .index(INDEX)
                    .query(effectiveQuery)
                    .from(from)
                    .size(size)
                    .trackTotalHits(t -> t.enabled(true));
            if (!"random".equals(sortField)) {
                searchBuilder.sort(s -> s.field(f -> f.field(sortField).order(sortOrder)));
            }

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
            ensureIndexWithMapping();
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
            ensureIndexWithMapping();
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
     * 단건 차량 삭제 (carId 기준)
     */
    public boolean deleteByCarId(long carId) {
        try {
            var response = client.delete(d -> d.index(INDEX).id(String.valueOf(carId)));
            boolean deleted = "deleted".equals(response.result().jsonValue());
            log.info("[Elasticsearch] deleteByCarId: carId={}, result={}", carId, response.result().jsonValue());
            return deleted;
        } catch (ElasticsearchException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("index_not_found") || msg.contains("no such index")) {
                log.warn("[Elasticsearch] deleteByCarId: index not found, carId={}", carId);
                return false;
            }
            log.error("[Elasticsearch] deleteByCarId failed: carId={}", carId, e);
            throw new RuntimeException("ES 삭제 실패: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("[Elasticsearch] deleteByCarId failed: carId={}", carId, e);
            throw new RuntimeException("ES 삭제 실패: " + e.getMessage(), e);
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
     * 인덱스를 삭제 후 고정 매핑으로 재생성한다.
     * 동적 매핑 꼬임(숫자 필드가 text로 생성되는 문제)을 방지한다.
     */
    public void resetIndexWithMapping() {
        try {
            boolean exists = client.indices().exists(e -> e.index(INDEX)).value();
            if (exists) {
                deleteIndexWithRetry();
            }
            createIndexWithMapping();
            log.info("[Elasticsearch] index [{}] created with fixed mapping", INDEX);
        } catch (Exception e) {
            log.error("[Elasticsearch] reset index with mapping failed", e);
            throw new RuntimeException("Elasticsearch 인덱스 재생성 실패", e);
        }
    }

    private void deleteIndexWithRetry() throws Exception {
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                client.indices().delete(d -> d
                    .index(INDEX)
                    .masterTimeout(Time.of(t -> t.time("60s")))
                    .timeout(Time.of(t -> t.time("60s")))
                );
                log.info("[Elasticsearch] index [{}] deleted (attempt {})", INDEX, attempt);
                return;
            } catch (Exception e) {
                log.warn("[Elasticsearch] delete attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
                if (attempt == maxRetries) throw e;
                Thread.sleep(5000L * attempt);
            }
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

    private void ensureIndexWithMapping() {
        try {
            boolean exists = client.indices().exists(e -> e.index(INDEX)).value();
            if (!exists) {
                createIndexWithMapping();
                log.info("[Elasticsearch] index [{}] created with fixed mapping (auto)", INDEX);
            }
        } catch (Exception e) {
            throw new RuntimeException("Elasticsearch 인덱스 확인/생성 실패", e);
        }
    }

    private void createIndexWithMapping() throws Exception {
        client.indices().create(c -> c
            .index(INDEX)
            .mappings(m -> m
                .properties("carId", longNumber())
                .properties("makerCode", keyword())
                .properties("modelGroupCode", keyword())
                .properties("modelCode", keyword())
                .properties("trimCode", keyword())
                .properties("gradeCode", keyword())
                .properties("makerName", textWithKeyword())
                .properties("modelGroupName", textWithKeyword())
                .properties("modelName", textWithKeyword())
                .properties("trimName", textWithKeyword())
                .properties("year", integerNumber())
                .properties("km", integerNumber())
                .properties("priceMin", integerNumber())
                .properties("priceMax", integerNumber())
                .properties("priceUpdatedAt", dateTime())
                .properties("platformCount", integerNumber())
                .properties("fuel", textWithKeyword())
                .properties("transmission", textWithKeyword())
                .properties("color", textWithKeyword())
                .properties("bodyType", textWithKeyword())
                .properties("region", textWithKeyword())
                .properties("carNo", textWithKeyword())
                .properties("representativeImageUrl", keyword())
            )
        );
    }

    private static Property keyword() {
        return Property.of(p -> p.keyword(k -> k.ignoreAbove(256)));
    }

    private static Property textWithKeyword() {
        return Property.of(p -> p.text(t -> t
            .fields("keyword", f -> f.keyword(k -> k.ignoreAbove(256)))
        ));
    }

    private static Property integerNumber() {
        return Property.of(p -> p.integer(i -> i));
    }

    private static Property longNumber() {
        return Property.of(p -> p.long_(l -> l));
    }

    private static Property dateTime() {
        return Property.of(p -> p.date(d -> d.format("strict_date_optional_time||epoch_millis")));
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
     * carId 목록으로 차량 카드 데이터를 조회.
     * 좋아요 목록 페이지에서 DB를 거치지 않고 ES 문서를 직접 사용한다.
     */
    public List<Map<String, Object>> findCarsByIds(List<Long> carIds) {
        if (carIds == null || carIds.isEmpty()) return List.of();
        try {
            LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
            for (Long carId : carIds) {
                if (carId == null || carId <= 0) continue;
                uniqueIds.add(carId);
            }
            if (uniqueIds.isEmpty()) return List.of();

            List<FieldValue> values = new ArrayList<>(uniqueIds.size());
            for (Long id : uniqueIds) values.add(FieldValue.of(id));

            SearchRequest request = new SearchRequest.Builder()
                .index(INDEX)
                .size(Math.min(values.size(), 500))
                .trackTotalHits(t -> t.enabled(false))
                .query(QueryBuilders.terms(t -> t.field("carId").terms(v -> v.value(values))))
                .build();

            SearchResponse<Map> response = client.search(request, Map.class);
            List<Map<String, Object>> out = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source == null) continue;
                Map<String, Object> item = convertToCardItem(source);
                if (item != null) out.add(item);
            }
            return out;
        } catch (ElasticsearchException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("index_not_found") || msg.contains("no such index")) {
                return List.of();
            }
            log.warn("[Elasticsearch] findCarsByIds failed", e);
            return List.of();
        } catch (Exception e) {
            log.warn("[Elasticsearch] findCarsByIds failed", e);
            return List.of();
        }
    }

    public List<Map<String, Object>> findCarsByCarNos(List<String> carNos) {
        if (carNos == null || carNos.isEmpty()) return List.of();
        try {
            LinkedHashSet<String> uniqueCarNos = new LinkedHashSet<>();
            for (String carNo : carNos) {
                if (carNo == null) continue;
                String normalized = carNo.trim();
                if (normalized.isEmpty()) continue;
                uniqueCarNos.add(normalized);
            }
            if (uniqueCarNos.isEmpty()) return List.of();

            List<FieldValue> values = new ArrayList<>(uniqueCarNos.size());
            for (String carNo : uniqueCarNos) values.add(FieldValue.of(carNo));

            SearchRequest request = new SearchRequest.Builder()
                .index(INDEX)
                .size(Math.min(values.size(), 500))
                .trackTotalHits(t -> t.enabled(false))
                .query(QueryBuilders.terms(t -> t.field("carNo.keyword").terms(v -> v.value(values))))
                .build();

            SearchResponse<Map> response = client.search(request, Map.class);
            List<Map<String, Object>> out = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source == null) continue;
                Map<String, Object> item = convertToCardItem(source);
                if (item != null) out.add(item);
            }
            return out;
        } catch (ElasticsearchException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("index_not_found") || msg.contains("no such index")) {
                return List.of();
            }
            log.warn("[Elasticsearch] findCarsByCarNos failed", e);
            return List.of();
        } catch (Exception e) {
            log.warn("[Elasticsearch] findCarsByCarNos failed", e);
            return List.of();
        }
    }

    private Query buildQuery(Map<String, Object> params) {
        List<Query> must = new ArrayList<>();
        List<Query> filter = new ArrayList<>();
        List<String> modelCodes = parseCsvValues(params.get("modelCode"));
        List<String> makerCodes = parseCsvValues(params.get("makerCodes"));
        List<String> excludeMakerCodes = parseCsvValues(params.get("excludeMakerCodes"));

        String q = buildSearchQuery(params);
        Query textSearchMust = buildTextSearchQuery(q);
        if (textSearchMust != null) {
            must.add(textSearchMust);
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
        List<String> trimCodes = parseCsvValues(params.get("trimCode"));
        if (!trimCodes.isEmpty()) {
            if (trimCodes.size() == 1) {
                filter.add(QueryBuilders.term(t -> t.field("trimCode").value(trimCodes.get(0))));
            } else {
                filter.add(QueryBuilders.bool(b -> {
                    List<Query> should = new ArrayList<>();
                    for (String trimCode : trimCodes) {
                        should.add(QueryBuilders.term(t -> t.field("trimCode").value(trimCode)));
                    }
                    b.should(should);
                    b.minimumShouldMatch("1");
                    return b;
                }));
            }
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
            // 예산 상한은 "최저가(priceMin) <= budget"으로 판단해야
            // 멀티 플랫폼 가격 범위(priceMin~priceMax)를 가진 차량이 누락되지 않는다.
            filter.add(QueryBuilders.range(r -> r.field("priceMin").lte(JsonData.of(finalPriceMax))));
        }
        List<String> fuels = parseFuelTokens(params.get("fuel"));
        if (!fuels.isEmpty()) {
            filter.add(buildTermsOrMissingQuery("fuel.keyword", "fuel", fuels));
        }
        List<String> excludeFuels = parseFuelTokens(params.get("excludeFuel"));
        if (!excludeFuels.isEmpty()) {
            Query excluded = buildTermsOrMissingQuery("fuel.keyword", "fuel", excludeFuels);
            filter.add(QueryBuilders.bool(b -> {
                b.mustNot(excluded);
                return b;
            }));
        }
        List<String> colors = parseColorTokens(params.get("color"));
        if (!colors.isEmpty()) {
            filter.add(buildTermsOrMissingQuery("color.keyword", "color", colors));
        }
        List<String> excludeColors = parseColorTokens(params.get("excludeColor"));
        if (!excludeColors.isEmpty()) {
            Query excluded = buildTermsOrMissingQuery("color.keyword", "color", excludeColors);
            filter.add(QueryBuilders.bool(b -> {
                b.mustNot(excluded);
                return b;
            }));
        }
        List<String> transmissions = parseTransmissionTokens(params.get("transmission"));
        if (!transmissions.isEmpty()) {
            filter.add(buildTermsOrMissingQuery("transmission.keyword", "transmission", transmissions));
        }
        List<String> bodyTypes = parseBodyTypeTokens(params.get("bodyType"));
        if (!bodyTypes.isEmpty()) {
            filter.add(buildTermsOrMissingQuery("bodyType.keyword", "bodyType", bodyTypes));
        }
        List<String> excludeBodyTypes = parseBodyTypeTokens(params.get("excludeBodyType"));
        if (!excludeBodyTypes.isEmpty()) {
            Query excluded = buildTermsOrMissingQuery("bodyType.keyword", "bodyType", excludeBodyTypes);
            filter.add(QueryBuilders.bool(b -> {
                b.mustNot(excluded);
                return b;
            }));
        }
        List<String> regions = parseCsvValues(params.get("region"));
        if (!regions.isEmpty()) {
            filter.add(buildRegionPrefixQuery(regions));
        }
        List<String> excludeRegions = parseCsvValues(params.get("excludeRegion"));
        if (!excludeRegions.isEmpty()) {
            Query excluded = buildRegionPrefixQuery(excludeRegions);
            filter.add(QueryBuilders.bool(b -> {
                b.mustNot(excluded);
                return b;
            }));
        }
        if (params.get("carNo") != null && !String.valueOf(params.get("carNo")).trim().isEmpty()) {
            String carNoVal = String.valueOf(params.get("carNo")).trim();
            if (carNoVal.length() >= 4) {
                filter.add(QueryBuilders.wildcard(w -> w.field("carNo.keyword").value("*" + carNoVal + "*").caseInsensitive(true)));
            } else {
                filter.add(QueryBuilders.term(t -> t.field("carNo.keyword").value(carNoVal)));
            }
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
        if (sort == null || sort.isEmpty() || "null".equals(sort)) return "random";
        if ("LOW_PRICE".equals(sort)) return "priceMin";
        if ("LOW_KM".equals(sort)) return "km";
        if ("NEW_YEAR".equals(sort)) return "year";
        if ("RECENT".equals(sort)) return "priceUpdatedAt";
        return "random";
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
        Map<String, Object> textSearchMust = buildTextSearchClauseMap(q);
        if (!textSearchMust.isEmpty()) {
            must.add(textSearchMust);
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
        List<String> trimCodes = parseCsvValues(params.get("trimCode"));
        if (!trimCodes.isEmpty()) {
            if (trimCodes.size() == 1) filter.add(Map.of("term", Map.of("trimCode", trimCodes.get(0))));
            else filter.add(Map.of("terms", Map.of("trimCode", trimCodes)));
        }
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
        if (priceMax != null) {
            filter.add(Map.of("range", Map.of("priceMin", Map.of("lte", priceMax))));
        }
        List<String> fuels = parseFuelTokens(params.get("fuel"));
        if (!fuels.isEmpty()) {
            filter.add(buildTermsOrMissingMap("fuel.keyword", "fuel", fuels));
        }
        List<String> excludeFuels = parseFuelTokens(params.get("excludeFuel"));
        if (!excludeFuels.isEmpty()) {
            filter.add(Map.of("bool", Map.of(
                "must_not", List.of(buildTermsOrMissingMap("fuel.keyword", "fuel", excludeFuels))
            )));
        }
        List<String> colors = parseColorTokens(params.get("color"));
        if (!colors.isEmpty()) {
            filter.add(buildTermsOrMissingMap("color.keyword", "color", colors));
        }
        List<String> excludeColors = parseColorTokens(params.get("excludeColor"));
        if (!excludeColors.isEmpty()) {
            filter.add(Map.of("bool", Map.of(
                "must_not", List.of(buildTermsOrMissingMap("color.keyword", "color", excludeColors))
            )));
        }
        List<String> transmissionsLog = parseTransmissionTokens(params.get("transmission"));
        if (!transmissionsLog.isEmpty()) filter.add(buildTermsOrMissingMap("transmission.keyword", "transmission", transmissionsLog));
        List<String> bodyTypes = parseBodyTypeTokens(params.get("bodyType"));
        if (!bodyTypes.isEmpty()) {
            filter.add(buildTermsOrMissingMap("bodyType.keyword", "bodyType", bodyTypes));
        }
        List<String> excludeBodyTypes = parseBodyTypeTokens(params.get("excludeBodyType"));
        if (!excludeBodyTypes.isEmpty()) {
            filter.add(Map.of("bool", Map.of(
                "must_not", List.of(buildTermsOrMissingMap("bodyType.keyword", "bodyType", excludeBodyTypes))
            )));
        }
        List<String> regionsLog = parseCsvValues(params.get("region"));
        if (!regionsLog.isEmpty()) {
            List<Map<String, Object>> should = new ArrayList<>();
            for (String region : regionsLog) {
                should.add(Map.of("prefix", Map.of("region.keyword", region)));
            }
            filter.add(should.size() == 1 ? should.get(0) : Map.of("bool", Map.of("should", should, "minimum_should_match", "1")));
        }
        List<String> excludeRegionsLog = parseCsvValues(params.get("excludeRegion"));
        if (!excludeRegionsLog.isEmpty()) {
            List<Map<String, Object>> mustNot = new ArrayList<>();
            for (String region : excludeRegionsLog) {
                mustNot.add(Map.of("prefix", Map.of("region.keyword", region)));
            }
            filter.add(Map.of("bool", Map.of("must_not", mustNot)));
        }
        if (params.get("carNo") != null && !String.valueOf(params.get("carNo")).trim().isEmpty()) {
            String carNoVal = String.valueOf(params.get("carNo")).trim();
            if (carNoVal.length() >= 4) {
                filter.add(Map.of("wildcard", Map.of("carNo.keyword", Map.of("value", "*" + carNoVal + "*", "case_insensitive", true))));
            } else {
                filter.add(Map.of("term", Map.of("carNo.keyword", carNoVal)));
            }
        }

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
        out.put("fuel", normalizeFuelType(asNullableString(out.get("fuel"))));
        out.put("color", normalizeColorType(asNullableString(out.get("color"))));
        out.put("bodyType", normalizeBodyType(asNullableString(out.get("bodyType"))));
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

    private static Query buildTextSearchQuery(String rawQuery) {
        if (rawQuery == null) return null;
        String query = rawQuery.trim();
        if (query.isEmpty()) return null;

        List<Query> should = new ArrayList<>();
        should.add(QueryBuilders.multiMatch(m -> m.query(query).fields(TEXT_SEARCH_FIELDS)));
        for (String field : TEXT_SEARCH_WILDCARD_FIELDS) {
            should.add(QueryBuilders.wildcard(w -> w.field(field).value("*" + query + "*").caseInsensitive(true)));
        }

        return QueryBuilders.bool(b -> {
            b.should(should);
            b.minimumShouldMatch("1");
            return b;
        });
    }

    private static Map<String, Object> buildTextSearchClauseMap(String rawQuery) {
        if (rawQuery == null) return Map.of();
        String query = rawQuery.trim();
        if (query.isEmpty()) return Map.of();

        List<Map<String, Object>> should = new ArrayList<>();
        should.add(Map.of("multi_match", Map.of("query", query, "fields", TEXT_SEARCH_FIELDS)));
        for (String field : TEXT_SEARCH_WILDCARD_FIELDS) {
            should.add(Map.of(
                "wildcard", Map.of(
                    field, Map.of(
                        "value", "*" + query + "*",
                        "case_insensitive", true
                    )
                )
            ));
        }

        return Map.of("bool", Map.of(
            "should", should,
            "minimum_should_match", "1"
        ));
    }

    private static Query buildTermsOrMissingQuery(String keywordField, String existsField, List<String> values) {
        List<Query> should = new ArrayList<>();
        for (String value : values) {
            should.add(QueryBuilders.term(t -> t.field(keywordField).value(value)));
        }
        if (containsEtc(values)) {
            should.add(QueryBuilders.bool(b -> b.mustNot(QueryBuilders.exists(e -> e.field(existsField)))));
        }
        if (should.size() == 1) return should.get(0);
        return QueryBuilders.bool(b -> {
            b.should(should);
            b.minimumShouldMatch("1");
            return b;
        });
    }

    private static Query buildKeywordTermsQuery(String keywordField, List<String> values) {
        if (values == null || values.isEmpty()) {
            return QueryBuilders.matchAll(m -> m);
        }
        if (values.size() == 1) {
            return QueryBuilders.term(t -> t.field(keywordField).value(values.get(0)));
        }
        return QueryBuilders.bool(b -> {
            List<Query> should = new ArrayList<>();
            for (String value : values) {
                should.add(QueryBuilders.term(t -> t.field(keywordField).value(value)));
            }
            b.should(should);
            b.minimumShouldMatch("1");
            return b;
        });
    }

    /**
     * 지역 prefix 쿼리 빌더.
     * DB에는 "경기 수원시 권선구" 형태로 저장되므로 "경기" 등 광역 단위 값도 매칭되도록 prefix 쿼리 사용.
     */
    private static Query buildRegionPrefixQuery(List<String> regions) {
        if (regions == null || regions.isEmpty()) return QueryBuilders.matchAll(m -> m);
        if (regions.size() == 1) {
            final String region = regions.get(0);
            return QueryBuilders.prefix(p -> p.field("region.keyword").value(region));
        }
        return QueryBuilders.bool(b -> {
            List<Query> should = new ArrayList<>();
            for (String region : regions) {
                should.add(QueryBuilders.prefix(p -> p.field("region.keyword").value(region)));
            }
            b.should(should);
            b.minimumShouldMatch("1");
            return b;
        });
    }

    private static Map<String, Object> buildTermsOrMissingMap(String keywordField, String existsField, List<String> values) {
        List<Map<String, Object>> should = new ArrayList<>();
        for (String value : values) {
            should.add(Map.of("term", Map.of(keywordField, value)));
        }
        if (containsEtc(values)) {
            should.add(Map.of("bool", Map.of(
                "must_not", List.of(Map.of("exists", Map.of("field", existsField)))
            )));
        }
        if (should.size() == 1) return should.get(0);
        return Map.of("bool", Map.of(
            "should", should,
            "minimum_should_match", "1"
        ));
    }

    private static boolean containsEtc(List<String> values) {
        if (values == null || values.isEmpty()) return false;
        for (String value : values) {
            if ("기타".equals(value)) return true;
        }
        return false;
    }

    private static List<String> parseBodyTypeTokens(Object value) {
        List<String> values = parseCsvValues(value);
        if (values.isEmpty()) return List.of();

        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String item : values) {
            String normalized = normalizeBodyType(item);
            if (normalized == null || normalized.isBlank()) continue;
            addLegacyBodyTypeTokens(tokens, normalized);
        }
        return new ArrayList<>(tokens);
    }

    private static void addLegacyBodyTypeTokens(Set<String> out, String normalized) {
        out.add(normalized);
        switch (normalized) {
            case "SUV" -> Collections.addAll(out, "suv");
            case "RV" -> Collections.addAll(out, "rv");
            case "준중형" -> Collections.addAll(out, "준중형차");
            case "중형" -> Collections.addAll(out, "중형차");
            case "대형" -> Collections.addAll(out, "대형차");
            case "소형" -> Collections.addAll(out, "소형차");
            case "승합" -> Collections.addAll(out, "승합차", "경승합차");
            case "화물" -> Collections.addAll(out, "화물차", "트럭");
            case "스포츠카" -> Collections.addAll(out, "쿠페", "컨버터블", "sportscar", "sports car");
            case "기타" -> Collections.addAll(out, "null");
            default -> {
            }
        }
    }

    private static List<String> parseTransmissionTokens(Object value) {
        List<String> values = parseCsvValues(value);
        if (values.isEmpty()) return List.of();
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String item : values) {
            String normalized = normalizeTransmissionType(item);
            if (normalized == null || normalized.isBlank()) continue;
            // "오토"와 "자동" 둘 다 자동변속기를 의미하므로 둘 다 포함
            if ("오토".equals(normalized) || "자동".equals(normalized)) {
                tokens.add("오토");
                tokens.add("자동");
            } else {
                tokens.add(normalized);
            }
        }
        return new ArrayList<>(tokens);
    }

    private static String normalizeTransmissionType(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isBlank() || "null".equalsIgnoreCase(v)) return null;
        String lower = v.toLowerCase(Locale.ROOT);
        if (lower.equals("오토") || lower.equals("자동") || lower.equals("at")
                || lower.contains("automatic") || lower.equals("자동변속기")) return "오토";
        if (lower.equals("수동") || lower.equals("mt") || lower.contains("manual") || lower.equals("수동변속기")) return "수동";
        if (lower.equals("cvt")) return "CVT";
        if (lower.equals("세미오토") || lower.contains("semi") || lower.equals("dct") || lower.equals("dsg")) return "세미오토";
        return v;
    }

    private static List<String> parseFuelTokens(Object value) {
        List<String> values = parseCsvValues(value);
        if (values.isEmpty()) return List.of();

        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String item : values) {
            String normalized = normalizeFuelType(item);
            if (normalized == null || normalized.isBlank()) continue;
            addLegacyFuelTokens(tokens, normalized);
        }
        return new ArrayList<>(tokens);
    }

    private static void addLegacyFuelTokens(Set<String> out, String normalized) {
        out.add(normalized);
        switch (normalized) {
            case "LPG" -> Collections.addAll(out, "LPG(일반인)", "LPG(일반인 구입)");
            case "하이브리드" -> Collections.addAll(out, "하이브리드(가솔린)", "하이브리드(디젤)", "하이브리드(LPG)");
            case "기타" -> Collections.addAll(out, "null");
            default -> {
            }
        }
    }

    private static List<String> parseColorTokens(Object value) {
        List<String> values = parseCsvValues(value);
        if (values.isEmpty()) return List.of();

        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String item : values) {
            String normalized = normalizeColorType(item);
            if (normalized == null || normalized.isBlank()) continue;
            addLegacyColorTokens(tokens, normalized);
        }
        return new ArrayList<>(tokens);
    }

    private static void addLegacyColorTokens(Set<String> out, String normalized) {
        out.add(normalized);
        switch (normalized) {
            case "회색" -> Collections.addAll(out, "쥐색");
            case "하늘색" -> Collections.addAll(out, "하늘");
            case "파랑색" -> Collections.addAll(out, "파랑", "파란색", "청색", "남색");
            case "초록색" -> Collections.addAll(out, "청옥색", "연두색", "담녹색", "녹색");
            case "진주색투톤" -> Collections.addAll(out, "진주투톤");
            case "진주색" -> Collections.addAll(out, "진주");
            case "주황색" -> Collections.addAll(out, "주황");
            case "보라색" -> Collections.addAll(out, "보라", "자주색");
            case "은색" -> Collections.addAll(out, "은회색", "은하색", "명은색");
            case "금색" -> Collections.addAll(out, "연금색");
            case "빨강색" -> Collections.addAll(out, "빨강", "빨간색");
            case "분홍색" -> Collections.addAll(out, "분홍");
            case "노랑색" -> Collections.addAll(out, "노랑", "노란색");
            case "미색" -> Collections.addAll(out, "갈대색");
            case "검정색" -> Collections.addAll(out, "검정");
            case "기타" -> Collections.addAll(out, "인기색상", "null");
            default -> {
            }
        }
    }

    private static String normalizeColorType(String raw) {
        if (raw == null) return "기타";
        String v = raw.trim();
        if (v.isBlank()) return "기타";
        if ("null".equalsIgnoreCase(v)) return "기타";
        return switch (v) {
            case "흰색투톤" -> "흰색투톤";
            case "흰색", "흰", "화이트", "WHITE", "White", "white" -> "흰색";
            case "회색", "쥐색", "그레이", "그레이색", "GREY", "GRAY", "Grey", "Gray", "grey", "gray" -> "회색";
            case "하늘색", "하늘" -> "하늘색";
            case "파랑색", "파랑", "파란색", "청색", "남색", "블루", "블루색", "BLUE", "Blue", "blue" -> "파랑색";
            case "초록색", "청옥색", "연두색", "담녹색", "녹색", "그린", "그린색", "GREEN", "Green", "green" -> "초록색";
            case "진주색투톤", "진주투톤" -> "진주색투톤";
            case "진주색", "진주" -> "진주색";
            case "주황색", "주황", "오렌지", "오렌지색", "ORANGE", "Orange", "orange" -> "주황색";
            case "보라색", "보라", "자주색", "퍼플", "PURPLE", "Purple", "purple", "바이올렛" -> "보라색";
            case "은색", "은회색", "은하색", "명은색", "실버", "SILVER", "Silver", "silver" -> "은색";
            case "은색투톤" -> "은색투톤";
            case "금색", "연금색" -> "금색";
            case "금색투톤" -> "금색투톤";
            case "빨강색", "빨강", "빨간색", "레드", "RED", "Red", "red" -> "빨강색";
            case "분홍색", "분홍", "핑크", "핑크색", "PINK", "Pink", "pink" -> "분홍색";
            case "미색", "갈대색", "베이지", "아이보리", "크림", "BEIGE", "Beige", "beige" -> "미색";
            case "노랑색", "노랑", "노란색", "옐로우", "YELLOW", "Yellow", "yellow" -> "노랑색";
            case "검정투톤" -> "검정투톤";
            case "검정색", "검정", "블랙", "BLACK", "Black", "black" -> "검정색";
            case "갈색투톤" -> "갈색투톤";
            case "갈색" -> "갈색";
            case "기타", "인기색상" -> "기타";
            default -> "기타";
        };
    }

    private static String normalizeBodyType(String raw) {
        if (raw == null) return "기타";
        String v = raw.trim();
        if (v.isBlank() || "null".equalsIgnoreCase(v)) return "기타";

        String lower = v.toLowerCase(Locale.ROOT);
        if ("suv".equals(lower)) return "SUV";
        if ("rv".equals(lower)) return "RV";

        return switch (v) {
            case "경차", "소형", "준중형", "중형", "대형", "SUV", "RV",
                "승합", "스포츠카", "트럭", "화물", "상용", "버스", "기타" -> v;
            case "준중형차" -> "준중형";
            case "중형차", "중대형" -> "중형";
            case "대형차" -> "대형";
            case "소형차" -> "소형";
            case "승합차", "경승합차" -> "승합";
            case "화물차" -> "화물";
            case "스포츠카/쿠페" -> "스포츠카";
            default -> "기타";
        };
    }

    private static String normalizeFuelType(String raw) {
        if (raw == null) return "기타";
        String trimmed = raw.trim();
        if (trimmed.isBlank() || "null".equalsIgnoreCase(trimmed)) return "기타";
        String v = trimmed.toLowerCase(Locale.ROOT);
        if (v.contains("lpg")) return "LPG";
        if (v.contains("전기") || v.contains("electric") || "ev".equals(v)) return "전기";
        if (v.contains("하이브리드") || v.contains("hybrid")) return "하이브리드";
        if (v.contains("디젤") || v.contains("경유") || v.contains("diesel")) return "디젤";
        if (v.contains("가솔린") || v.contains("휘발유") || v.contains("gasoline") || v.contains("petrol")) return "가솔린";
        if ("기타".equals(trimmed)) return "기타";
        return trimmed;
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

    private static Map<String, Object> convertToCardItem(Map<String, Object> hit) {
        long carId = parseLong(hit.get("carId"), 0L);
        if (carId <= 0) return null;
        String carNo = getString(hit, "carNo");
        if (carNo == null || carNo.isBlank()) return null;

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("carId", carId);
        item.put("carNo", carNo);
        item.put("maker", firstNonBlank(getString(hit, "makerName"), getString(hit, "maker")));
        item.put("model", firstNonBlank(getString(hit, "modelName"), getString(hit, "model")));
        item.put("trim", firstNonBlank(getString(hit, "trimName"), getString(hit, "trim")));
        item.put("year", getInteger(hit, "year"));
        item.put("km", getInteger(hit, "km"));
        item.put("priceMin", getInteger(hit, "priceMin"));
        item.put("priceMax", getInteger(hit, "priceMax"));
        item.put("representativeImageUrl", getString(hit, "representativeImageUrl"));
        item.put("modelCode", getString(hit, "modelCode"));
        item.put("fuel", getString(hit, "fuel"));
        item.put("region", getString(hit, "region"));
        return item;
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

    private static String firstNonBlank(String a, String b) {
        String ta = asNullableString(a);
        if (ta != null) return ta;
        return asNullableString(b);
    }

    private static String asNullableString(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
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

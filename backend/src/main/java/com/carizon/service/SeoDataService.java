package com.carizon.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.carizon.dto.SeoModelDto;
import com.carizon.search.config.ElasticsearchConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeoDataService {

    private final JdbcTemplate jdbc;
    private final ElasticsearchClient esClient;

    private static final String INDEX = ElasticsearchConfig.CARS_INDEX;

    /** DB에서 모델 메타데이터만 조회 (집계 없음) */
    private static final String META_SQL = """
        SELECT
            mo.model_code  AS modelCode,
            mo.model_name  AS modelName,
            mo.maker_code  AS makerCode,
            mk.maker_name  AS makerName,
            es.embed_text_3 AS description,
            mi.image_url   AS imageUrl
        FROM cz_model mo
        LEFT JOIN cz_maker mk ON mk.maker_code COLLATE utf8mb4_unicode_ci = mo.maker_code
        LEFT JOIN cz_model_embedding_source es ON es.model_code COLLATE utf8mb4_unicode_ci = mo.model_code
        LEFT JOIN (
            SELECT model_code COLLATE utf8mb4_unicode_ci AS model_code, image_url
            FROM cz_model_image
            WHERE model_code = ?
            ORDER BY is_main DESC, sort_order ASC
            LIMIT 1
        ) mi ON mi.model_code = mo.model_code
        WHERE mo.model_code = ?
    """;

    private static final RowMapper<SeoModelDto> META_MAPPER = (rs, i) -> new SeoModelDto(
            rs.getString("modelCode"),
            rs.getString("modelName"),
            rs.getString("makerCode"),
            rs.getString("makerName"),
            rs.getString("description"),
            rs.getString("imageUrl"),
            0L, null, null
    );

    /**
     * 모델 SEO 페이지용 데이터.
     * DB: 모델 메타데이터(이름/설명/이미지) — cz_model 소규모 테이블만 조회
     * ES:  매물수(count) + 가격 범위(min/max) 집계
     */
    public Optional<SeoModelDto> getModelSeo(String modelCode, String makerCode) {
        if (modelCode == null || modelCode.isBlank()) return Optional.empty();

        boolean hasMaker = makerCode != null && !makerCode.isBlank();
        String mc = modelCode.trim();

        // 1) DB 메타데이터 조회
        String metaSql = META_SQL + (hasMaker ? " AND mo.maker_code = ?" : "") + " LIMIT 1";
        List<SeoModelDto> metas = hasMaker
                ? jdbc.query(metaSql, META_MAPPER, mc, mc, makerCode.trim())
                : jdbc.query(metaSql, META_MAPPER, mc, mc);
        if (metas.isEmpty()) return Optional.empty();
        SeoModelDto meta = metas.get(0);

        // 2) ES 집계: carCount + priceMin + priceMax
        try {
            var boolQ = BoolQuery.of(b -> {
                b.filter(QueryBuilders.term(t -> t.field("modelCode").value(mc)));
                if (hasMaker) b.filter(QueryBuilders.term(t -> t.field("makerCode").value(makerCode.trim())));
                return b;
            });

            SearchResponse<Void> resp = esClient.search(s -> s
                    .index(INDEX)
                    .size(0)
                    .query(q -> q.bool(boolQ))
                    .aggregations("price_min", a -> a.min(m -> m.field("priceMin")))
                    .aggregations("price_max", a -> a.max(m -> m.field("priceMax"))),
                    Void.class);

            long count = resp.hits().total() != null ? resp.hits().total().value() : 0L;
            double rawMin = resp.aggregations().get("price_min").min().value();
            double rawMax = resp.aggregations().get("price_max").max().value();
            Integer priceMin = (Double.isInfinite(rawMin) || Double.isNaN(rawMin) || rawMin <= 0) ? null : (int) rawMin;
            Integer priceMax = (Double.isInfinite(rawMax) || Double.isNaN(rawMax) || rawMax <= 0) ? null : (int) rawMax;

            return Optional.of(new SeoModelDto(
                    meta.getModelCode(), meta.getModelName(), meta.getMakerCode(), meta.getMakerName(),
                    meta.getDescription(), meta.getImageUrl(), count, priceMin, priceMax));

        } catch (Exception e) {
            log.warn("[SeoDataService] ES aggregation failed for modelCode={}: {}", mc, e.getMessage());
            return Optional.of(meta);
        }
    }

    /**
     * 매물이 있는 모델 목록 (makerCode 필터 선택).
     * ES terms 집계로 carCount/가격 집계 → DB IN 쿼리로 메타데이터 보완.
     */
    public List<SeoModelDto> getModelsWithDescription(String makerCode, int limit) {
        int safeLimit = Math.min(limit, 500);
        boolean hasMaker = makerCode != null && !makerCode.isBlank();

        try {
            // 1) ES: modelCode별 count + 가격 집계
            final String makerCodeFinal = hasMaker ? makerCode.trim() : null;
            SearchResponse<Void> resp = esClient.search(s -> {
                s.index(INDEX).size(0);
                if (hasMaker) s.query(q -> q.term(t -> t.field("makerCode").value(makerCodeFinal)));
                s.aggregations("by_model", a -> a
                        .terms(t -> t.field("modelCode").size(safeLimit))
                        .aggregations("price_min", pa -> pa.min(m -> m.field("priceMin")))
                        .aggregations("price_max", pa -> pa.max(m -> m.field("priceMax")))
                );
                return s;
            }, Void.class);

            var buckets = resp.aggregations().get("by_model").sterms().buckets().array();
            if (buckets.isEmpty()) return List.of();

            // 2) DB: IN 쿼리로 메타데이터 조회
            List<String> modelCodes = buckets.stream()
                    .map(b -> b.key().stringValue())
                    .collect(Collectors.toList());

            String inClause = modelCodes.stream().map(c -> "?").collect(Collectors.joining(","));
            String metaListSql = """
                SELECT
                    mo.model_code  AS modelCode,
                    mo.model_name  AS modelName,
                    mo.maker_code  AS makerCode,
                    mk.maker_name  AS makerName,
                    es.embed_text_3 AS description,
                    mi.image_url   AS imageUrl
                FROM cz_model mo
                LEFT JOIN cz_maker mk ON mk.maker_code COLLATE utf8mb4_unicode_ci = mo.maker_code
                LEFT JOIN cz_model_embedding_source es ON es.model_code COLLATE utf8mb4_unicode_ci = mo.model_code
                LEFT JOIN (
                    SELECT model_code COLLATE utf8mb4_unicode_ci AS model_code,
                           image_url,
                           ROW_NUMBER() OVER (PARTITION BY model_code ORDER BY is_main DESC, sort_order ASC) AS rn
                    FROM cz_model_image
                    WHERE model_code IN (""" + inClause + """
                    )
                ) mi ON mi.model_code = mo.model_code AND mi.rn = 1
                WHERE mo.model_code IN (""" + inClause + ")";

            Object[] params = new Object[modelCodes.size() * 2];
            for (int i = 0; i < modelCodes.size(); i++) {
                params[i] = modelCodes.get(i);
                params[modelCodes.size() + i] = modelCodes.get(i);
            }

            List<SeoModelDto> metaList = jdbc.query(metaListSql, META_MAPPER, params);
            Map<String, SeoModelDto> metaByCode = metaList.stream()
                    .collect(Collectors.toMap(SeoModelDto::getModelCode, m -> m, (a, b) -> a));

            // 3) ES 집계 결과와 메타데이터 병합 (bucket 순서 = carCount 내림차순)
            List<SeoModelDto> result = new ArrayList<>();
            for (var bucket : buckets) {
                String code = bucket.key().stringValue();
                SeoModelDto base = metaByCode.get(code);
                if (base == null) continue;

                long count = bucket.docCount();
                double rawMin = bucket.aggregations().get("price_min").min().value();
                double rawMax = bucket.aggregations().get("price_max").max().value();
                Integer pMin = (Double.isInfinite(rawMin) || rawMin <= 0) ? null : (int) rawMin;
                Integer pMax = (Double.isInfinite(rawMax) || rawMax <= 0) ? null : (int) rawMax;

                result.add(new SeoModelDto(
                        base.getModelCode(), base.getModelName(), base.getMakerCode(), base.getMakerName(),
                        base.getDescription(), base.getImageUrl(), count, pMin, pMax));
            }
            return result;

        } catch (Exception e) {
            log.warn("[SeoDataService] ES models aggregation failed: {}", e.getMessage());
            return List.of();
        }
    }
}

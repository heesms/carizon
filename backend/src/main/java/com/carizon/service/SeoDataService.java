package com.carizon.service;

import com.carizon.dto.SeoModelDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SeoDataService {

    private final JdbcTemplate jdbc;

    /**
     * 모델 SEO 페이지용 데이터.
     * cz_model + cz_maker + cz_model_embedding_source + cz_model_image + car_master 집계
     */
    public Optional<SeoModelDto> getModelSeo(String modelCode, String makerCode) {
        if (modelCode == null || modelCode.isBlank()) return Optional.empty();

        boolean hasMaker = makerCode != null && !makerCode.isBlank();
        String sql = """
            SELECT
                mo.model_code        AS modelCode,
                mo.model_name        AS modelName,
                mo.maker_code        AS makerCode,
                mk.maker_name        AS makerName,
                es.embed_text_3      AS description,
                mi.image_url         AS imageUrl,
                COALESCE(agg.carCount, 0) AS carCount,
                agg.priceMin         AS priceMin,
                agg.priceMax         AS priceMax
            FROM cz_model mo
            LEFT JOIN cz_maker mk ON mk.maker_code COLLATE utf8mb4_unicode_ci = mo.maker_code
            LEFT JOIN cz_model_embedding_source es ON es.model_code COLLATE utf8mb4_unicode_ci = mo.model_code
            LEFT JOIN (
                SELECT model_code COLLATE utf8mb4_unicode_ci AS model_code, is_main, image_url,
                       ROW_NUMBER() OVER (PARTITION BY model_code ORDER BY is_main DESC, sort_order ASC) AS rn
                FROM cz_model_image
            ) mi ON mi.model_code = mo.model_code AND mi.rn = 1
            LEFT JOIN (
                SELECT cm.model_code COLLATE utf8mb4_unicode_ci AS model_code,
                       COUNT(*) AS carCount,
                       MIN(pc.price) AS priceMin,
                       MAX(pc.price) AS priceMax
                FROM car_master cm
                LEFT JOIN platform_car pc ON pc.car_id = cm.car_id AND pc.price > 0
                WHERE cm.adv_status = 'ONSALE'
                GROUP BY cm.model_code
            ) agg ON agg.model_code = mo.model_code
            WHERE mo.model_code = ?
        """ + (hasMaker ? " AND mo.maker_code = ?" : "") + " LIMIT 1";

        var mapper = (org.springframework.jdbc.core.RowMapper<SeoModelDto>) (rs, i) -> new SeoModelDto(
                rs.getString("modelCode"),
                rs.getString("modelName"),
                rs.getString("makerCode"),
                rs.getString("makerName"),
                rs.getString("description"),
                rs.getString("imageUrl"),
                rs.getLong("carCount"),
                (Integer) rs.getObject("priceMin"),
                (Integer) rs.getObject("priceMax")
        );

        List<SeoModelDto> results = hasMaker
                ? jdbc.query(sql, mapper, modelCode.trim(), makerCode.trim())
                : jdbc.query(sql, mapper, modelCode.trim());

        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * 매물이 있는 모델 목록 (maker 기준 필터, embed_text3 조건 제거)
     */
    public List<SeoModelDto> getModelsWithDescription(String makerCode, int limit) {
        String sql = """
            SELECT
                mo.model_code        AS modelCode,
                mo.model_name        AS modelName,
                mo.maker_code        AS makerCode,
                mk.maker_name        AS makerName,
                es.embed_text_3      AS description,
                mi.image_url         AS imageUrl,
                agg.carCount         AS carCount,
                agg.priceMin         AS priceMin,
                agg.priceMax         AS priceMax
            FROM cz_model mo
            INNER JOIN (
                SELECT cm.model_code COLLATE utf8mb4_unicode_ci AS model_code,
                       COUNT(*) AS carCount,
                       MIN(pc.price) AS priceMin,
                       MAX(pc.price) AS priceMax
                FROM car_master cm
                LEFT JOIN platform_car pc ON pc.car_id = cm.car_id AND pc.price > 0
                WHERE cm.adv_status = 'ONSALE'
                GROUP BY cm.model_code
                HAVING COUNT(*) > 0
            ) agg ON agg.model_code = mo.model_code
            LEFT JOIN cz_maker mk ON mk.maker_code COLLATE utf8mb4_unicode_ci = mo.maker_code
            LEFT JOIN cz_model_embedding_source es ON es.model_code COLLATE utf8mb4_unicode_ci = mo.model_code
            LEFT JOIN (
                SELECT model_code COLLATE utf8mb4_unicode_ci AS model_code, image_url,
                       ROW_NUMBER() OVER (PARTITION BY model_code ORDER BY is_main DESC, sort_order ASC) AS rn
                FROM cz_model_image
            ) mi ON mi.model_code = mo.model_code AND mi.rn = 1
        """ + (makerCode != null ? " WHERE mo.maker_code = ? " : "")
            + " ORDER BY agg.carCount DESC LIMIT ?";

        if (makerCode != null) {
            return jdbc.query(sql, (rs, i) -> new SeoModelDto(
                    rs.getString("modelCode"),
                    rs.getString("modelName"),
                    rs.getString("makerCode"),
                    rs.getString("makerName"),
                    rs.getString("description"),
                    rs.getString("imageUrl"),
                    rs.getLong("carCount"),
                    (Integer) rs.getObject("priceMin"),
                    (Integer) rs.getObject("priceMax")
            ), makerCode.trim(), Math.min(limit, 500));
        } else {
            return jdbc.query(sql, (rs, i) -> new SeoModelDto(
                    rs.getString("modelCode"),
                    rs.getString("modelName"),
                    rs.getString("makerCode"),
                    rs.getString("makerName"),
                    rs.getString("description"),
                    rs.getString("imageUrl"),
                    rs.getLong("carCount"),
                    (Integer) rs.getObject("priceMin"),
                    (Integer) rs.getObject("priceMax")
            ), Math.min(limit, 500));
        }
    }
}

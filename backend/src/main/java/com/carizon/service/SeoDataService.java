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
    public Optional<SeoModelDto> getModelSeo(String modelCode) {
        if (modelCode == null || modelCode.isBlank()) return Optional.empty();

        List<SeoModelDto> results = jdbc.query("""
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
            LEFT JOIN cz_maker mk ON mk.maker_code = mo.maker_code
            LEFT JOIN cz_model_embedding_source es ON es.model_code = mo.model_code
            LEFT JOIN (
                SELECT model_code, is_main, image_url,
                       ROW_NUMBER() OVER (PARTITION BY model_code ORDER BY is_main DESC, sort_order ASC) AS rn
                FROM cz_model_image
            ) mi ON mi.model_code = mo.model_code AND mi.rn = 1
            LEFT JOIN (
                SELECT model_code,
                       COUNT(*) AS carCount,
                       MIN(price_min) AS priceMin,
                       MAX(price_max) AS priceMax
                FROM car_master
                WHERE adv_status = 'ONSALE'
                GROUP BY model_code
            ) agg ON agg.model_code = mo.model_code
            WHERE mo.model_code = ?
            LIMIT 1
        """, (rs, i) -> new SeoModelDto(
                rs.getString("modelCode"),
                rs.getString("modelName"),
                rs.getString("makerCode"),
                rs.getString("makerName"),
                rs.getString("description"),
                rs.getString("imageUrl"),
                rs.getLong("carCount"),
                (Integer) rs.getObject("priceMin"),
                (Integer) rs.getObject("priceMax")
        ), modelCode.trim());

        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * embed_text3 있는 모델 목록 (maker 기준 필터, sitemap/SEO 페이지 생성용)
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
                COALESCE(agg.carCount, 0) AS carCount,
                agg.priceMin         AS priceMin,
                agg.priceMax         AS priceMax
            FROM cz_model mo
            INNER JOIN cz_model_embedding_source es ON es.model_code = mo.model_code
                AND es.embed_text_3 IS NOT NULL AND TRIM(es.embed_text_3) <> ''
            LEFT JOIN cz_maker mk ON mk.maker_code = mo.maker_code
            LEFT JOIN (
                SELECT model_code, image_url,
                       ROW_NUMBER() OVER (PARTITION BY model_code ORDER BY is_main DESC, sort_order ASC) AS rn
                FROM cz_model_image
            ) mi ON mi.model_code = mo.model_code AND mi.rn = 1
            LEFT JOIN (
                SELECT model_code,
                       COUNT(*) AS carCount,
                       MIN(price_min) AS priceMin,
                       MAX(price_max) AS priceMax
                FROM car_master
                WHERE adv_status = 'ONSALE'
                GROUP BY model_code
            ) agg ON agg.model_code = mo.model_code
        """ + (makerCode != null ? " WHERE mo.maker_code = ? " : "")
            + " ORDER BY COALESCE(agg.carCount,0) DESC LIMIT ?";

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

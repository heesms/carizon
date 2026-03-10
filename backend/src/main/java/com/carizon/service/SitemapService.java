package com.carizon.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SitemapService {

    private static final java.util.Map<String, String> MAKER_CODE_TO_SLUG = java.util.Map.ofEntries(
        java.util.Map.entry("101", "hyundai"),
        java.util.Map.entry("102", "kia"),
        java.util.Map.entry("103", "gm-korea"),
        java.util.Map.entry("104", "kg-mobility"),
        java.util.Map.entry("105", "renault-korea"),
        java.util.Map.entry("107", "bmw"),
        java.util.Map.entry("108", "mercedes-benz"),
        java.util.Map.entry("109", "audi"),
        java.util.Map.entry("110", "peugeot"),
        java.util.Map.entry("111", "saab"),
        java.util.Map.entry("112", "volkswagen"),
        java.util.Map.entry("113", "fiat"),
        java.util.Map.entry("114", "porsche"),
        java.util.Map.entry("115", "jaguar"),
        java.util.Map.entry("116", "land-rover"),
        java.util.Map.entry("117", "volvo"),
        java.util.Map.entry("118", "citroen"),
        java.util.Map.entry("119", "rolls-royce"),
        java.util.Map.entry("121", "chrysler"),
        java.util.Map.entry("122", "ford"),
        java.util.Map.entry("123", "honda"),
        java.util.Map.entry("124", "toyota"),
        java.util.Map.entry("125", "mitsubishi"),
        java.util.Map.entry("126", "mazda"),
        java.util.Map.entry("128", "nissan"),
        java.util.Map.entry("132", "lamborghini"),
        java.util.Map.entry("133", "lexus"),
        java.util.Map.entry("136", "lincoln"),
        java.util.Map.entry("137", "maserati"),
        java.util.Map.entry("138", "bentley"),
        java.util.Map.entry("140", "subaru"),
        java.util.Map.entry("142", "chevrolet"),
        java.util.Map.entry("143", "alfa-romeo"),
        java.util.Map.entry("146", "cadillac"),
        java.util.Map.entry("148", "ferrari"),
        java.util.Map.entry("151", "renault"),
        java.util.Map.entry("153", "infiniti"),
        java.util.Map.entry("156", "aston-martin"),
        java.util.Map.entry("158", "polestar"),
        java.util.Map.entry("160", "mini"),
        java.util.Map.entry("163", "bugatti"),
        java.util.Map.entry("167", "acura"),
        java.util.Map.entry("170", "jeep"),
        java.util.Map.entry("173", "mclaren"),
        java.util.Map.entry("176", "byd"),
        java.util.Map.entry("189", "genesis"),
        java.util.Map.entry("190", "tesla")
    );

    private static final java.util.Map<String, String> BODY_TYPE_TO_SLUG = java.util.Map.ofEntries(
        java.util.Map.entry("경차", "micro"),
        java.util.Map.entry("소형", "small"),
        java.util.Map.entry("준중형", "compact"),
        java.util.Map.entry("중형", "midsize"),
        java.util.Map.entry("대형", "fullsize"),
        java.util.Map.entry("RV", "rv"),
        java.util.Map.entry("SUV", "suv"),
        java.util.Map.entry("스포츠카", "sports"),
        java.util.Map.entry("화물", "cargo")
    );

    private final JdbcTemplate jdbc;

    @Value("${seo.site-url:https://www.carizon.shop}")
    private String siteUrl;

    @Value("${seo.sitemap.detail-page-size:5000}")
    private int detailPageSize;

    @Value("${seo.sitemap.maker-limit:60}")
    private int makerLimit;

    @Value("${seo.sitemap.bodytype-limit:20}")
    private int bodyTypeLimit;

    @Value("${seo.sitemap.model-limit:120}")
    private int modelLimit;

    public String buildSitemapIndexXml() {
        List<SitemapEntry> entries = new ArrayList<>();
        LocalDate today = LocalDate.now();
        entries.add(new SitemapEntry("/sitemaps/static.xml", today.toString()));
        entries.add(new SitemapEntry("/sitemaps/list.xml", today.toString()));

        int pages = getDetailSitemapPageCount();
        for (int i = 1; i <= pages; i++) {
            entries.add(new SitemapEntry("/sitemaps/cars-" + i + ".xml", today.toString()));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (SitemapEntry entry : entries) {
            sb.append("  <sitemap>\n");
            sb.append("    <loc>").append(xmlEscape(absoluteUrl(entry.path()))).append("</loc>\n");
            sb.append("    <lastmod>").append(xmlEscape(entry.lastmod())).append("</lastmod>\n");
            sb.append("  </sitemap>\n");
        }
        sb.append("</sitemapindex>");
        return sb.toString();
    }

    public String buildStaticSitemapXml() {
        LocalDate today = LocalDate.now();
        List<SitemapEntry> entries = List.of(
            new SitemapEntry("/", today.toString()),
            new SitemapEntry("/search", today.toString()),
            new SitemapEntry("/recommendation", today.toString()),
            new SitemapEntry("/ai-ranking", today.toString())
        );
        return buildUrlSet(entries);
    }

    public String buildListSitemapXml() {
        List<SitemapEntry> entries = new ArrayList<>();
        Set<String> dedup = new LinkedHashSet<>();
        LocalDate today = LocalDate.now();

        // 브랜드 전용관 페이지: /cars/maker/{slug}
        List<String> makers = jdbc.query("""
            SELECT cm.maker_code
            FROM car_master cm
            LEFT JOIN cz_maker m ON m.maker_code = cm.maker_code
            WHERE cm.adv_status = 'ONSALE'
              AND cm.maker_code IS NOT NULL
              AND TRIM(cm.maker_code) <> ''
              AND cm.maker_code <> '106'
              AND IFNULL(TRIM(m.maker_name), '') <> '기타'
            GROUP BY cm.maker_code
            ORDER BY COUNT(*) DESC
            LIMIT ?
        """, (rs, i) -> rs.getString(1), clamp(makerLimit, 0, 200));
        for (String makerCode : makers) {
            if (makerCode == null || makerCode.isBlank()) continue;
            String slug = MAKER_CODE_TO_SLUG.get(makerCode.trim());
            if (slug == null) continue; // skip if no slug mapping
            String path = "/cars/maker/" + slug;
            if (dedup.add(path)) {
                entries.add(new SitemapEntry(path, today.toString()));
            }
        }

        // 차종 전용관 페이지: /cars/type/{slug}
        List<String> bodyTypes = jdbc.query("""
            SELECT cm.body_type
            FROM car_master cm
            WHERE cm.adv_status = 'ONSALE'
              AND cm.body_type IS NOT NULL
              AND TRIM(cm.body_type) <> ''
              AND TRIM(cm.body_type) <> '기타'
            GROUP BY cm.body_type
            ORDER BY COUNT(*) DESC
            LIMIT ?
        """, (rs, i) -> rs.getString(1), clamp(bodyTypeLimit, 0, 200));
        for (String bodyType : bodyTypes) {
            if (bodyType == null || bodyType.isBlank()) continue;
            String slug = BODY_TYPE_TO_SLUG.get(bodyType.trim());
            if (slug == null) continue; // skip if no slug mapping
            String path = "/cars/type/" + slug;
            if (dedup.add(path)) {
                entries.add(new SitemapEntry(path, today.toString()));
            }
        }

        // 모델 전용관 페이지: /cars/maker/{makerSlug}/{modelCode}
        // 매물 있는 모델만, maker slug 매핑 가능한 것만
        List<String[]> models = jdbc.query("""
            SELECT cm.model_code, cm.maker_code
            FROM car_master cm
            INNER JOIN cz_model_embedding_source mes ON mes.model_code = cm.model_code
            WHERE cm.adv_status = 'ONSALE'
              AND cm.model_code IS NOT NULL
              AND TRIM(cm.model_code) <> ''
              AND cm.maker_code IS NOT NULL
              AND cm.maker_code <> '106'
              AND mes.embed_text_3 IS NOT NULL
              AND TRIM(mes.embed_text_3) <> ''
            GROUP BY cm.model_code, cm.maker_code
            ORDER BY COUNT(*) DESC
            LIMIT ?
        """, (rs, i) -> new String[]{rs.getString(1), rs.getString(2)}, clamp(modelLimit, 0, 2000));

        for (String[] row : models) {
            String modelCode = row[0];
            String makerCodeVal = row[1];
            if (modelCode == null || makerCodeVal == null) continue;
            String makerSlug = MAKER_CODE_TO_SLUG.get(makerCodeVal.trim());
            if (makerSlug == null) continue;
            String path = "/cars/maker/" + makerSlug + "/" + modelCode;
            if (dedup.add(path)) {
                entries.add(new SitemapEntry(path, today.toString()));
            }
        }

        return buildUrlSet(entries);
    }

    public int getDetailSitemapPageCount() {
        Integer total = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM car_master cm
            WHERE cm.adv_status = 'ONSALE'
              AND cm.car_id IS NOT NULL
        """, Integer.class);
        int safeTotal = total != null ? Math.max(total, 0) : 0;
        int safeSize = clamp(detailPageSize, 100, 20000);
        if (safeTotal == 0) return 0;
        return (safeTotal + safeSize - 1) / safeSize;
    }

    public String buildCarsDetailSitemapXml(int page) {
        int pageCount = getDetailSitemapPageCount();
        if (page < 1 || page > pageCount) {
            throw new IllegalArgumentException("invalid detail sitemap page: " + page);
        }

        int safeSize = clamp(detailPageSize, 100, 20000);
        int offset = (page - 1) * safeSize;
        List<SitemapEntry> entries = jdbc.query("""
            SELECT cm.car_id, DATE_FORMAT(COALESCE(cm.updated_at, cm.created_at), '%Y-%m-%d') AS lastmod
            FROM car_master cm
            WHERE cm.adv_status = 'ONSALE'
              AND cm.car_id IS NOT NULL
            ORDER BY cm.car_id
            LIMIT ? OFFSET ?
        """, (rs, i) -> new SitemapEntry("/cars/" + rs.getLong("car_id"), rs.getString("lastmod")), safeSize, offset);

        return buildUrlSet(entries);
    }

    private String buildUrlSet(List<SitemapEntry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (SitemapEntry entry : entries) {
            sb.append("  <url>\n");
            sb.append("    <loc>").append(xmlEscape(absoluteUrl(entry.path()))).append("</loc>\n");
            if (entry.lastmod() != null && !entry.lastmod().isBlank()) {
                sb.append("    <lastmod>").append(xmlEscape(entry.lastmod())).append("</lastmod>\n");
            }
            sb.append("  </url>\n");
        }
        sb.append("</urlset>");
        return sb.toString();
    }

    private String absoluteUrl(String path) {
        String base = siteUrl == null ? "https://www.carizon.shop" : siteUrl.trim();
        if (base.isEmpty()) base = "https://www.carizon.shop";
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (path == null || path.isBlank()) return base;
        if (path.startsWith("/")) return base + path;
        return base + "/" + path;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String xmlEscape(String value) {
        if (value == null) return "";
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }

    private record SitemapEntry(String path, String lastmod) {}
}

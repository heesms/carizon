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

    private final JdbcTemplate jdbc;

    @Value("${seo.site-url:https://carizon.shop}")
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
            String path = "/search?makerCode=" + urlEncode(makerCode.trim());
            if (dedup.add(path)) {
                entries.add(new SitemapEntry(path, today.toString()));
            }
        }

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
            String path = "/search?bodyType=" + urlEncode(bodyType.trim());
            if (dedup.add(path)) {
                entries.add(new SitemapEntry(path, today.toString()));
            }
        }

        List<String> models = jdbc.query("""
            SELECT cm.model_code
            FROM car_master cm
            LEFT JOIN cz_model mo ON mo.model_code = cm.model_code
            WHERE cm.adv_status = 'ONSALE'
              AND cm.model_code IS NOT NULL
              AND TRIM(cm.model_code) <> ''
              AND IFNULL(TRIM(mo.model_name), '') <> ''
            GROUP BY cm.model_code
            ORDER BY COUNT(*) DESC
            LIMIT ?
        """, (rs, i) -> rs.getString(1), clamp(modelLimit, 0, 500));
        for (String modelCode : models) {
            if (modelCode == null || modelCode.isBlank()) continue;
            String path = "/search?modelCode=" + urlEncode(modelCode.trim());
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
        String base = siteUrl == null ? "https://carizon.shop" : siteUrl.trim();
        if (base.isEmpty()) base = "https://carizon.shop";
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

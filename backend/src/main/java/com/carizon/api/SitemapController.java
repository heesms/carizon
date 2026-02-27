package com.carizon.api;

import com.carizon.domain.mapper.CarMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 차량 상세 페이지 동적 사이트맵 제공
 * GET /api/sitemap-cars.xml
 */
@RestController
public class SitemapController {

    private static final String SITE_URL = "https://carizon.shop";
    private final CarMapper carMapper;

    public SitemapController(CarMapper carMapper) {
        this.carMapper = carMapper;
    }

    @GetMapping(value = "/api/sitemap-cars.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String carsSitemap() {
        List<Map<String, Object>> rows = carMapper.selectSitemapCarIds();

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (Map<String, Object> row : rows) {
            Object carId = row.get("carId");
            Object updatedAt = row.get("updatedAt");
            if (carId == null) continue;
            sb.append("  <url>\n");
            sb.append("    <loc>").append(SITE_URL).append("/cars/").append(carId).append("</loc>\n");
            if (updatedAt != null) {
                sb.append("    <lastmod>").append(String.valueOf(updatedAt).substring(0, 10)).append("</lastmod>\n");
            }
            sb.append("    <changefreq>weekly</changefreq>\n");
            sb.append("    <priority>0.7</priority>\n");
            sb.append("  </url>\n");
        }
        sb.append("</urlset>");
        return sb.toString();
    }
}

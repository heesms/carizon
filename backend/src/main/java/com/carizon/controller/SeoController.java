package com.carizon.controller;

import com.carizon.service.SitemapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
public class SeoController {

    private final SitemapService sitemapService;

    @GetMapping(value = {"/api/seo/sitemap.xml", "/sitemap.xml"}, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemapIndex() {
        return xml(sitemapService.buildSitemapIndexXml());
    }

    @GetMapping(value = {"/api/seo/sitemaps/static.xml", "/sitemaps/static.xml"}, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> staticSitemap() {
        return xml(sitemapService.buildStaticSitemapXml());
    }

    @GetMapping(value = {"/api/seo/sitemaps/list.xml", "/sitemaps/list.xml"}, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> listSitemap() {
        return xml(sitemapService.buildListSitemapXml());
    }

    @GetMapping(value = {"/api/seo/sitemaps/cars-{page}.xml", "/sitemaps/cars-{page}.xml"}, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> carDetailsSitemap(@PathVariable int page) {
        try {
            return xml(sitemapService.buildCarsDetailSitemapXml(page));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private ResponseEntity<String> xml(String body) {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_XML)
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
            .body(body);
    }
}

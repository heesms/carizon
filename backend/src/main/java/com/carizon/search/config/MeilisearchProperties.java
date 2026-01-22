package com.carizon.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Meilisearch 설정 프로퍼티
 */
@Data
@Component
@ConfigurationProperties(prefix = "meilisearch")
public class MeilisearchProperties {
    private String host = "http://localhost:7700";
    private String apiKey = "masterKey";
    private String indexName = "cars";
}

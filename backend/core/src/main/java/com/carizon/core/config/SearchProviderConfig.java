package com.carizon.core.config;

import com.carizon.core.service.search.SearchProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class SearchProviderConfig {

    @Bean
    @Primary
    public SearchProvider searchProvider(@Value("${carizon.search.provider:db}") String provider,
                                         @Qualifier("dbSearchProvider") SearchProvider db,
                                         @Qualifier("elasticsearchSearchProvider") SearchProvider es) {
        return "elasticsearch".equalsIgnoreCase(provider) ? es : db;
    }
}

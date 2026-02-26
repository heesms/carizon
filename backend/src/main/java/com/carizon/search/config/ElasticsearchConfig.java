package com.carizon.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    public static final String CARS_INDEX = "cars";

    @Bean
    public RestClient restClient(
            @Value("${spring.elasticsearch.uris:http://localhost:9200}") String uris) {
        String scheme = uris.startsWith("https") ? "https" : "http";
        String withoutScheme = uris.replaceFirst("^https?://", "");
        String host = withoutScheme.split(":")[0];
        int port = withoutScheme.contains(":") ? Integer.parseInt(withoutScheme.split(":")[1]) : 9200;
        return RestClient.builder(new HttpHost(host, port, scheme)).build();
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient restClient) {
        RestClientTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}

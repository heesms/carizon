package com.carizon.search.config;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Meilisearch 설정
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MeilisearchConfig {

    private final MeilisearchProperties properties;

    @Bean
    public Client meilisearchClient() {
        try {
            Config config = new Config(properties.getHost(), properties.getApiKey());
            Client client = new Client(config);
            
            // 인덱스가 없으면 생성 (연결 테스트)
            try {
                client.getIndex(properties.getIndexName());
                log.info("[Meilisearch] 인덱스 '{}' 이미 존재함", properties.getIndexName());
            } catch (Exception e) {
                // 인덱스가 없으면 생성
                try {
                    client.createIndex(properties.getIndexName(), "carId");
                    log.info("[Meilisearch] 인덱스 '{}' 생성 완료", properties.getIndexName());
                    
                    // 인덱스 설정 (검색 가능 필드, 필터 가능 필드 등)
                    configureIndex(client);
                } catch (Exception e2) {
                    log.warn("[Meilisearch] 인덱스 생성 실패 (Meilisearch 서버가 다운되었을 수 있음): {}. 서버는 계속 실행됩니다.", e2.getMessage());
                    // 인덱스 생성 실패해도 Client 객체는 반환 (실제 사용 시 예외 처리)
                }
            }
            
            log.info("[Meilisearch] 클라이언트 초기화 완료");
            return client;
        } catch (Exception e) {
            log.warn("[Meilisearch] 클라이언트 초기화 실패 - 서버는 계속 실행됩니다. Meilisearch가 사용 불가능합니다: {}", e.getMessage());
            // Client 객체는 생성하되, 실제 연결은 지연시킴
            // 실제 사용 시 MeilisearchService에서 예외 처리
            try {
                Config config = new Config(properties.getHost(), properties.getApiKey());
                Client client = new Client(config);
                log.warn("[Meilisearch] Client 객체는 생성되었으나 연결 확인은 실패했습니다. 실제 사용 시 예외가 발생할 수 있습니다.");
                return client;
            } catch (Exception e2) {
                // Client 생성 자체가 실패한 경우 - Config 생성은 실패하지 않으므로 이 경우는 거의 없음
                // 하지만 만약 발생한다면, 최후의 수단으로 빈 Config로 Client 생성
                log.error("[Meilisearch] Client 객체 생성 실패. Meilisearch 기능은 사용할 수 없습니다: {}", e2.getMessage());
                // Config 생성은 실패하지 않으므로, 여기까지 오는 경우는 거의 없음
                // 하지만 만약 발생한다면, 예외를 던지지 않고 빈 Client를 반환하려고 하지만
                // Spring Bean은 null을 반환할 수 없으므로, 여기서는 예외를 던지지 않고
                // 빈 Config로 Client를 생성 시도
                Config fallbackConfig = new Config(properties.getHost(), properties.getApiKey());
                return new Client(fallbackConfig);
            }
        }
    }

    private void configureIndex(Client client) {
        try {
            var index = client.index(properties.getIndexName());
            
            // 검색 가능 필드 설정
            String[] searchableAttributes = {
                "makerName", "modelName", "trimName", "modelCode"
            };
            index.updateSearchableAttributesSettings(searchableAttributes);
            
            // 필터 가능 필드 설정
            String[] filterableAttributes = {
                "makerCode", "modelGroupCode", "modelCode", "trimCode", "gradeCode",
                "year", "km", "priceMin", "priceMax", "fuel", "transmission", 
                "bodyType", "region"
            };
            index.updateFilterableAttributesSettings(filterableAttributes);
            
            // 정렬 가능 필드 설정
            String[] sortableAttributes = {
                "priceMin", "priceMax", "km", "year", "priceUpdatedAt"
            };
            index.updateSortableAttributesSettings(sortableAttributes);
            
            log.info("[Meilisearch] 인덱스 설정 완료");
        } catch (Exception e) {
            log.warn("[Meilisearch] 인덱스 설정 실패 (무시 가능): {}", e.getMessage());
        }
    }
}

package com.carizon.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Configuration
public class RateLimitConfig {

    @Bean
    public ConcurrentMap<String, Integer> rateLimitCounter(@Value("${carizon.rate-limit.enabled:false}") boolean enabled) {
        return enabled ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>();
    }
}

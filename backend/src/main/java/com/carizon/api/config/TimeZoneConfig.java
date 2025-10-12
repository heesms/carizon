package com.carizon.api.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class TimeZoneConfig {

    @PostConstruct
    public void init() {
        // Set application-wide timezone to Asia/Seoul
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }
}

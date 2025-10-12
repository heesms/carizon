package com.carizon.api.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan({"com.carizon.api.mapper", "com.carizon.integration.mapper"})
public class MyBatisConfig {
}

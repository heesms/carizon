package com.carizon.api.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.carizon.api.mapper")
public class MyBatisConfig {
}

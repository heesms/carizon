package com.carizon.jobs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.carizon")
@EntityScan(basePackages = "com.carizon.core.domain")
@EnableJpaRepositories(basePackages = "com.carizon.core.repository")
public class CarizonJobsApplication {
    public static void main(String[] args) {
        SpringApplication.run(CarizonJobsApplication.class, args);
    }
}

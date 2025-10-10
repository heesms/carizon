package com.carizon.jobs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.carizon")
public class CarizonJobsApplication {
    public static void main(String[] args) {
        SpringApplication.run(CarizonJobsApplication.class, args);
    }
}

package com.carizon.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.carizon")
public class CarizonApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CarizonApiApplication.class, args);
    }
}

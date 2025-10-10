package com.carizon.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI carizonOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Carizon API")
                .description("Used car aggregation and analysis platform API")
                .version("v1.0.0")
                .contact(new Contact()
                    .name("Carizon Team")
                    .email("dev@carizon.com")));
    }
}

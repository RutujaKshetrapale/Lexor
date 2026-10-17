package com.lexor.matching.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI driverMatchingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LEXOR Driver Matching Service API")
                        .description("Microservice responsible for geospatial proximity evaluation, eligibility filtering, candidate ranking, atomic driver reservation, and driver location ingestion.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("LEXOR Engineering Team")
                                .email("dev@lexor.mobility")))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuthentication"))
                .components(new Components()
                        .addSecuritySchemes("BearerAuthentication",
                                new SecurityScheme()
                                        .name("BearerAuthentication")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter your JWT Bearer token to authorize requests")));
    }
}

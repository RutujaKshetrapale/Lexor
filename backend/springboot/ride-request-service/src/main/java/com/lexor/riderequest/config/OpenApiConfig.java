package com.lexor.riderequest.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI rideRequestServiceOpenAPI() {
        final String securitySchemeName = "BearerAuthentication";

        return new OpenAPI()
                .info(new Info()
                        .title("LEXOR — Ride Request Microservice API")
                        .description("REST API for creating ride requests, estimating fares, managing rider journeys, and administrative request monitoring across the LEXOR AI-Powered Smart Mobility Platform.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("LEXOR Engineering Team")
                                .email("engineering@lexor.mobility")
                                .url("https://github.com/RutujaKshetrapale/LEXOR"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://lexor.mobility/license")))
                .servers(List.of(
                        new Server().url("http://localhost:8091").description("Local Development Environment"),
                        new Server().url("https://api.lexor.mobility/ride-requests").description("Production Gateway")
                ))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter JWT Bearer token issued by LEXOR Auth Service (`auth-service` on port 8081). Example: `Bearer eyJhbGci...`")));
    }
}

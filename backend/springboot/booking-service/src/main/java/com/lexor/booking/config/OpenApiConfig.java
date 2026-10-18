package com.lexor.booking.config;

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
    public OpenAPI bookingServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LEXOR Booking Service API (Spring Boot)")
                        .description("Microservice managing complete booking lifecycle, OTP verification, driver trip states, and administrative monitoring for the LEXOR Smart Mobility Platform.")
                        .version("v1")
                        .contact(new Contact()
                                .name("LEXOR Engineering Team")
                                .email("dev@lexor.mobility")))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuthentication"))
                .components(new Components()
                        .addSecuritySchemes("BearerAuthentication", new SecurityScheme()
                                .name("BearerAuthentication")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter a valid LEXOR JWT token to authorize requests.")));
    }
}

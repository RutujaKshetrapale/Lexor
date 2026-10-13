package com.lexor.vehicle.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI vehicleServiceOpenAPI() {
        final String securitySchemeName = "BearerAuthentication";

        return new OpenAPI()
                .info(new Info()
                        .title("LEXOR Vehicle Microservice API")
                        .description("Microservice for vehicle registration, compliance document management, administrative verification, and fleet-driver assignments.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("LEXOR Engineering Team")
                                .email("engineering@lexor.mobility"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://lexor.mobility/license")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter your valid LEXOR JWT Bearer token to authorize requests")));
    }
}

package com.sonbadrim_server.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

import java.util.List;

@Configuration
@OpenAPIDefinition(
        info = @Info(title = "손봐드림 API Docs", version = "v1")
)
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        SecurityScheme bearerAuth = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name(HttpHeaders.AUTHORIZATION);

        SecurityRequirement securityRequirement =
                new SecurityRequirement().addList("bearerAuth");

        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", bearerAuth))
                .security(List.of(securityRequirement));
    }

    @Bean
    public GroupedOpenApi coreOpenApi() {
        return GroupedOpenApi.builder()
                .group("sonbadream")
                .pathsToMatch("/**")
                .build();
    }
}
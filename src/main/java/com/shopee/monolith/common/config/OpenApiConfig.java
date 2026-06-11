package com.shopee.monolith.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Shopee Clone API",
                version = "1.0",
                description = "Documentation for Shopee Clone Modular Monolith REST APIs"
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Local Server")
        }
)
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }

    // ==================== Module groups (Task 7 OpenAPI polish) ====================

    @Bean
    public GroupedOpenApi authGroup() {
        return GroupedOpenApi.builder().group("auth-user")
                .pathsToMatch("/api/auth/**", "/api/users/**", "/api/addresses/**").build();
    }

    @Bean
    public GroupedOpenApi catalogGroup() {
        return GroupedOpenApi.builder().group("catalog-search")
                .pathsToMatch("/api/products/**", "/api/categories/**", "/api/shops/**",
                        "/api/search/**", "/api/recommendations/**", "/api/media/**")
                .build();
    }

    @Bean
    public GroupedOpenApi commerceGroup() {
        return GroupedOpenApi.builder().group("cart-order-payment")
                .pathsToMatch("/api/cart/**", "/api/orders/**", "/api/buyer/**",
                        "/api/payments/**", "/api/inventories/**", "/api/seller/**")
                .build();
    }

    @Bean
    public GroupedOpenApi engagementGroup() {
        return GroupedOpenApi.builder().group("review-notification-chat")
                .pathsToMatch("/api/reviews/**", "/api/products/*/reviews",
                        "/api/notifications/**", "/api/chat/**")
                .build();
    }
}

package com.cosmos.fraud.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {

    private static final String INGESTION_SERVICE_URI  = "http://localhost:8081";
    private static final String FEATURE_STORE_URI      = "http://localhost:8082";
    private static final String SCORING_SERVICE_URI    = "http://localhost:8083";
    private static final String THREEDS_SERVICE_URI    = "http://localhost:8084";
    private static final String AUDIT_SERVICE_URI      = "http://localhost:8085";

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder,
                                      RateLimiterConfig rateLimiterConfig) {
        return builder.routes()

                // ingestion-service: /v1/transactions/**
                .route("ingestion-service-transactions", r -> r
                        .path("/v1/transactions/**")
                        .filters(f -> f
                                .circuitBreaker(c -> c
                                        .setName("ingestion-cb")
                                        .setFallbackUri("forward:/fallback/ingestion"))
                                .requestRateLimiter(rl -> rl
                                        .setRateLimiter(rateLimiterConfig.redisRateLimiter())
                                        .setKeyResolver(rateLimiterConfig.ipKeyResolver())))
                        .uri(INGESTION_SERVICE_URI))

                // feature-store: /v1/features/**
                .route("feature-store", r -> r
                        .path("/v1/features/**")
                        .filters(f -> f
                                .circuitBreaker(c -> c
                                        .setName("feature-store-cb")
                                        .setFallbackUri("forward:/fallback/feature-store"))
                                .requestRateLimiter(rl -> rl
                                        .setRateLimiter(rateLimiterConfig.redisRateLimiter())
                                        .setKeyResolver(rateLimiterConfig.ipKeyResolver())))
                        .uri(FEATURE_STORE_URI))

                // scoring-service: /v1/scoring/**
                .route("scoring-service", r -> r
                        .path("/v1/scoring/**")
                        .filters(f -> f
                                .circuitBreaker(c -> c
                                        .setName("scoring-cb")
                                        .setFallbackUri("forward:/fallback/scoring"))
                                .requestRateLimiter(rl -> rl
                                        .setRateLimiter(rateLimiterConfig.redisRateLimiter())
                                        .setKeyResolver(rateLimiterConfig.ipKeyResolver())))
                        .uri(SCORING_SERVICE_URI))

                // threeds-service: /v1/threeds/**
                .route("threeds-service", r -> r
                        .path("/v1/threeds/**")
                        .filters(f -> f
                                .circuitBreaker(c -> c
                                        .setName("threeds-cb")
                                        .setFallbackUri("forward:/fallback/threeds"))
                                .requestRateLimiter(rl -> rl
                                        .setRateLimiter(rateLimiterConfig.redisRateLimiter())
                                        .setKeyResolver(rateLimiterConfig.ipKeyResolver())))
                        .uri(THREEDS_SERVICE_URI))

                // audit-service: /v1/audit/**
                .route("audit-service", r -> r
                        .path("/v1/audit/**")
                        .filters(f -> f
                                .circuitBreaker(c -> c
                                        .setName("audit-cb")
                                        .setFallbackUri("forward:/fallback/audit"))
                                .requestRateLimiter(rl -> rl
                                        .setRateLimiter(rateLimiterConfig.redisRateLimiter())
                                        .setKeyResolver(rateLimiterConfig.ipKeyResolver())))
                        .uri(AUDIT_SERVICE_URI))

                // devices: /v1/devices/** → ingestion-service
                .route("ingestion-service-devices", r -> r
                        .path("/v1/devices/**")
                        .filters(f -> f
                                .circuitBreaker(c -> c
                                        .setName("ingestion-cb")
                                        .setFallbackUri("forward:/fallback/ingestion"))
                                .requestRateLimiter(rl -> rl
                                        .setRateLimiter(rateLimiterConfig.redisRateLimiter())
                                        .setKeyResolver(rateLimiterConfig.ipKeyResolver())))
                        .uri(INGESTION_SERVICE_URI))

                .build();
    }
}

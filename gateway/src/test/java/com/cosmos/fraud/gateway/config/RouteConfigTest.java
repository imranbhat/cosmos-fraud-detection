package com.cosmos.fraud.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the {@link RouteConfig} produces a valid {@link RouteLocator}
 * with the expected routes.
 *
 * <p>The test uses a minimal application context. Redis and OAuth2 are stubbed
 * out via test properties so no external services are required.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                // Disable Redis auto-configuration in tests
                "spring.data.redis.host=localhost",
                "spring.data.redis.port=6379",
                // Supply a dummy issuer so JWT auto-configuration doesn't fail
                "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://test.example.com",
                // Disable Actuator endpoints that require full context
                "management.endpoints.web.exposure.include=health"
        }
)
@ActiveProfiles("test")
class RouteConfigTest {

    @Autowired(required = false)
    private RouteLocator routeLocator;

    // ──────────────────────────────────────────────────────────────────────────
    // Bean presence
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RouteLocator bean is created by RouteConfig")
    void routeLocatorBean_isPresent() {
        assertThat(routeLocator)
                .as("RouteLocator bean must be registered in the application context")
                .isNotNull();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Route counts
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RouteLocator contains exactly 6 routes")
    void routeLocator_containsSixRoutes() {
        StepVerifier.create(routeLocator.getRoutes().collectList())
                .assertNext(routes -> assertThat(routes)
                        .as("Expected 6 routes: transactions, features, scoring, threeds, audit, devices")
                        .hasSize(6))
                .verifyComplete();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Route IDs
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RouteLocator contains all expected route IDs")
    void routeLocator_containsExpectedRouteIds() {
        StepVerifier.create(routeLocator.getRoutes()
                        .map(route -> route.getId())
                        .collectList())
                .assertNext(ids -> assertThat(ids).containsExactlyInAnyOrder(
                        "ingestion-service-transactions",
                        "feature-store",
                        "scoring-service",
                        "threeds-service",
                        "audit-service",
                        "ingestion-service-devices"))
                .verifyComplete();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Route URIs
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Routes point to the correct backend URIs")
    void routes_haveCorrectBackendUris() {
        StepVerifier.create(routeLocator.getRoutes().collectList())
                .assertNext(routes -> {
                    assertThat(routes)
                            .anySatisfy(route -> {
                                assertThat(route.getId()).isEqualTo("scoring-service");
                                assertThat(route.getUri().toString()).isEqualTo("http://localhost:8083");
                            });
                    assertThat(routes)
                            .anySatisfy(route -> {
                                assertThat(route.getId()).isEqualTo("audit-service");
                                assertThat(route.getUri().toString()).isEqualTo("http://localhost:8085");
                            });
                    assertThat(routes)
                            .anySatisfy(route -> {
                                assertThat(route.getId()).isEqualTo("ingestion-service-devices");
                                assertThat(route.getUri().toString()).isEqualTo("http://localhost:8081");
                            });
                })
                .verifyComplete();
    }
}

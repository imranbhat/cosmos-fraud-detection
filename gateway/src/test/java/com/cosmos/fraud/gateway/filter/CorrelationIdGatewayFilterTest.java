package com.cosmos.fraud.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static com.cosmos.fraud.gateway.filter.CorrelationIdGatewayFilter.CORRELATION_ID_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;

@ExtendWith(MockitoExtension.class)
class CorrelationIdGatewayFilterTest {

    private CorrelationIdGatewayFilter filter;

    @Mock
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdGatewayFilter();
        // Chain always succeeds and returns the exchange it received
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Order
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Filter order is HIGHEST_PRECEDENCE")
    void filterOrder_isHighestPrecedence() {
        assertThat(filter.getOrder()).isEqualTo(HIGHEST_PRECEDENCE);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Correlation ID generation
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Generates UUID correlation ID when header is absent")
    void filter_generatesCorrelationId_whenHeaderMissing() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/v1/transactions/123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ArgumentCaptor<ServerWebExchange> exchangeCaptor =
                ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(exchangeCaptor.capture())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ServerWebExchange capturedExchange = exchangeCaptor.getValue();
        String correlationId = capturedExchange.getRequest()
                .getHeaders()
                .getFirst(CORRELATION_ID_HEADER);

        assertThat(correlationId)
                .as("A UUID correlation ID should be generated")
                .isNotBlank()
                // Rough UUID shape check
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    @DisplayName("Propagates existing correlation ID unchanged")
    void filter_passesThrough_existingCorrelationId() {
        String existingId = "my-fixed-correlation-id";

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/v1/scoring/evaluate")
                .header(CORRELATION_ID_HEADER, existingId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ArgumentCaptor<ServerWebExchange> exchangeCaptor =
                ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(exchangeCaptor.capture())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ServerWebExchange capturedExchange = exchangeCaptor.getValue();
        String correlationId = capturedExchange.getRequest()
                .getHeaders()
                .getFirst(CORRELATION_ID_HEADER);

        assertThat(correlationId)
                .as("Existing correlation ID must be preserved")
                .isEqualTo(existingId);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Response header propagation
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Adds correlation ID to response headers")
    void filter_addsCorrelationIdToResponseHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/v1/features/123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        HttpHeaders responseHeaders = exchange.getResponse().getHeaders();
        assertThat(responseHeaders.getFirst(CORRELATION_ID_HEADER))
                .as("Response should contain X-Correlation-ID")
                .isNotBlank();
    }

    @Test
    @DisplayName("Response correlation ID matches generated request correlation ID")
    void filter_responseCorrelationId_matchesRequest() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/v1/audit/events")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ArgumentCaptor<ServerWebExchange> exchangeCaptor =
                ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(exchangeCaptor.capture())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        String requestCorrelationId = exchangeCaptor.getValue()
                .getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        String responseCorrelationId = exchange.getResponse()
                .getHeaders().getFirst(CORRELATION_ID_HEADER);

        assertThat(responseCorrelationId)
                .as("Request and response correlation IDs must match")
                .isEqualTo(requestCorrelationId);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Blank header handling
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Generates new ID when header is present but blank")
    void filter_generatesCorrelationId_whenHeaderIsBlank() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/v1/devices/abc")
                .header(CORRELATION_ID_HEADER, "   ")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ArgumentCaptor<ServerWebExchange> exchangeCaptor =
                ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(exchangeCaptor.capture())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        String correlationId = exchangeCaptor.getValue()
                .getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);

        assertThat(correlationId)
                .as("A new UUID should be generated for blank header values")
                .isNotBlank()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
}

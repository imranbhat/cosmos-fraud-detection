package com.cosmos.fraud.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global filter that ensures every request carries a correlation ID.
 *
 * <p>If the incoming {@code X-Correlation-ID} header is absent, a new UUID is
 * generated and attached to both the mutated request and the downstream
 * response. The correlation ID is also stored in the SLF4J MDC so that all
 * log statements emitted during the request lifecycle include it.</p>
 */
@Component
public class CorrelationIdGatewayFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdGatewayFilter.class);

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String MDC_CORRELATION_KEY   = "correlationId";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
            log.debug("Generated new correlation ID: {}", correlationId);
        } else {
            log.debug("Using existing correlation ID: {}", correlationId);
        }

        final String finalCorrelationId = correlationId;

        // Propagate correlation ID downstream in the request
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(CORRELATION_ID_HEADER, finalCorrelationId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        // Add correlation ID to the response headers before continuing
        ServerHttpResponse response = mutatedExchange.getResponse();
        response.getHeaders().add(CORRELATION_ID_HEADER, finalCorrelationId);

        // Place the correlation ID in MDC for the duration of the reactive chain.
        // contextWrite ensures the MDC value is available to downstream operators.
        return chain.filter(mutatedExchange)
                .contextWrite(ctx -> {
                    MDC.put(MDC_CORRELATION_KEY, finalCorrelationId);
                    return ctx;
                })
                .doFinally(signal -> MDC.remove(MDC_CORRELATION_KEY));
    }
}

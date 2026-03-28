package com.cosmos.fraud.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Global filter that logs the HTTP method, path, correlation ID, response status
 * code, and request latency for every request handled by the gateway.
 */
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public int getOrder() {
        // Run immediately after the correlation-ID filter so the ID is already set
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String correlationId = request.getHeaders()
                .getFirst(CorrelationIdGatewayFilter.CORRELATION_ID_HEADER);
        String method = request.getMethod().name();
        String path   = request.getURI().getPath();

        long startTime = Instant.now().toEpochMilli();

        log.info("Inbound request  correlationId={} method={} path={}",
                correlationId, method, path);

        return chain.filter(exchange)
                .doFinally(signalType -> {
                    long latencyMs = Instant.now().toEpochMilli() - startTime;
                    int statusCode = exchange.getResponse().getStatusCode() != null
                            ? exchange.getResponse().getStatusCode().value()
                            : 0;

                    log.info("Outbound response correlationId={} method={} path={} status={} latencyMs={}",
                            correlationId, method, path, statusCode, latencyMs);
                });
    }
}

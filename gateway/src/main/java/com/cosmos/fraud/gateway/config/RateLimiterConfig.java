package com.cosmos.fraud.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Objects;

@Configuration
public class RateLimiterConfig {

    /**
     * Redis-backed rate limiter.
     * replenishRate = 100 tokens/second (steady-state requests allowed per second)
     * burstCapacity  = 200 (max tokens that can accumulate for burst traffic)
     */
    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(100, 200);
    }

    /**
     * Resolves rate-limiting key from the client's remote IP address.
     * Falls back to "unknown" when the address cannot be determined.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.justOrEmpty(exchange.getRequest().getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(Objects::toString)
                .defaultIfEmpty("unknown");
    }
}

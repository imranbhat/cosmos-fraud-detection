package com.cosmos.fraud.featurestore.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * Redis infrastructure configuration.
 *
 * <p>Registers:
 * <ul>
 *   <li>{@link LettuceConnectionFactory} – non-blocking Lettuce driver</li>
 *   <li>{@link StringRedisTemplate} – string-typed template for all Redis operations</li>
 *   <li>{@link RedisScript} – pre-loaded Lua script for atomic feature updates</li>
 * </ul>
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);
        return new LettuceConnectionFactory(config);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * Loads {@code update-features.lua} from the classpath and wraps it in a
     * {@link DefaultRedisScript}.  The script returns a JSON string.
     */
    @Bean
    public RedisScript<String> updateFeaturesScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("scripts/update-features.lua")));
        script.setResultType(String.class);
        return script;
    }
}

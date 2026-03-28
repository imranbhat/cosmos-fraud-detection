package com.cosmos.fraud.audit.config;

import com.datastax.oss.driver.api.core.CqlSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;

/**
 * Configures the DataStax/ScyllaDB CQL session from application.yml properties.
 */
@Configuration
public class ScyllaConfig {

    @Value("${scylla.contact-points:localhost}")
    private String contactPoints;

    @Value("${scylla.port:9042}")
    private int port;

    @Value("${scylla.datacenter:datacenter1}")
    private String datacenter;

    @Value("${scylla.keyspace:fraud_audit}")
    private String keyspace;

    @Bean
    public CqlSession cqlSession() {
        return CqlSession.builder()
                .addContactPoint(new InetSocketAddress(contactPoints, port))
                .withLocalDatacenter(datacenter)
                .withKeyspace(keyspace)
                .build();
    }
}

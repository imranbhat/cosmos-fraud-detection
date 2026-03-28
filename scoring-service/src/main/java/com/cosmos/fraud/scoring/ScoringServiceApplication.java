package com.cosmos.fraud.scoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.cosmos.fraud.scoring.config.ScoringProperties;

@SpringBootApplication
@EnableConfigurationProperties(ScoringProperties.class)
public class ScoringServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScoringServiceApplication.class, args);
    }
}

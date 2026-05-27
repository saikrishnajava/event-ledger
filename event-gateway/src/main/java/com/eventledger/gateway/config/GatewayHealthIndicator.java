package com.eventledger.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GatewayHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(GatewayHealthIndicator.class);
    private final RestClient restClient;

    public GatewayHealthIndicator(RestClient accountServiceRestClient) {
        this.restClient = accountServiceRestClient;
    }

    @Override
    public Health health() {
        try {
            restClient.get()
                    .uri("/health")
                    .retrieve()
                    .toBodilessEntity();
            return Health.up()
                    .withDetail("accountService", "UP")
                    .build();
        } catch (Exception e) {
            log.warn("Account Service health check failed", e);
            return Health.down()
                    .withDetail("accountService", "DOWN")
                    .withDetail("error", "Account Service unreachable")
                    .build();
        }
    }
}

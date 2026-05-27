package com.eventledger.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GatewayHealthIndicator implements HealthIndicator {

    private final RestClient restClient;

    public GatewayHealthIndicator(RestClient accountServiceRestClient) {
        this.restClient = accountServiceRestClient;
    }

    @Override
    public Health health() {
        try {
            String response = restClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(String.class);
            return Health.up()
                    .withDetail("accountService", "UP")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("accountService", "DOWN")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}

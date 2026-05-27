package com.eventledger.gateway.integration;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that Resilience4j CircuitBreaker is correctly configured
 * and wired into the Spring context. State transition behavior
 * (OPEN after failures) is verified end-to-end via the
 * GatewayIntegrationTest.shouldReturn503WhenAccountServiceThrows test.
 */
@SpringBootTest
class CircuitBreakerIntegrationTest {

    @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

    @Test
    void shouldHaveCircuitBreakerRegisteredAndConfigured() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("accountService");
        assertThat(cb).isNotNull();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void shouldHaveCorrectConfiguration() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("accountService");
        assertThat(cb.getCircuitBreakerConfig().getSlidingWindowSize()).isEqualTo(10);
        assertThat(cb.getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(50);
        assertThat(cb.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
    }
}

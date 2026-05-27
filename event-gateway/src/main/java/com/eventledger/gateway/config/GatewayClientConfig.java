package com.eventledger.gateway.config;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Configuration
public class GatewayClientConfig {

    @Value("${account-service.url}")
    private String accountServiceUrl;

    @Bean
    public RestClient accountServiceRestClient() {
        return RestClient.builder()
                .baseUrl(accountServiceUrl)
                .requestInterceptor(tracePropagationInterceptor())
                .build();
    }

    private ClientHttpRequestInterceptor tracePropagationInterceptor() {
        return (request, body, execution) -> {
            String traceId = MDC.get("traceId");
            String spanId = MDC.get("spanId");

            if (traceId == null) {
                traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
                spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            }

            // Propagate traceId and current spanId (becomes parent for Account Service)
            String traceparent = String.format("00-%s-%s-01", traceId, spanId);
            request.getHeaders().add("traceparent", traceparent);
            return execution.execute(request, body);
        };
    }
}

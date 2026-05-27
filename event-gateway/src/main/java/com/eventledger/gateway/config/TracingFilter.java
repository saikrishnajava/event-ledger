package com.eventledger.gateway.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(1)
public class TracingFilter implements Filter {

    private static final String TRACEPARENT_HEADER = "traceparent";
    private static final String TRACE_ID_KEY = "traceId";
    private static final String SPAN_ID_KEY = "spanId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String traceparent = httpRequest.getHeader(TRACEPARENT_HEADER);

        String traceId;
        String spanId;

        if (traceparent != null && traceparent.matches("^00-[0-9a-f]{32}-[0-9a-f]{16}-01$")) {
            String[] parts = traceparent.split("-");
            traceId = parts[1];
            spanId = generateId(16);
        } else {
            traceId = generateId(32);
            spanId = generateId(16);
        }

        MDC.put(TRACE_ID_KEY, traceId);
        MDC.put(SPAN_ID_KEY, spanId);

        httpResponse.setHeader(TRACEPARENT_HEADER, String.format("00-%s-%s-01", traceId, spanId));

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_KEY);
            MDC.remove(SPAN_ID_KEY);
        }
    }

    private String generateId(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length);
    }
}

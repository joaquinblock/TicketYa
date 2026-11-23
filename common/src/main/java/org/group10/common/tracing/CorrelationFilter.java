package org.group10.common.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.group10.common.security.GatewayHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class CorrelationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CorrelationFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = CorrelationHeaders.normalize(request.getHeader(CorrelationHeaders.HEADER));
        String user = normalizeUser(request.getHeader(GatewayHeaders.USER_ID));
        long startedAt = System.nanoTime();
        response.setHeader(CorrelationHeaders.HEADER, correlationId);
        MDC.put(CorrelationHeaders.MDC_KEY, correlationId);
        MDC.put("userId", user);
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("http_request method={} endpoint={} user={} status={} durationMs={} correlationId={}",
                    request.getMethod(), request.getRequestURI(), user, response.getStatus(), durationMs,
                    correlationId);
            MDC.remove("userId");
            MDC.remove(CorrelationHeaders.MDC_KEY);
        }
    }

    private static String normalizeUser(String userId) {
        return userId == null || userId.isBlank() ? "anonymous" : userId.trim();
    }
}

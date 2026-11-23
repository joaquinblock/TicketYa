package org.group10.common.logging;

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

/**
 * Abre el contexto de correlación del request, lo devuelve en la respuesta y, al terminar,
 * registra el request con su endpoint, usuario, status y duración.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
    private static final String ANONYMOUS = "anonymous";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = CorrelationIds.validOrNew(request.getHeader(CorrelationIds.HEADER_NAME));
        long startedAt = System.nanoTime();
        MDC.put(CorrelationIds.MDC_KEY, correlationId);
        response.setHeader(CorrelationIds.HEADER_NAME, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            logRequest(request, response, (System.nanoTime() - startedAt) / 1_000_000);
            MDC.remove(CorrelationIds.MDC_KEY);
        }
    }

    private static void logRequest(HttpServletRequest request, HttpServletResponse response, long durationMs) {
        log.atInfo()
                .addKeyValue("method", request.getMethod())
                .addKeyValue("endpoint", request.getRequestURI())
                .addKeyValue("userId", userOf(request))
                .addKeyValue("status", response.getStatus())
                .addKeyValue("durationMs", durationMs)
                .log("{} {} -> {} en {} ms", request.getMethod(), request.getRequestURI(), response.getStatus(),
                        durationMs);
    }

    private static String userOf(HttpServletRequest request) {
        String userId = request.getHeader(GatewayHeaders.USER_ID);
        return userId == null || userId.isBlank() ? ANONYMOUS : userId.trim();
    }
}

package org.group10.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.group10.common.security.GatewayHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Function;

/**
 * Abre el contexto de correlación del request, lo devuelve en la respuesta y, al terminar,
 * registra el request con su endpoint, usuario, status y duración.
 * <p>
 * Lo que sigue en la cadena ve el id en el header aunque el cliente no lo haya mandado: en el api-gateway, así
 * llega al servicio el mismo id que se registró y se devolvió.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
    private static final String ANONYMOUS = "anonymous";

    private final Function<HttpServletRequest, String> userOf;

    /**
     * Para los servicios: el usuario es el {@code X-User-Id} que puso el api-gateway.
     */
    public CorrelationIdFilter() {
        this(request -> request.getHeader(GatewayHeaders.USER_ID));
    }

    /**
     * @param userOf el usuario del request, o {@code null} si es anónimo. Se lee al terminar el request
     */
    public CorrelationIdFilter(Function<HttpServletRequest, String> userOf) {
        this.userOf = userOf;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = CorrelationIds.validOrNew(request.getHeader(CorrelationIds.HEADER_NAME));
        long startedAt = System.nanoTime();
        MDC.put(CorrelationIds.MDC_KEY, correlationId);
        response.setHeader(CorrelationIds.HEADER_NAME, correlationId);
        try {
            filterChain.doFilter(new CorrelatedRequest(request, correlationId), response);
        } finally {
            logRequest(request, response, (System.nanoTime() - startedAt) / 1_000_000);
            MDC.remove(CorrelationIds.MDC_KEY);
        }
    }

    private void logRequest(HttpServletRequest request, HttpServletResponse response, long durationMs) {
        log.atInfo()
                .addKeyValue("method", request.getMethod())
                .addKeyValue("endpoint", request.getRequestURI())
                .addKeyValue("userId", userOrAnonymous(request))
                .addKeyValue("status", response.getStatus())
                .addKeyValue("durationMs", durationMs)
                .log("{} {} -> {} en {} ms", request.getMethod(), request.getRequestURI(), response.getStatus(),
                        durationMs);
    }

    private String userOrAnonymous(HttpServletRequest request) {
        String userId = userOf.apply(request);
        return userId == null || userId.isBlank() ? ANONYMOUS : userId.trim();
    }

    /**
     * El request con el id de correlación en su header, en lugar del que mandó el cliente si no era válido.
     */
    private static final class CorrelatedRequest extends HttpServletRequestWrapper {

        private final String correlationId;

        CorrelatedRequest(HttpServletRequest request, String correlationId) {
            super(request);
            this.correlationId = correlationId;
        }

        @Override
        public String getHeader(String name) {
            return isCorrelationId(name) ? correlationId : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return isCorrelationId(name) ? Collections.enumeration(List.of(correlationId)) : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>(Collections.list(super.getHeaderNames()));
            names.removeIf(CorrelatedRequest::isCorrelationId);
            names.add(CorrelationIds.HEADER_NAME);
            return Collections.enumeration(names);
        }

        private static boolean isCorrelationId(String name) {
            return CorrelationIds.HEADER_NAME.equalsIgnoreCase(name);
        }
    }
}

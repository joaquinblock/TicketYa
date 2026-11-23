package org.group10.gateway.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.group10.common.logging.CorrelationIds;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * El {@code CorrelationIdFilter} ya puso el id en la respuesta, y el servicio al que se reenvió el request lo devuelve
 * también: sin este filtro el cliente lo recibiría dos veces.
 */
class SingleCorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, new HttpServletResponseWrapper(response) {
            @Override
            public void setHeader(String name, String value) {
                if (!isCorrelationId(name)) {
                    super.setHeader(name, value);
                }
            }

            @Override
            public void addHeader(String name, String value) {
                if (!isCorrelationId(name)) {
                    super.addHeader(name, value);
                }
            }
        });
    }

    private static boolean isCorrelationId(String name) {
        return CorrelationIds.HEADER_NAME.equalsIgnoreCase(name);
    }
}

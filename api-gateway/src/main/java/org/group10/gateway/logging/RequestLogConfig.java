package org.group10.gateway.logging;

import org.group10.common.logging.CorrelationIdFilter;
import org.group10.gateway.security.IdentityHeadersFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * El id de correlación nace acá, en la entrada del sistema: se devuelve, se reenvía al servicio y cada request queda
 * registrado, también los que el gateway rechaza (401, 503 o una ruta que no existe). La respuesta lleva el id una
 * sola vez, aunque el servicio también lo devuelva.
 * <p>
 * La autoconfiguración de {@code common} no aplica al gateway porque lee el usuario del {@code X-User-Id}, que acá
 * todavía es el del cliente: el usuario sale del token que validó {@link IdentityHeadersFilter}.
 */
@Configuration
public class RequestLogConfig {

    @Bean
    FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(
                new CorrelationIdFilter(request -> (String) request.getAttribute(IdentityHeadersFilter.USER_ID_ATTRIBUTE)));
        // Después del filtro de observación (HIGHEST_PRECEDENCE + 1), para que el log lleve el traceId de Zipkin
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        return registration;
    }

    @Bean
    FilterRegistrationBean<SingleCorrelationIdFilter> singleCorrelationIdFilter() {
        FilterRegistrationBean<SingleCorrelationIdFilter> registration =
                new FilterRegistrationBean<>(new SingleCorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
        return registration;
    }
}

package org.group10.common.config;

import org.group10.common.logging.CorrelationIdFilter;
import org.group10.common.logging.CorrelationIdRequestInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;

/**
 * Correlación de requests en todos los servicios servlet salvo el gateway, que la registra con su propio usuario
 * ({@code RequestLogConfig}, ADR 0010).
 * El filtro va primero para que los 401/403 de seguridad también queden registrados con su id.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "ticketya.logging.correlation.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnMissingClass("org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions")
public class CorrelationIdAutoConfiguration {

    @Bean
    FilterRegistrationBean<CorrelationIdFilter> ticketyaCorrelationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        // Justo después del filtro de observación (HIGHEST_PRECEDENCE + 1), para que el log del request lleve el
        // traceId de Zipkin; y antes que la seguridad, para que los 401/403 también queden registrados
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        return registration;
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RestClientHttpServiceGroupConfigurer.class)
    static class PropagateCorrelationId {

        @Bean
        RestClientHttpServiceGroupConfigurer ticketyaCorrelationIdConfigurer() {
            CorrelationIdRequestInterceptor interceptor = new CorrelationIdRequestInterceptor();
            return groups -> groups.forEachClient((group, builder) -> builder.requestInterceptor(interceptor));
        }
    }
}

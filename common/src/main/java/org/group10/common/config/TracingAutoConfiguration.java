package org.group10.common.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Trazas de Zipkin en todos los servicios, el gateway incluido (ADR 0010). Los healthchecks de Docker piden
 * {@code /actuator/health} cada pocos segundos: si se trazaran, taparían en Zipkin las trazas de las operaciones.
 */
@AutoConfiguration
@ConditionalOnClass({ObservationPredicate.class, ServerRequestObservationContext.class})
public class TracingAutoConfiguration {

    @Bean
    ObservationPredicate ticketyaIgnoreActuatorObservations() {
        return (name, context) -> !(context instanceof ServerRequestObservationContext request
                && request.getCarrier().getRequestURI().startsWith("/actuator"));
    }
}

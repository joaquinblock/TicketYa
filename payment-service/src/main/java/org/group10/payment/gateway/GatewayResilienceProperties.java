package org.group10.payment.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Timeout, reintentos y circuit breaker de las llamadas a la pasarela.
 * El peor caso es {@code maxAttempts × timeout + (maxAttempts - 1) × waitBetweenAttempts},
 * y tiene que quedar por debajo de lo que el ticket-service espera al payment-service.
 *
 * @param timeout                 cuánto se espera cada llamada antes de darla por perdida
 * @param maxAttempts             intentos en total, contando el primero
 * @param waitBetweenAttempts     pausa entre un intento y el siguiente
 * @param slidingWindowSize       últimas llamadas que mira el circuit breaker
 * @param minimumCalls            llamadas necesarias antes de calcular la tasa de fallas
 * @param failureRateThreshold    porcentaje de fallas que abre el circuito
 * @param waitInOpenState         cuánto queda abierto antes de dejar pasar llamadas de prueba
 * @param callsInHalfOpenState    llamadas de prueba que deciden si el circuito se cierra
 */
@ConfigurationProperties("payment.gateway.resilience")
public record GatewayResilienceProperties(
        Duration timeout,
        int maxAttempts,
        Duration waitBetweenAttempts,
        int slidingWindowSize,
        int minimumCalls,
        float failureRateThreshold,
        Duration waitInOpenState,
        int callsInHalfOpenState) {
}

package org.group10.payment.gateway;

/**
 * Cómo responde la pasarela simulada: cuánto demora y si está caída.
 */
public record MockGatewayBehavior(long delayMs, boolean down) {
}

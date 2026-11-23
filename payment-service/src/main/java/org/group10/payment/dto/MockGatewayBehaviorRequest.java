package org.group10.payment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.group10.payment.gateway.MockGatewayBehavior;

/**
 * Nuevo comportamiento de la pasarela simulada. La demora tiene tope para no dejar hilos esperando de más.
 */
public record MockGatewayBehaviorRequest(
        @NotNull @PositiveOrZero @Max(MockGatewayBehaviorRequest.MAX_DELAY_MS) Long delayMs,
        @NotNull Boolean down) {

    public static final long MAX_DELAY_MS = 60_000;

    public MockGatewayBehavior toBehavior() {
        return new MockGatewayBehavior(delayMs, down);
    }
}

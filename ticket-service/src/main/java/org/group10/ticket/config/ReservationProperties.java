package org.group10.ticket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param paymentCallAttempts llamadas al payment-service por cada pago cuando no hay respuesta, todas con la misma
 *                            clave. Sumando sus timeouts tienen que entrar en {@code paymentMargin}.
 */
@ConfigurationProperties("ticketya.reservation")
public record ReservationProperties(Duration hold, Duration paymentMargin, int maxRejectedPayments,
                                    int paymentCallAttempts) {
}

package org.group10.ticket.messaging;

import java.time.Instant;

/**
 * Evento interno de Spring: una orden quedó {@code PAID}. Se dispara dentro de la transacción del pago y
 * {@link PurchaseConfirmedPublisher} lo avisa por RabbitMQ recién cuando esa transacción se confirma.
 */
public record PurchaseConfirmed(Long orderId, Long userId, Long eventId, Instant occurredAt) {
}

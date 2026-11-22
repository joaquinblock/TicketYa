package org.group10.ticket.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Contrato de {@code purchase.confirmed} (ADR 0004). Lleva solo ids: los datos los pide quien consume.
 * El {@code messageId} es el mismo en cada reentrega, así el consumidor descarta los repetidos.
 */
public record PurchaseConfirmedMessage(UUID messageId, Instant occurredAt, Long orderId, Long userId, Long eventId) {
}

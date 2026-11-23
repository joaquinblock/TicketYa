package org.group10.event.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Contrato de {@code event.cancelled}, {@code event.suspended} y {@code event.rescheduled} (ADR 0004). Lleva solo
 * ids: los datos del evento los pide quien consume. El {@code messageId} es el mismo en cada reentrega, así el
 * consumidor descarta los repetidos.
 */
public record EventChangedMessage(UUID messageId, Instant occurredAt, Long eventId, EventChange change) {
}

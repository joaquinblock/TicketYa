package org.group10.event.messaging;

import java.time.Instant;

/**
 * Evento interno de Spring: un evento se canceló, se suspendió, se reprogramó o se publicó. Se dispara dentro de la transacción
 * que cambia el evento y {@link EventChangedPublisher} lo avisa por RabbitMQ recién cuando esa transacción se confirma.
 */
public record EventChanged(Long eventId, EventChange change, Instant occurredAt) {
}

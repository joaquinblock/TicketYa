package org.group10.ticket.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Contrato de {@code return.resolved}: el administrador aprobó o rechazó una solicitud de devolución. Lleva solo
 * ids y la resolución ({@code APPROVED} o {@code REJECTED}); el nombre del evento lo pide quien consume.
 */
public record ReturnResolvedMessage(UUID messageId, Instant occurredAt, Long requestId, Long ticketId, Long userId,
                                    Long eventId, String resolution) {
}

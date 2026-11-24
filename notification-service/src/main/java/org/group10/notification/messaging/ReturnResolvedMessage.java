package org.group10.notification.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code return.resolved} que publica ticket-service cuando el administrador aprueba o rechaza una solicitud de
 * devolución. Copia propia del contrato, como {@link PurchaseConfirmedMessage}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReturnResolvedMessage(UUID messageId, Instant occurredAt, Long requestId, Long ticketId, Long userId,
                                    Long eventId, String resolution) {

    public boolean isApproved() {
        return "APPROVED".equals(resolution);
    }
}

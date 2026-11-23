package org.group10.notification.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code event.cancelled}, {@code event.suspended} o {@code event.rescheduled}, que publica event-service (ADR 0004).
 * Es una copia propia del contrato: notification-service no depende de las clases de event-service. Un
 * {@code change} desconocido no se puede convertir y va a la DLQ sin reintentos.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventChangedMessage(UUID messageId, Instant occurredAt, Long eventId, Change change) {

    public enum Change {
        CANCELLED,
        SUSPENDED,
        RESCHEDULED,
        /** Llega por {@code event.*} pero no es un cambio: lo avisa {@code EventPublishedListener} (US-NOT-03). */
        PUBLISHED
    }
}

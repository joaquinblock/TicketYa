package org.group10.notification.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code event.published}, que publica event-service con el mismo contrato que los cambios de un evento (ADR 0004).
 * Es una copia propia: notification-service no depende de las clases de event-service. El {@code change} se ignora
 * porque la routing key ya dice que es una publicación.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventPublishedMessage(UUID messageId, Instant occurredAt, Long eventId) {
}

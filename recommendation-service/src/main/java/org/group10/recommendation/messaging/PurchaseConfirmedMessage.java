package org.group10.recommendation.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code purchase.confirmed} que publica ticket-service cuando una orden queda {@code PAID} (ADR 0004). Es una copia
 * propia del contrato: recommendation-service no depende de las clases de ticket-service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PurchaseConfirmedMessage(UUID messageId, Instant occurredAt, Long orderId, Long userId, Long eventId) {
}

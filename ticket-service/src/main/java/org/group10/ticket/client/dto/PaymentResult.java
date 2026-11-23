package org.group10.ticket.client.dto;

/**
 * Lo que ticket-service usa de la respuesta del payment-service: el estado del pago.
 */
public record PaymentResult(PaymentResultStatus status) {
}

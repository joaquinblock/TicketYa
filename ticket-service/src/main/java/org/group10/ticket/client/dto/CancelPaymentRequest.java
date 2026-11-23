package org.group10.ticket.client.dto;

public record CancelPaymentRequest(String idempotencyKey) {
}

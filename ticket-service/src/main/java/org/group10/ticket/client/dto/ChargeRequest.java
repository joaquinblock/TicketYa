package org.group10.ticket.client.dto;

import org.group10.ticket.entity.PaymentMethod;

import java.math.BigDecimal;

public record ChargeRequest(Long orderId, BigDecimal amount, PaymentMethod paymentMethod, String idempotencyKey) {
}

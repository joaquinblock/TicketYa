package org.group10.ticket.client.dto;

import java.math.BigDecimal;

public record RefundRequest(Long ticketId, Long orderId, BigDecimal amount, String reason, Long returnRequestId) {
}

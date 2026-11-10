package org.group10.payment.dto;

import org.group10.payment.entity.RefundReason;
import org.group10.payment.entity.RefundStatus;

import java.math.BigDecimal;

public record RefundResponse(
        Long id,
        Long ticketId,
        Long paymentId,
        BigDecimal amount,
        RefundReason reason,
        Long returnRequestId,
        RefundStatus status,
        String gatewayId
) {}

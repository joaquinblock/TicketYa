package org.group10.payment.mapper;

import org.group10.payment.dto.PaymentRequest;
import org.group10.payment.dto.PaymentResponse;
import org.group10.payment.entity.Payment;
import org.group10.payment.entity.PaymentStatus;

public final class PaymentMapper {

    private PaymentMapper() {
    }

    public static Payment toPendingEntity(PaymentRequest request) {
        return Payment.builder()
                .orderId(request.orderId())
                .idempotencyKey(request.idempotencyKey())
                .paymentMethod(request.paymentMethod())
                .amount(request.amount())
                .status(PaymentStatus.PENDING)
                .build();
    }

    public static PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getAmount(),
                payment.getPaymentMethod(), payment.getStatus(), payment.getGatewayId());
    }
}

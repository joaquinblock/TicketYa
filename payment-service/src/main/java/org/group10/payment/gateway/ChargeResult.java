package org.group10.payment.gateway;

import org.group10.payment.entity.PaymentStatus;

public record ChargeResult(String gatewayId, PaymentStatus status, String failureReason) {

    public static ChargeResult approved(String gatewayId) {
        return new ChargeResult(gatewayId, PaymentStatus.APPROVED, null);
    }
}

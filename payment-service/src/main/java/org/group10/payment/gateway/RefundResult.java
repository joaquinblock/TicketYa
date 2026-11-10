package org.group10.payment.gateway;

import org.group10.payment.entity.RefundStatus;

public record RefundResult(String gatewayId, RefundStatus status, String failureReason) {

    public static RefundResult credited(String gatewayId) {
        return new RefundResult(gatewayId, RefundStatus.CREDITED, null);
    }

    public static RefundResult failed(String failureReason) {
        return new RefundResult(null, RefundStatus.FAILED, failureReason);
    }
}

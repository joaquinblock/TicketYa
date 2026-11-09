package org.group10.payment.gateway;

import org.group10.payment.entity.Payment;
import org.group10.payment.entity.PaymentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockPaymentGatewayTest {

    private final MockPaymentGateway gateway = new MockPaymentGateway();

    @Test
    void charge_returnsAnApprovedResultWithAGatewayId() {
        ChargeResult result = gateway.charge(paymentWithKey("attempt-1"));

        assertThat(result.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(result.gatewayId()).isNotBlank();
        assertThat(result.failureReason()).isNull();
    }

    @Test
    void charge_sameKey_returnsTheSameGatewayId() {
        ChargeResult first = gateway.charge(paymentWithKey("attempt-1"));
        ChargeResult second = gateway.charge(paymentWithKey("attempt-1"));

        assertThat(second.gatewayId()).isEqualTo(first.gatewayId());
    }

    @Test
    void charge_differentKeys_returnDifferentGatewayIds() {
        ChargeResult first = gateway.charge(paymentWithKey("attempt-1"));
        ChargeResult second = gateway.charge(paymentWithKey("attempt-2"));

        assertThat(second.gatewayId()).isNotEqualTo(first.gatewayId());
    }

    private Payment paymentWithKey(String idempotencyKey) {
        return Payment.builder().idempotencyKey(idempotencyKey).build();
    }
}

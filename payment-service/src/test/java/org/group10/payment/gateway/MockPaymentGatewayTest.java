package org.group10.payment.gateway;

import org.group10.payment.entity.Payment;
import org.group10.payment.entity.PaymentMethod;
import org.group10.payment.entity.PaymentStatus;
import org.group10.payment.entity.Refund;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockPaymentGatewayTest {

    private static final long NO_DELAY = 0;
    private static final long SHORT_DELAY_MS = 50;

    private final Payment payment = Payment.builder()
            .orderId(1L)
            .idempotencyKey("attempt-1")
            .paymentMethod(PaymentMethod.CREDIT_CARD)
            .amount(new BigDecimal("1000.00"))
            .status(PaymentStatus.PENDING)
            .build();

    private final Refund refund = Refund.builder().ticketId(5L).build();

    @Test
    void charge_gatewayUp_approvesWithAGatewayIdDerivedFromTheKey() {
        MockPaymentGateway gateway = gatewayWith(NO_DELAY, false);

        ChargeResult first = gateway.charge(payment);
        ChargeResult repeated = gateway.charge(payment);

        assertThat(first.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(repeated.gatewayId()).isEqualTo(first.gatewayId());
    }

    @Test
    void charge_withDelay_answersAfterTheDelay() {
        MockPaymentGateway gateway = gatewayWith(SHORT_DELAY_MS, false);

        long start = System.nanoTime();
        ChargeResult result = gateway.charge(payment);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(SHORT_DELAY_MS));
        assertThat(result.status()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void charge_gatewayDown_throwsGatewayUnavailable() {
        MockPaymentGateway gateway = gatewayWith(NO_DELAY, true);

        assertThatThrownBy(() -> gateway.charge(payment))
                .isInstanceOf(PaymentGatewayUnavailableException.class);
    }

    @Test
    void refund_gatewayDown_throwsGatewayUnavailable() {
        MockPaymentGateway gateway = gatewayWith(NO_DELAY, true);

        assertThatThrownBy(() -> gateway.refund(refund))
                .isInstanceOf(PaymentGatewayUnavailableException.class);
    }

    @Test
    void changeBehavior_gatewayRecovers_chargesAgainWithoutRestarting() {
        MockPaymentGateway gateway = gatewayWith(NO_DELAY, true);

        gateway.changeBehavior(new MockGatewayBehavior(NO_DELAY, false));

        assertThat(gateway.charge(payment).status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(gateway.behavior()).isEqualTo(new MockGatewayBehavior(NO_DELAY, false));
    }

    @Test
    void charge_interruptedWhileDelayed_throwsGatewayUnavailableAndKeepsTheInterruptFlag() {
        MockPaymentGateway gateway = gatewayWith(10_000, false);
        Thread.currentThread().interrupt();

        try {
            assertThatThrownBy(() -> gateway.charge(payment))
                    .isInstanceOf(PaymentGatewayUnavailableException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private MockPaymentGateway gatewayWith(long delayMs, boolean down) {
        return new MockPaymentGateway(PaymentMethod.DEBIT_CARD, delayMs, down);
    }
}

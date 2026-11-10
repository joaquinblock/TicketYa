package org.group10.payment.gateway;

import org.group10.payment.entity.Payment;
import org.group10.payment.entity.PaymentMethod;
import org.group10.payment.entity.PaymentStatus;
import org.group10.payment.entity.Refund;
import org.group10.payment.entity.RefundStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockPaymentGatewayTest {

    private final MockPaymentGateway gateway = new MockPaymentGateway(PaymentMethod.DEBIT_CARD);

    @Test
    void charge_anyOtherMethod_returnsAnApprovedResultWithAGatewayId() {
        ChargeResult result = gateway.charge(paymentWith("attempt-1", PaymentMethod.CREDIT_CARD));

        assertThat(result.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(result.gatewayId()).isNotBlank();
        assertThat(result.failureReason()).isNull();
    }

    @Test
    void charge_configuredRejectedMethod_returnsARejectedResultWithItsReason() {
        ChargeResult result = gateway.charge(paymentWith("attempt-1", PaymentMethod.DEBIT_CARD));

        assertThat(result.status()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(result.failureReason()).isNotBlank();
        assertThat(result.gatewayId()).isNotBlank();
    }

    @Test
    void charge_rejectedMethodComesFromTheConfiguration() {
        MockPaymentGateway gatewayRejectingCreditCards = new MockPaymentGateway(PaymentMethod.CREDIT_CARD);

        assertThat(gatewayRejectingCreditCards.charge(paymentWith("attempt-1", PaymentMethod.CREDIT_CARD)).status())
                .isEqualTo(PaymentStatus.REJECTED);
        assertThat(gatewayRejectingCreditCards.charge(paymentWith("attempt-1", PaymentMethod.DEBIT_CARD)).status())
                .isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void charge_sameKey_returnsTheSameGatewayId() {
        ChargeResult first = gateway.charge(paymentWith("attempt-1", PaymentMethod.CREDIT_CARD));
        ChargeResult second = gateway.charge(paymentWith("attempt-1", PaymentMethod.CREDIT_CARD));

        assertThat(second.gatewayId()).isEqualTo(first.gatewayId());
    }

    @Test
    void charge_differentKeys_returnDifferentGatewayIds() {
        ChargeResult first = gateway.charge(paymentWith("attempt-1", PaymentMethod.CREDIT_CARD));
        ChargeResult second = gateway.charge(paymentWith("attempt-2", PaymentMethod.CREDIT_CARD));

        assertThat(second.gatewayId()).isNotEqualTo(first.gatewayId());
    }

    @Test
    void refund_anyTicket_isCreditedWithAGatewayId() {
        RefundResult result = gateway.refund(refundOfTicket(1L));

        assertThat(result.status()).isEqualTo(RefundStatus.CREDITED);
        assertThat(result.gatewayId()).isNotBlank();
    }

    @Test
    void refund_paymentMadeWithTheRejectedMethod_isCreditedAnyway() {
        Refund refund = Refund.builder()
                .ticketId(1L)
                .payment(paymentWith("attempt-1", PaymentMethod.DEBIT_CARD))
                .build();

        assertThat(gateway.refund(refund).status()).isEqualTo(RefundStatus.CREDITED);
    }

    @Test
    void refund_sameTicket_returnsTheSameGatewayId() {
        assertThat(gateway.refund(refundOfTicket(1L)).gatewayId())
                .isEqualTo(gateway.refund(refundOfTicket(1L)).gatewayId());
    }

    @Test
    void refund_differentTickets_returnDifferentGatewayIds() {
        assertThat(gateway.refund(refundOfTicket(1L)).gatewayId())
                .isNotEqualTo(gateway.refund(refundOfTicket(2L)).gatewayId());
    }

    private Refund refundOfTicket(Long ticketId) {
        return Refund.builder().ticketId(ticketId).build();
    }

    private Payment paymentWith(String idempotencyKey, PaymentMethod paymentMethod) {
        return Payment.builder().idempotencyKey(idempotencyKey).paymentMethod(paymentMethod).build();
    }
}

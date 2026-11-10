package org.group10.payment.gateway;

import org.group10.payment.entity.Payment;
import org.group10.payment.entity.PaymentMethod;
import org.group10.payment.entity.Refund;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Simula la pasarela de pagos: no mueve dinero ni se conecta a nada.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    private static final String GATEWAY_ID_PREFIX = "MOCK-";
    private static final String REJECTION_REASON = "Fondos insuficientes";
    private static final String REFUND_SOURCE_PREFIX = "refund-ticket-";

    private final PaymentMethod rejectedMethod;

    public MockPaymentGateway(@Value("${payment.mock.rejected-method}") PaymentMethod rejectedMethod) {
        this.rejectedMethod = rejectedMethod;
    }

    @Override
    public ChargeResult charge(Payment payment) {
        String gatewayId = gatewayIdDerivedFrom(payment.getIdempotencyKey());
        return payment.getPaymentMethod() == rejectedMethod
                ? ChargeResult.rejected(gatewayId, REJECTION_REASON)
                : ChargeResult.approved(gatewayId);
    }

    @Override
    public RefundResult refund(Refund refund) {
        return RefundResult.credited(gatewayIdDerivedFrom(REFUND_SOURCE_PREFIX + refund.getTicketId()));
    }

    private String gatewayIdDerivedFrom(String source) {
        return GATEWAY_ID_PREFIX + UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }
}

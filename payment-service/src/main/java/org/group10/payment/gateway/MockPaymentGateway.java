package org.group10.payment.gateway;

import org.group10.payment.entity.Payment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Simula la pasarela de pagos: no mueve dinero ni se conecta a nada.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    private static final String GATEWAY_ID_PREFIX = "MOCK-";

    @Override
    public ChargeResult charge(Payment payment) {
        return ChargeResult.approved(gatewayIdDerivedFrom(payment.getIdempotencyKey()));
    }

    private String gatewayIdDerivedFrom(String idempotencyKey) {
        return GATEWAY_ID_PREFIX + UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8));
    }
}

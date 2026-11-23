package org.group10.payment.gateway;

import org.group10.payment.entity.Payment;
import org.group10.payment.entity.PaymentMethod;
import org.group10.payment.entity.PaymentStatus;
import org.group10.payment.entity.Refund;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simula la pasarela de pagos: no mueve dinero ni se conecta a nada.
 * La demora y la caída se pueden cambiar en ejecución para probar la resiliencia.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    private static final String GATEWAY_ID_PREFIX = "MOCK-";
    private static final String REJECTION_REASON = "Fondos insuficientes";
    private static final String REFUND_SOURCE_PREFIX = "refund-ticket-";

    private final PaymentMethod rejectedMethod;
    private final AtomicReference<MockGatewayBehavior> behavior;

    public MockPaymentGateway(@Value("${payment.mock.rejected-method}") PaymentMethod rejectedMethod,
                              @Value("${payment.mock.delay-ms:0}") long delayMs,
                              @Value("${payment.mock.down:false}") boolean down) {
        this.rejectedMethod = rejectedMethod;
        this.behavior = new AtomicReference<>(new MockGatewayBehavior(delayMs, down));
    }

    @Override
    public ChargeResult charge(Payment payment) {
        simulateTheNetwork();
        String gatewayId = gatewayIdDerivedFrom(payment.getIdempotencyKey());
        return payment.getPaymentMethod() == rejectedMethod
                ? ChargeResult.rejected(gatewayId, REJECTION_REASON)
                : ChargeResult.approved(gatewayId);
    }

    @Override
    public PaymentStatus cancel(Payment payment) {
        simulateTheNetwork();
        return PaymentStatus.CANCELLED;
    }

    @Override
    public RefundResult refund(Refund refund) {
        simulateTheNetwork();
        return RefundResult.credited(gatewayIdDerivedFrom(REFUND_SOURCE_PREFIX + refund.getTicketId()));
    }

    public MockGatewayBehavior behavior() {
        return behavior.get();
    }

    public void changeBehavior(MockGatewayBehavior newBehavior) {
        behavior.set(newBehavior);
    }

    private void simulateTheNetwork() {
        MockGatewayBehavior current = behavior.get();
        waitFor(current.delayMs());
        if (current.down()) {
            throw new PaymentGatewayUnavailableException("La pasarela simulada está caída");
        }
    }

    private void waitFor(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayUnavailableException("Se cortó la espera a la pasarela simulada");
        }
    }

    private String gatewayIdDerivedFrom(String source) {
        return GATEWAY_ID_PREFIX + UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }
}

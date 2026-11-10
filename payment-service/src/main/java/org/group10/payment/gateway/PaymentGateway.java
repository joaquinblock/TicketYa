package org.group10.payment.gateway;

import org.group10.payment.entity.Payment;
import org.group10.payment.entity.Refund;

/**
 * Pasarela de pagos: el sistema externo que mueve el dinero.
 */
public interface PaymentGateway {

    ChargeResult charge(Payment payment);

    RefundResult refund(Refund refund);
}

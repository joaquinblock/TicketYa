package org.group10.payment.gateway;

import org.group10.payment.entity.Payment;

/**
 * Pasarela de pagos: el sistema externo que mueve el dinero.
 */
public interface PaymentGateway {

    ChargeResult charge(Payment payment);
}

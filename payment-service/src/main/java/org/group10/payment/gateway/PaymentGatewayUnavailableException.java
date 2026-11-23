package org.group10.payment.gateway;

/**
 * La pasarela no respondió: no se sabe si el cobro o el reintegro se hizo.
 */
public class PaymentGatewayUnavailableException extends RuntimeException {

    public PaymentGatewayUnavailableException(String message) {
        super(message);
    }
}

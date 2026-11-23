package org.group10.ticket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.group10.common.dto.ErrorDto;
import org.group10.ticket.client.PaymentClient;
import org.group10.ticket.client.dto.CancelPaymentRequest;
import org.group10.ticket.client.dto.PaymentResultStatus;
import org.group10.ticket.entity.Order;
import org.group10.ticket.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Anula el intento de pago sin resultado de una orden que venció o se canceló (US-TKT-23). Si el cobro resulta
 * aprobado, no se emiten entradas: se marca el cobro y se reintegra cada entrada con motivo
 * {@code PURCHASE_COMPENSATION}. Mientras payment no tenga un resultado, se vuelve a preguntar en la siguiente corrida.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentAttemptResolutionService {

    private static final String PAYMENT_NOT_FOUND = "payment_not_found";

    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;
    private final PurchaseCompensationService purchaseCompensationService;
    private final TransactionOperations transactions;
    private final Clock clock;

    public void resolveAttemptsOfClosedOrders() {
        orderRepository.findClosedOrdersWithAnAttemptWithoutResult().forEach(this::resolveLoggingAnyFailure);
    }

    private void resolveLoggingAnyFailure(Order order) {
        try {
            resolve(order);
        } catch (RestClientResponseException answer) {
            if (isPaymentNotFound(answer)) {
                log.info("payment-service no registró el intento de la orden {}: no hubo cobro", order.getId());
                clearAttempt(order);
            } else {
                log.error("payment-service no pudo anular el intento de la orden {} ({}): se reintenta en la "
                        + "próxima corrida", order.getId(), answer.getStatusCode(), answer);
            }
        } catch (RuntimeException withoutAnswer) {
            log.error("No se pudo anular el intento de la orden {}: se reintenta en la próxima corrida",
                    order.getId(), withoutAnswer);
        }
    }

    /**
     * Solo el 404 {@code payment_not_found} dice que el cobro nunca llegó. Otro 404, como una ruta que no existe, no
     * dice nada del pago: limpiar la clave ahí podría dejar sin reintegrar un cobro aprobado.
     */
    private static boolean isPaymentNotFound(RestClientResponseException answer) {
        if (!answer.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return false;
        }
        try {
            ErrorDto error = answer.getResponseBodyAs(ErrorDto.class);
            return error != null && PAYMENT_NOT_FOUND.equals(error.error());
        } catch (RuntimeException unreadableBody) {
            return false;
        }
    }

    private void resolve(Order order) {
        PaymentResultStatus result = paymentClient.cancel(new CancelPaymentRequest(order.getPaymentAttemptKey()))
                .status();
        switch (result) {
            case PENDING -> log.info("El intento de la orden {} sigue sin resultado: se pregunta en la próxima corrida",
                    order.getId());
            case CANCELLED, REJECTED -> clearAttempt(order);
            case APPROVED -> compensateTheLateCharge(order);
        }
    }

    private void compensateTheLateCharge(Order order) {
        log.warn("El intento de la orden {} {} resultó aprobado: se reintegra sin emitir entradas", order.getId(),
                order.getStatus());
        transactions.executeWithoutResult(transaction -> {
            orderRepository.markChargeApproved(order.getId(), LocalDateTime.now(clock));
            orderRepository.clearPaymentAttemptOfClosedOrder(order.getId(), order.getPaymentAttemptKey());
        });
        purchaseCompensationService.refundTicketsOf(order);
    }

    private void clearAttempt(Order order) {
        transactions.executeWithoutResult(transaction ->
                orderRepository.clearPaymentAttemptOfClosedOrder(order.getId(), order.getPaymentAttemptKey()));
    }
}

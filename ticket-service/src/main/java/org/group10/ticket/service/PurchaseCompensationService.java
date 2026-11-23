package org.group10.ticket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.group10.ticket.client.PaymentClient;
import org.group10.ticket.client.dto.RefundRequest;
import org.group10.ticket.entity.Order;
import org.group10.ticket.entity.Ticket;
import org.group10.ticket.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Devuelve el dinero de una compra que se cobró y no se concretó (US-TKT-22): un reintegro por entrada con
 * motivo {@code PURCHASE_COMPENSATION}. Se pide en el momento y, si falla, lo retoma el job hasta tener respuesta.
 * Pedirlo dos veces es seguro, porque payment-service reintegra una sola vez por {@code ticket_id}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseCompensationService {

    private static final String PURCHASE_COMPENSATION = "PURCHASE_COMPENSATION";

    private final TicketRepository ticketRepository;
    private final PaymentClient paymentClient;
    private final TransactionOperations transactions;
    private final Clock clock;

    public void refundTicketsOf(Order order) {
        ticketRepository.findByOrderIdOrderById(order.getId()).forEach(ticket -> refund(order, ticket));
    }

    public void refundPendingCompensations() {
        ticketRepository.findTicketsToCompensate().forEach(ticket -> refund(ticket.getOrder(), ticket));
    }

    private void refund(Order order, Ticket ticket) {
        try {
            paymentClient.refund(new RefundRequest(ticket.getId(), order.getId(), ticket.getPrice(),
                    PURCHASE_COMPENSATION, null));
            transactions.executeWithoutResult(transaction ->
                    ticketRepository.markCompensated(ticket.getId(), LocalDateTime.now(clock)));
        } catch (RestClientResponseException answer) {
            if (answer.getStatusCode().is4xxClientError()) {
                stopRetrying(order, ticket, answer);
            } else {
                logRetry(order, ticket, answer);
            }
        } catch (RuntimeException withoutAnswer) {
            logRetry(order, ticket, withoutAnswer);
        }
    }

    private void stopRetrying(Order order, Ticket ticket, RestClientResponseException rejection) {
        String error = "%d %s".formatted(rejection.getStatusCode().value(), rejection.getResponseBodyAsString());
        transactions.executeWithoutResult(transaction -> ticketRepository.registerRefundError(ticket.getId(), error));
        log.error("payment-service rechazó el reintegro de compensación de la entrada {} de la orden {} ({}): "
                + "hay que resolverlo a mano", ticket.getId(), order.getId(), error);
    }

    private static void logRetry(Order order, Ticket ticket, RuntimeException failure) {
        log.error("No se pudo reintegrar la entrada {} de la orden {}: se reintenta en la próxima corrida",
                ticket.getId(), order.getId(), failure);
    }
}

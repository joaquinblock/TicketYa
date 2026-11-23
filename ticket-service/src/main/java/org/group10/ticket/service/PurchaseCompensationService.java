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

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Devuelve el dinero de una compra que se cobró y no se concretó (US-TKT-22): un reintegro por entrada con
 * motivo {@code PURCHASE_COMPENSATION}. Pedirlo dos veces es seguro, porque payment-service reintegra una sola
 * vez por {@code ticket_id}.
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
        ticketRepository.findByOrderIdOrderById(order.getId()).forEach(ticket -> refundLoggingAnyFailure(order, ticket));
    }

    private void refundLoggingAnyFailure(Order order, Ticket ticket) {
        try {
            paymentClient.refund(new RefundRequest(ticket.getId(), order.getId(), ticket.getPrice(),
                    PURCHASE_COMPENSATION, null));
            transactions.executeWithoutResult(transaction ->
                    ticketRepository.markCompensated(ticket.getId(), LocalDateTime.now(clock)));
        } catch (RuntimeException failure) {
            log.error("No se pudo reintegrar la entrada {} de la orden {}", ticket.getId(), order.getId(), failure);
        }
    }
}

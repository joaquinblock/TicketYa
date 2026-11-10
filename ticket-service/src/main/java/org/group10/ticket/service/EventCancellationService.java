package org.group10.ticket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.group10.ticket.client.PaymentClient;
import org.group10.ticket.client.dto.RefundRequest;
import org.group10.ticket.entity.OrderStatus;
import org.group10.ticket.entity.Ticket;
import org.group10.ticket.repository.OrderRepository;
import org.group10.ticket.repository.ReturnRequestRepository;
import org.group10.ticket.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventCancellationService {

    private static final String EVENT_CANCELLED_REASON = "EVENT_CANCELLED";

    private final OrderRepository orderRepository;
    private final OrderClosingService orderClosingService;
    private final ReturnRequestRepository returnRequestRepository;
    private final TicketRepository ticketRepository;
    private final PaymentClient paymentClient;
    private final TransactionOperations transactions;

    public void closeOpenPurchasesOfTheEvent(Long eventId) {
        orderRepository.findIdsOfPendingOrdersOfEvent(eventId).forEach(orderId ->
                orderClosingService.closePendingOrderReleasingItsPlaces(orderId, OrderStatus.CANCELLED));
        returnRequestRepository.cancelPendingRequestsOfEvent(eventId);
    }

    public void refundTicketsOfCancelledEvents() {
        ticketRepository.findTicketsToRefundOfCancelledEvents().forEach(this::refundLoggingAnyFailure);
    }

    private void refundLoggingAnyFailure(Ticket ticket) {
        try {
            paymentClient.refund(new RefundRequest(ticket.getId(), ticket.getOrder().getId(), ticket.getPrice(),
                    EVENT_CANCELLED_REASON, null));
            transactions.executeWithoutResult(transaction ->
                    ticketRepository.markSoldTicketAsRefunded(ticket.getId()));
        } catch (RuntimeException failure) {
            log.error("No se pudo reintegrar la entrada {} del evento cancelado {}", ticket.getId(),
                    ticket.getOrder().getEventId(), failure);
        }
    }
}

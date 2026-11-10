package org.group10.ticket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.group10.common.dto.ErrorDto;
import org.group10.common.exception.TicketYaException;
import org.group10.ticket.client.PaymentClient;
import org.group10.ticket.client.dto.ChargeRequest;
import org.group10.ticket.client.dto.RefundRequest;
import org.group10.ticket.config.ReservationProperties;
import org.group10.ticket.dto.OrderResponse;
import org.group10.ticket.dto.PayOrderRequest;
import org.group10.ticket.entity.Order;
import org.group10.ticket.entity.OrderStatus;
import org.group10.ticket.entity.PaymentMethod;
import org.group10.ticket.entity.Ticket;
import org.group10.ticket.entity.TicketStatus;
import org.group10.ticket.exception.TicketErrorCode;
import org.group10.ticket.repository.EventSeatRepository;
import org.group10.ticket.repository.OrderRepository;
import org.group10.ticket.repository.TicketRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderPaymentService {

    private static final String PURCHASE_COMPENSATION = "PURCHASE_COMPENSATION";
    private static final String REJECTED_ATTEMPTS = "rejectedAttempts";

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final EventSeatRepository eventSeatRepository;
    private final OrderQueryService orderQueryService;
    private final OrderClosingService orderClosingService;
    private final PaymentClient paymentClient;
    private final TransactionOperations transactions;
    private final ReservationProperties reservationProperties;
    private final Clock clock;

    public OrderResponse pay(Long userId, Long orderId, PayOrderRequest request) {
        Order order = orderQueryService.findOrderOfCustomer(userId, orderId);
        if (order.getStatus() == OrderStatus.PAID) {
            return orderQueryService.toResponse(order);
        }
        ensureAttemptCanStart(order, request.idempotencyKey());

        Integer takenOrders = transactions.execute(transaction ->
                orderRepository.takeOrderForPaymentAttempt(orderId, request.idempotencyKey()));
        if (takenOrders == null || takenOrders == 0) {
            return answerWhenTheOrderCouldNotBeTaken(orderId);
        }

        chargeRegisteringAnyRejection(order, request);
        return issueTicketsOrCompensate(order, request.paymentMethod());
    }

    private void ensureAttemptCanStart(Order order, String attemptKey) {
        ensureOrderIsPending(order);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!order.getExpiresAt().isAfter(now)) {
            throw rejectionOfExpiredReservation(order);
        }
        boolean isNewAttempt = !attemptKey.equals(order.getPaymentAttemptKey());
        if (isNewAttempt && !now.isBefore(order.getExpiresAt().minus(reservationProperties.paymentMargin()))) {
            throw new TicketYaException(TicketErrorCode.PAYMENT_WINDOW_CLOSED,
                    "La reserva de la orden %d está por vencer y ya no acepta pagos nuevos".formatted(order.getId()));
        }
    }

    private OrderResponse answerWhenTheOrderCouldNotBeTaken(Long orderId) {
        Order current = findOrder(orderId);
        if (current.getStatus() == OrderStatus.PAID) {
            return orderQueryService.toResponse(current);
        }
        ensureOrderIsPending(current);
        throw new TicketYaException(TicketErrorCode.PAYMENT_IN_PROGRESS,
                "La orden %d tiene otro pago en curso".formatted(orderId));
    }

    private void chargeRegisteringAnyRejection(Order order, PayOrderRequest request) {
        try {
            paymentClient.charge(new ChargeRequest(order.getId(), order.getTotal(), request.paymentMethod(),
                    request.idempotencyKey()));
        } catch (RestClientResponseException failure) {
            if (failure.getStatusCode().isSameCodeAs(HttpStatus.UNPROCESSABLE_CONTENT)) {
                registerRejection(order.getId(), request.idempotencyKey(), rejectedAttemptsOf(failure));
            }
            throw failure;
        }
    }

    private void registerRejection(Long orderId, String attemptKey, int rejectedAttempts) {
        if (rejectedAttempts >= reservationProperties.maxRejectedPayments()) {
            orderClosingService.closePendingOrderReleasingItsPlaces(orderId, OrderStatus.REJECTED);
        } else {
            transactions.executeWithoutResult(transaction ->
                    orderRepository.clearPaymentAttempt(orderId, attemptKey));
        }
    }

    private static int rejectedAttemptsOf(RestClientResponseException rejection) {
        ErrorDto error = rejection.getResponseBodyAs(ErrorDto.class);
        return ((Number) error.detail().get(REJECTED_ATTEMPTS)).intValue();
    }

    private OrderResponse issueTicketsOrCompensate(Order order, PaymentMethod paymentMethod) {
        if (issueTicketsIfThePlacesAreStillHeld(order, paymentMethod)) {
            return orderQueryService.toResponse(findOrder(order.getId()));
        }

        orderClosingService.closePendingOrderReleasingItsPlaces(order.getId(), OrderStatus.EXPIRED);
        Order current = findOrder(order.getId());
        if (current.getStatus() == OrderStatus.PAID) {
            return orderQueryService.toResponse(current);
        }
        refundTicketsOfOrder(current);
        throw rejectionOf(current);
    }

    private boolean issueTicketsIfThePlacesAreStillHeld(Order order, PaymentMethod paymentMethod) {
        return Boolean.TRUE.equals(transactions.execute(transaction -> {
            List<Ticket> tickets = ticketRepository.findByOrderIdOrderById(order.getId());
            boolean issued = order.getExpiresAt().isAfter(LocalDateTime.now(clock))
                    && sellsEverySeatOfTheOrder(order, tickets)
                    && orderRepository.markPendingOrderAsPaid(order.getId(), paymentMethod) == 1;

            if (issued) {
                tickets.stream().filter(ticket -> ticket.getStatus() == TicketStatus.RESERVED).forEach(Ticket::issue);
            } else {
                transaction.setRollbackOnly();
            }
            return issued;
        }));
    }

    private boolean sellsEverySeatOfTheOrder(Order order, List<Ticket> tickets) {
        long ticketsWithSeat = tickets.stream().filter(ticket -> ticket.getSeatId() != null).count();
        return eventSeatRepository.sellSeatsHeldByOrder(order.getId()) == ticketsWithSeat;
    }

    private void refundTicketsOfOrder(Order order) {
        ticketRepository.findByOrderIdOrderById(order.getId()).forEach(ticket -> refundLoggingAnyFailure(order, ticket));
    }

    private void refundLoggingAnyFailure(Order order, Ticket ticket) {
        try {
            paymentClient.refund(new RefundRequest(ticket.getId(), order.getId(), ticket.getPrice(),
                    PURCHASE_COMPENSATION, null));
        } catch (RuntimeException failure) {
            log.error("No se pudo reintegrar la entrada {} de la orden {}", ticket.getId(), order.getId(), failure);
        }
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow();
    }

    private static void ensureOrderIsPending(Order order) {
        if (order.getStatus() != OrderStatus.PENDING) {
            throw rejectionOf(order);
        }
    }

    private static TicketYaException rejectionOf(Order order) {
        return order.getStatus() == OrderStatus.EXPIRED
                ? rejectionOfExpiredReservation(order)
                : new TicketYaException(TicketErrorCode.ORDER_NOT_PAYABLE,
                        "La orden %d está %s y no se puede pagar".formatted(order.getId(), order.getStatus()));
    }

    private static TicketYaException rejectionOfExpiredReservation(Order order) {
        return new TicketYaException(TicketErrorCode.RESERVATION_EXPIRED,
                "La reserva de la orden %d ya venció".formatted(order.getId()));
    }
}

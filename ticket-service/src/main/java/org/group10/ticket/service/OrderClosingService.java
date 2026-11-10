package org.group10.ticket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.group10.common.exception.TicketYaException;
import org.group10.ticket.dto.OrderResponse;
import org.group10.ticket.entity.Order;
import org.group10.ticket.entity.OrderStatus;
import org.group10.ticket.exception.TicketErrorCode;
import org.group10.ticket.repository.EventSeatRepository;
import org.group10.ticket.repository.EventSectorStockRepository;
import org.group10.ticket.repository.OrderRepository;
import org.group10.ticket.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderClosingService {

    private final OrderQueryService orderQueryService;
    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final EventSeatRepository eventSeatRepository;
    private final EventSectorStockRepository eventSectorStockRepository;
    private final TransactionOperations transactions;
    private final Clock clock;

    public OrderResponse cancelReservation(Long userId, Long orderId) {
        orderQueryService.findOrderOfCustomer(userId, orderId);
        transactions.executeWithoutResult(transaction -> {
            if (orderRepository.cancelPendingOrderWithoutPaymentInProgress(orderId) == 1) {
                releasePlacesOfOrder(orderId);
            }
        });

        Order order = orderQueryService.findOrderOfCustomer(userId, orderId);
        ensureOrderEndedCancelled(order);
        return orderQueryService.toResponse(order);
    }

    public void expireOverdueReservations() {
        orderRepository.findIdsOfPendingOrdersOverdueAt(LocalDateTime.now(clock))
                .forEach(this::expireOrderLoggingAnyFailure);
    }

    public void closePendingOrderReleasingItsPlaces(Long orderId, OrderStatus nextStatus) {
        transactions.executeWithoutResult(transaction -> {
            if (orderRepository.closePendingOrder(orderId, nextStatus) == 1) {
                releasePlacesOfOrder(orderId);
            }
        });
    }

    private void expireOrderLoggingAnyFailure(Long orderId) {
        try {
            closePendingOrderReleasingItsPlaces(orderId, OrderStatus.EXPIRED);
        } catch (RuntimeException failure) {
            log.error("No se pudo vencer la orden {}", orderId, failure);
        }
    }

    private void releasePlacesOfOrder(Long orderId) {
        ticketRepository.countReservedPlacesWithoutSeatOfOrder(orderId).forEach(reserved ->
                eventSectorStockRepository.releasePlaces(reserved.eventSectorId(), reserved.places()));
        ticketRepository.voidReservedTicketsOfOrder(orderId);
        eventSeatRepository.releaseSeatsHeldByOrder(orderId);
    }

    private static void ensureOrderEndedCancelled(Order order) {
        switch (order.getStatus()) {
            case CANCELLED -> {
            }
            case PENDING -> throw new TicketYaException(TicketErrorCode.PAYMENT_IN_PROGRESS,
                    "La orden %d tiene un pago en curso".formatted(order.getId()));
            case EXPIRED -> throw new TicketYaException(TicketErrorCode.RESERVATION_EXPIRED,
                    "La reserva de la orden %d ya venció".formatted(order.getId()));
            default -> throw new TicketYaException(TicketErrorCode.ORDER_NOT_CANCELLABLE,
                    "La orden %d está %s y no se puede abandonar".formatted(order.getId(), order.getStatus()));
        }
    }
}

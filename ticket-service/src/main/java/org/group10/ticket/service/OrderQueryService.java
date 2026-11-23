package org.group10.ticket.service;

import lombok.RequiredArgsConstructor;
import org.group10.common.exception.TicketYaException;
import org.group10.ticket.dto.OrderResponse;
import org.group10.ticket.entity.EventSale;
import org.group10.ticket.entity.Order;
import org.group10.ticket.entity.OrderStatus;
import org.group10.ticket.entity.Ticket;
import org.group10.ticket.exception.TicketErrorCode;
import org.group10.ticket.mapper.OrderMapper;
import org.group10.ticket.repository.EventSaleRepository;
import org.group10.ticket.repository.OrderRepository;
import org.group10.ticket.repository.TicketRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final EventSaleRepository eventSaleRepository;

    public List<OrderResponse> findOrdersOfCustomer(Long userId) {
        List<Order> orders = orderRepository.findByUserIdOrderByIdDesc(userId);
        Map<Long, List<Ticket>> ticketsByOrder = ticketRepository
                .findByOrderIdInOrderById(orders.stream().map(Order::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(ticket -> ticket.getOrder().getId()));
        Map<Long, EventSale> salesByEvent = eventSaleRepository
                .findAllById(orders.stream().map(Order::getEventId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(EventSale::getEventId, Function.identity()));

        return orders.stream()
                .map(order -> OrderMapper.toResponse(order, salesByEvent.get(order.getEventId()),
                        ticketsByOrder.getOrDefault(order.getId(), List.of())))
                .toList();
    }

    public OrderResponse findOrderDetailOfCustomer(Long userId, Long orderId) {
        return toResponse(findOrderOfCustomer(userId, orderId));
    }

    /**
     * La orden pagada con sus entradas y su QR, para otro servicio (el mail de la compra del notification-service).
     * No filtra por cliente: la ruta es interna. Solo responde órdenes PAID, para no avisar una compra que no se
     * concretó.
     *
     * @throws TicketYaException {@code ORDER_NOT_FOUND} o {@code ORDER_NOT_PAID}
     */
    public OrderResponse findPaidOrderDetail(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new TicketYaException(TicketErrorCode.ORDER_NOT_FOUND,
                        "No se encontró la orden con id %d".formatted(orderId)));
        if (order.getStatus() != OrderStatus.PAID) {
            throw new TicketYaException(TicketErrorCode.ORDER_NOT_PAID,
                    "La orden %d no está pagada: está %s".formatted(orderId, order.getStatus()));
        }
        return toResponse(order);
    }

    /**
     * Los usuarios con alguna orden PAID del evento, una vez cada uno aunque tengan varias, para que el
     * notification-service les avise un cambio del evento. Un evento sin compras devuelve una lista vacía.
     */
    public List<Long> findBuyersOfEvent(Long eventId) {
        return orderRepository.findDistinctUserIdsOfPaidOrdersOfEvent(eventId);
    }

    public Order findOrderOfCustomer(Long userId, Long orderId) {
        return orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new TicketYaException(TicketErrorCode.ORDER_NOT_FOUND,
                        "No se encontró la orden con id %d".formatted(orderId)));
    }

    public OrderResponse toResponse(Order order) {
        return toResponse(order, ticketRepository.findByOrderIdOrderById(order.getId()));
    }

    public OrderResponse toResponse(Order order, List<Ticket> tickets) {
        return OrderMapper.toResponse(order, eventSaleRepository.findById(order.getEventId()).orElseThrow(), tickets);
    }
}

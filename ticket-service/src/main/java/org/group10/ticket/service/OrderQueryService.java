package org.group10.ticket.service;

import lombok.RequiredArgsConstructor;
import org.group10.common.exception.TicketYaException;
import org.group10.ticket.dto.OrderResponse;
import org.group10.ticket.entity.Order;
import org.group10.ticket.entity.Ticket;
import org.group10.ticket.exception.TicketErrorCode;
import org.group10.ticket.mapper.OrderMapper;
import org.group10.ticket.repository.OrderRepository;
import org.group10.ticket.repository.TicketRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;

    public List<OrderResponse> findOrdersOfCustomer(Long userId) {
        List<Order> orders = orderRepository.findByUserIdOrderByIdDesc(userId);
        Map<Long, List<Ticket>> ticketsByOrder = ticketRepository
                .findByOrderIdInOrderById(orders.stream().map(Order::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(ticket -> ticket.getOrder().getId()));

        return orders.stream()
                .map(order -> OrderMapper.toResponse(order, ticketsByOrder.getOrDefault(order.getId(), List.of())))
                .toList();
    }

    public OrderResponse findOrderDetailOfCustomer(Long userId, Long orderId) {
        return toResponse(findOrderOfCustomer(userId, orderId));
    }

    public Order findOrderOfCustomer(Long userId, Long orderId) {
        return orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new TicketYaException(TicketErrorCode.ORDER_NOT_FOUND,
                        "No se encontró la orden con id %d".formatted(orderId)));
    }

    public OrderResponse toResponse(Order order) {
        return OrderMapper.toResponse(order, ticketRepository.findByOrderIdOrderById(order.getId()));
    }
}

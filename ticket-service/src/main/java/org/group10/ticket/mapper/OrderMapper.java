package org.group10.ticket.mapper;

import org.group10.ticket.dto.OrderResponse;
import org.group10.ticket.dto.TicketResponse;
import org.group10.ticket.entity.Order;
import org.group10.ticket.entity.Ticket;
import org.group10.ticket.entity.TicketStatus;

import java.util.List;

public final class OrderMapper {

    private OrderMapper() {
    }

    public static OrderResponse toResponse(Order order, List<Ticket> tickets) {
        return new OrderResponse(order.getId(), order.getEventId(), order.getStatus(), order.getExpiresAt(),
                order.getTotal(), order.getPaymentMethod(), tickets.stream().map(OrderMapper::toTicketResponse).toList());
    }

    private static TicketResponse toTicketResponse(Ticket ticket) {
        return new TicketResponse(ticket.getId(), ticket.getEventSectorId(), ticket.getSeatId(), ticket.getPrice(),
                ticket.getStatus(), ticket.getStatus() == TicketStatus.ISSUED ? ticket.getQrCode() : null);
    }
}

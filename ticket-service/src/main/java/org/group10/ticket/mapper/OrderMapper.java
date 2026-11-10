package org.group10.ticket.mapper;

import org.group10.ticket.dto.OrderResponse;
import org.group10.ticket.dto.OrderResponse.EventOfOrder;
import org.group10.ticket.dto.TicketResponse;
import org.group10.ticket.entity.EventSale;
import org.group10.ticket.entity.Order;
import org.group10.ticket.entity.Ticket;
import org.group10.ticket.entity.TicketStatus;

import java.util.List;

public final class OrderMapper {

    private OrderMapper() {
    }

    public static OrderResponse toResponse(Order order, EventSale sale, List<Ticket> tickets) {
        return new OrderResponse(order.getId(), order.getEventId(), toEventOfOrder(sale), order.getStatus(),
                order.getExpiresAt(), order.getTotal(), order.getPaymentMethod(),
                tickets.stream().map(OrderMapper::toTicketResponse).toList());
    }

    private static EventOfOrder toEventOfOrder(EventSale sale) {
        return new EventOfOrder(sale.getEventName(), sale.getDateTime(), sale.getVenueName(), sale.getVenueAddress(),
                sale.getVenueCity());
    }

    private static TicketResponse toTicketResponse(Ticket ticket) {
        return new TicketResponse(ticket.getId(), ticket.getEventSectorId(), ticket.getSectorName(),
                ticket.getSeatId(), ticket.getRow(), ticket.getNumber(), ticket.getPrice(), ticket.getStatus(),
                ticket.getStatus() == TicketStatus.ISSUED ? ticket.getQrCode() : null);
    }
}

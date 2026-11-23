package org.group10.notification.client;

import org.group10.notification.dto.PaidOrder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

@HttpExchange
public interface TicketClient {

    /**
     * 404 si la orden no existe, 409 si no está pagada.
     */
    @GetExchange("/internal/orders/{id}")
    PaidOrder findPaidOrder(@PathVariable Long id);

    /**
     * Los ids de los usuarios con alguna orden pagada del evento, sin repetir. Vacío si no hay.
     */
    @GetExchange("/internal/events/{eventId}/buyers")
    List<Long> findBuyers(@PathVariable Long eventId);
}

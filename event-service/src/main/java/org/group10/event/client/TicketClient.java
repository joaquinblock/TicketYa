package org.group10.event.client;

import org.group10.event.client.dto.EventSaleRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PutExchange;

@HttpExchange
public interface TicketClient {

    @PutExchange("/internal/event-sales/{eventId}")
    void synchronizeEventSale(@PathVariable Long eventId, @RequestBody EventSaleRequest request);
}

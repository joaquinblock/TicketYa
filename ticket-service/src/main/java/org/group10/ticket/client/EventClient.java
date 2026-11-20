package org.group10.ticket.client;

import org.group10.ticket.client.dto.EventReportEventResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PutExchange;

@HttpExchange
public interface EventClient {

    @GetExchange("/api/events/{eventId}")
    EventReportEventResponse findEventForReport(@PathVariable Long eventId);

    @PutExchange("/internal/events/{eventId}/sold-out")
    void markEventSoldOut(@PathVariable Long eventId);

    @PutExchange("/internal/events/{eventId}/back-on-sale")
    void markEventBackOnSale(@PathVariable Long eventId);
}

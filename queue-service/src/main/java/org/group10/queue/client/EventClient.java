package org.group10.queue.client;

import org.group10.queue.client.dto.QueueEventResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange
public interface EventClient {

    @GetExchange("/api/events/{eventId}")
    QueueEventResponse findEventForQueue(@PathVariable Long eventId);
}

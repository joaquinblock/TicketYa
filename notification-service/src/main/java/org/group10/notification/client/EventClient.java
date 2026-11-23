package org.group10.notification.client;

import org.group10.notification.dto.EventDetail;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange
public interface EventClient {

    /**
     * 404 si el evento no existe o es un borrador.
     */
    @GetExchange("/api/events/{id}")
    EventDetail findEvent(@PathVariable Long id);
}

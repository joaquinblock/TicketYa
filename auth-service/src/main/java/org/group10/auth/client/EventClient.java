package org.group10.auth.client;

import org.group10.auth.client.dto.EventOrganizationResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange
public interface EventClient {

    @GetExchange("/api/events/{eventId}")
    EventOrganizationResponse findEvent(@PathVariable Long eventId);
}

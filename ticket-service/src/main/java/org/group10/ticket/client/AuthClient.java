package org.group10.ticket.client;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange
public interface AuthClient {

    /**
     * 204 si el personal está asignado al evento; 403 {@code staff_not_assigned} si no.
     */
    @GetExchange("/internal/access-staff/{staffId}/events/{eventId}")
    void ensureStaffAssigned(@PathVariable Long staffId, @PathVariable Long eventId);
}

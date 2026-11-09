package org.group10.event.client;

import lombok.AllArgsConstructor;
import org.group10.event.client.dto.OrganizationResponse;
import org.group10.event.client.dto.OrganizerResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Client del auth-service. Si responde 4xx (p. ej. {@code organizer_not_found}), el
 * {@code GlobalExceptionHandler} de common reenvía ese mismo error al cliente; si no responde, da 503.
 */

@HttpExchange("/api")
public interface AuthClient {

    @GetExchange("/organizations/{id}")
    OrganizationResponse getOrganization(@PathVariable Long id);

    /**
     * Only active organizers: one that was removed, or whose user was deleted, gives 404.
     */
    @GetExchange("/organizers/{userId}")
    OrganizerResponse getOrganizer(@PathVariable Long userId);
}

package org.group10.ticket.client;

import org.group10.ticket.client.dto.UserPermissionsResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/api")
public interface AuthClient {

    @GetExchange("/users/{id}/permissions")
    UserPermissionsResponse getPermissions(@PathVariable Long id);
}

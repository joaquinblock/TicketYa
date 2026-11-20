package org.group10.notification.client;

import org.group10.notification.dto.UserContact;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange
public interface AuthClient {

    /**
     * 404 si el usuario no existe o está dado de baja.
     */
    @GetExchange("/internal/users/{id}")
    UserContact findContact(@PathVariable Long id);
}

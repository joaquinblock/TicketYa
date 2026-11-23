package org.group10.recommendation.client;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange
public interface EventClient {

    /**
     * Solo importa que exista: un id inexistente sale como {@code HttpClientErrorException.NotFound}, y el
     * {@code GlobalExceptionHandler} le devuelve al cliente el {@code participant_not_found} de event-service.
     */
    @GetExchange("/api/participants/{id}")
    void getParticipant(@PathVariable Long id);
}

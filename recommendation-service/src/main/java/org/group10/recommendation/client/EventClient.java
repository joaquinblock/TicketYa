package org.group10.recommendation.client;

import org.group10.recommendation.dto.EventDetail;
import org.group10.recommendation.dto.EventPage;
import org.group10.recommendation.entity.EventType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

@HttpExchange
public interface EventClient {

    /**
     * Solo importa que exista: un id inexistente sale como {@code HttpClientErrorException.NotFound}, y el
     * {@code GlobalExceptionHandler} le devuelve al cliente el {@code participant_not_found} de event-service.
     */
    @GetExchange("/api/participants/{id}")
    void getParticipant(@PathVariable Long id);

    /**
     * La búsqueda pública: solo trae eventos {@code PUBLISHED} o {@code SOLD_OUT} con fecha futura, ordenados por
     * fecha. Un filtro en {@code null} no se manda. {@code participantId} y {@code type} se combinan con <i>o</i>.
     */
    @GetExchange("/api/events")
    EventPage search(@RequestParam(required = false) List<Long> participantId,
                     @RequestParam(required = false) List<EventType> type,
                     @RequestParam(required = false) String city,
                     @RequestParam int size);

    /** Un evento que no es público sale como {@code HttpClientErrorException.NotFound}. */
    @GetExchange("/api/events/{id}")
    EventDetail findEvent(@PathVariable Long id);
}

package org.group10.notification.client;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

@HttpExchange
public interface RecommendationClient {

    /**
     * Los ids de los clientes que tienen como favorito a alguno de los participantes y guardaron esa ciudad, sin
     * repetir. Vacío si no hay.
     */
    @GetExchange("/internal/fans")
    List<Long> findFans(@RequestParam List<Long> participantIds, @RequestParam String city);
}

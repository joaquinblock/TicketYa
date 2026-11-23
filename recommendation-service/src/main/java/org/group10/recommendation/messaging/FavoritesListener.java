package org.group10.recommendation.messaging;

import lombok.RequiredArgsConstructor;
import org.group10.recommendation.client.EventClient;
import org.group10.recommendation.config.RabbitConfig;
import org.group10.recommendation.dto.EventDetail;
import org.group10.recommendation.service.PurchaseHistoryService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Escucha {@code purchase.confirmed} en {@code recommendation.favorites}: pide el evento a event-service y cuenta sus
 * participantes, lugar y organización para inferir favoritos. Va en otra cola que el historial
 * ({@link PurchaseConfirmedListener}) para que el historial no dependa de event-service.
 * <ul>
 *   <li>Sin {@code userId} o {@code eventId}, o con un evento que event-service no muestra (404), no hay nada que
 *       reintentar: va directo a la DLQ.</li>
 *   <li>Con event-service caído, la excepción sube y el contenedor reintenta (ver {@link RabbitConfig}).</li>
 *   <li>Un mensaje repetido no suma dos veces (ver {@link PurchaseHistoryService#countFavorites}).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class FavoritesListener {

    private final EventClient eventClient;
    private final PurchaseHistoryService purchaseHistoryService;

    @RabbitListener(queues = RabbitConfig.FAVORITES_QUEUE)
    public void onPurchaseConfirmed(PurchaseConfirmedMessage message) {
        if (message.userId() == null || message.eventId() == null) {
            throw new AmqpRejectAndDontRequeueException("purchase.confirmed sin userId o eventId: " + message);
        }
        EventDetail event;
        try {
            event = eventClient.findEvent(message.eventId());
        } catch (HttpClientErrorException.NotFound e) {
            throw new AmqpRejectAndDontRequeueException("event-service no muestra el evento " + message.eventId(), e);
        }
        Long venueId = event.venue() == null ? null : event.venue().id();
        purchaseHistoryService.countFavorites(message.userId(), message.eventId(), event.participantIds(), venueId,
                event.organizationId());
    }
}

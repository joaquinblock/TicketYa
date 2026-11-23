package org.group10.notification.messaging;

import org.group10.notification.config.RabbitConfig;
import org.group10.notification.service.FavoriteEventNotificationService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Escucha las publicaciones de eventos en {@code notification.favorites} y avisa a los fans de sus participantes que
 * viven en la ciudad del lugar (US-NOT-03).
 * <ul>
 *   <li>Sin {@code messageId} o {@code eventId} no hay nada que reintentar: va directo a la DLQ.</li>
 *   <li>Si falla el aviso de algún fan, la excepción sube y el contenedor reintenta el mensaje: los fans ya avisados
 *       se saltean (ver {@link FavoriteEventNotificationService}).</li>
 * </ul>
 */
@Component
public class EventPublishedListener {

    private final FavoriteEventNotificationService notificationService;

    public EventPublishedListener(FavoriteEventNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RabbitConfig.FAVORITES_QUEUE)
    public void onEventPublished(EventPublishedMessage message) {
        if (message.messageId() == null || message.eventId() == null) {
            throw new AmqpRejectAndDontRequeueException("Publicación sin messageId o eventId: " + message);
        }
        notificationService.notifyFans(message.messageId(), message.eventId());
    }
}

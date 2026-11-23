package org.group10.notification.messaging;

import org.group10.notification.config.RabbitConfig;
import org.group10.notification.service.EventChangeNotificationService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Escucha los cambios de un evento en {@code notification.event} y avisa a cada comprador (US-NOT-02).
 * <ul>
 *   <li>Sin {@code messageId}, {@code eventId} o {@code change} no hay nada que reintentar: va directo a la DLQ.</li>
 *   <li>Si falla el aviso de algún comprador, la excepción sube y el contenedor reintenta el mensaje: los compradores
 *       ya avisados se saltean (ver {@link EventChangeNotificationService}).</li>
 * </ul>
 */
@Component
public class EventChangedListener {

    private final EventChangeNotificationService notificationService;

    public EventChangedListener(EventChangeNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RabbitConfig.EVENT_QUEUE)
    public void onEventChanged(EventChangedMessage message) {
        if (message.messageId() == null || message.eventId() == null || message.change() == null) {
            throw new AmqpRejectAndDontRequeueException("Cambio de evento sin messageId, eventId o change: " + message);
        }
        notificationService.notifyChange(message.messageId(), message.eventId(), message.change());
    }
}

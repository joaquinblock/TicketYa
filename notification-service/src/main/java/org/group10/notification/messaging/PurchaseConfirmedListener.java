package org.group10.notification.messaging;

import lombok.extern.slf4j.Slf4j;
import org.group10.notification.config.RabbitConfig;
import org.group10.notification.service.PurchaseNotificationService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Escucha {@code purchase.confirmed} en {@code notification.purchase} y manda el mail de la compra (US-NOT-01).
 * <ul>
 *   <li>Sin {@code messageId}, {@code orderId} o {@code userId} no hay nada que reintentar: va directo a la DLQ.</li>
 *   <li>Un mensaje ya procesado choca con la clave del {@code messageId}. Se atrapa acá, fuera de la transacción del
 *       servicio (que ya hizo rollback), y se confirma sin mandar otro mail.</li>
 *   <li>Cualquier otra excepción sube y el contenedor reintenta (ver {@link RabbitConfig}).</li>
 * </ul>
 */
@Slf4j
@Component
public class PurchaseConfirmedListener {

    private final PurchaseNotificationService notificationService;

    public PurchaseConfirmedListener(PurchaseNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RabbitConfig.PURCHASE_QUEUE)
    public void onPurchaseConfirmed(PurchaseConfirmedMessage message) {
        if (message.messageId() == null || message.orderId() == null || message.userId() == null) {
            throw new AmqpRejectAndDontRequeueException("purchase.confirmed sin messageId, orderId o userId: " + message);
        }
        try {
            notificationService.notifyPurchase(message.messageId(), message.orderId(), message.userId());
        } catch (DataIntegrityViolationException e) {
            log.info("Mensaje repetido {} de la orden {}: el mail ya se envió", message.messageId(), message.orderId());
        }
    }
}

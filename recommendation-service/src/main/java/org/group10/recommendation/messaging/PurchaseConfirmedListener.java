package org.group10.recommendation.messaging;

import lombok.RequiredArgsConstructor;
import org.group10.common.tracing.CorrelationHeaders;
import org.group10.recommendation.config.RabbitConfig;
import org.group10.recommendation.service.PurchaseHistoryService;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Escucha {@code purchase.confirmed} en {@code recommendation.purchase} y suma el evento al historial del comprador.
 * <ul>
 *   <li>Sin {@code userId} o {@code eventId} no hay nada que reintentar: va directo a la DLQ.</li>
 *   <li>Un mensaje repetido no necesita control propio: guardar el mismo evento dos veces no cambia el historial.</li>
 *   <li>Cualquier otra excepción (MongoDB caído) sube y el contenedor reintenta (ver {@link RabbitConfig}).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class PurchaseConfirmedListener {

    private final PurchaseHistoryService purchaseHistoryService;

    public void onPurchaseConfirmed(PurchaseConfirmedMessage message) {
        onPurchaseConfirmed(message, null);
    }

    @RabbitListener(queues = RabbitConfig.PURCHASE_QUEUE)
    public void onPurchaseConfirmed(PurchaseConfirmedMessage message,
                                     @Header(name = CorrelationHeaders.HEADER, required = false) String correlationId) {
        MDC.put(CorrelationHeaders.MDC_KEY, CorrelationHeaders.normalize(correlationId));
        try {
            if (message.userId() == null || message.eventId() == null) {
                throw new AmqpRejectAndDontRequeueException("purchase.confirmed sin userId o eventId: " + message);
            }
            purchaseHistoryService.record(message.userId(), message.eventId());
        } finally {
            MDC.remove(CorrelationHeaders.MDC_KEY);
        }
    }
}

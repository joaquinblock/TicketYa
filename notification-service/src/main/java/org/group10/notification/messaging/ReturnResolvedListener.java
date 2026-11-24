package org.group10.notification.messaging;

import lombok.extern.slf4j.Slf4j;
import org.group10.common.logging.CorrelationIds;
import org.group10.notification.config.RabbitConfig;
import org.group10.notification.service.ReturnResolutionNotifier;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Escucha {@code return.resolved} en {@code notification.return} y le avisa al cliente si su devolución se aprobó o se
 * rechazó. Mismo manejo que {@link PurchaseConfirmedListener}: un mensaje incompleto va a la DLQ y uno repetido se
 * confirma sin mandar otro mail.
 */
@Slf4j
@Component
public class ReturnResolvedListener {

    private final ReturnResolutionNotifier notifier;

    public ReturnResolvedListener(ReturnResolutionNotifier notifier) {
        this.notifier = notifier;
    }

    @RabbitListener(queues = RabbitConfig.RETURN_QUEUE)
    public void onReturnResolved(ReturnResolvedMessage message,
                                 @Header(name = CorrelationIds.HEADER_NAME, required = false) String correlationId) {
        MDC.put(CorrelationIds.MDC_KEY, CorrelationIds.validOrNew(correlationId));
        try {
            if (message.messageId() == null || message.userId() == null || message.resolution() == null) {
                throw new AmqpRejectAndDontRequeueException(
                        "return.resolved sin messageId, userId o resolution: " + message);
            }
            try {
                notifier.notifyClient(message);
            } catch (DataIntegrityViolationException e) {
                log.info("Mensaje repetido {} de la solicitud {}: el aviso ya se envió",
                        message.messageId(), message.requestId());
            }
        } finally {
            MDC.remove(CorrelationIds.MDC_KEY);
        }
    }
}

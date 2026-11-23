package org.group10.event.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.group10.common.logging.CorrelationIds;
import org.group10.event.config.RabbitConfig;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Avisa el cambio de un evento recién cuando se confirma la transacción que lo cambió (ADR 0004). Sin outbox: si el
 * broker falla, el aviso se pierde y queda en el log, y la operación responde igual.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventChangedPublisher {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(EventChanged changed) {
        EventChangedMessage message = new EventChangedMessage(UUID.randomUUID(), changed.occurredAt(),
                changed.eventId(), changed.change());
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EVENTS_EXCHANGE, changed.change().routingKey(), message,
                    correlationPostProcessor());
        } catch (AmqpException e) {
            // Spring atraparía la excepción igual, pero la loguearía sin decir de qué evento era el aviso.
            log.warn("No se pudo avisar el cambio {} del evento {}", changed.change(), changed.eventId(), e);
        }
    }

    private static MessagePostProcessor correlationPostProcessor() {
        return amqpMessage -> {
            amqpMessage.getMessageProperties().setHeader(CorrelationIds.HEADER_NAME,
                    CorrelationIds.currentOrNew());
            return amqpMessage;
        };
    }
}

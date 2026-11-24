package org.group10.ticket.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.group10.common.logging.CorrelationIds;
import org.group10.ticket.config.RabbitConfig;
import org.group10.ticket.entity.ReturnRequest;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/**
 * Avisa {@code return.resolved} para que notification-service le escriba al cliente. Se llama después de confirmar
 * la resolución. Igual que la compra (ADR 0004), sin outbox: si el broker falla, el aviso se pierde y queda en el log,
 * y la resolución responde igual.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReturnResolvedPublisher {

    static final String ROUTING_KEY = "return.resolved";

    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;

    public void publish(ReturnRequest request) {
        ReturnResolvedMessage message = new ReturnResolvedMessage(UUID.randomUUID(), clock.instant(), request.getId(),
                request.getTicket().getId(), request.getRequestedBy(), request.getTicket().getOrder().getEventId(),
                request.getStatus().name());
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EVENTS_EXCHANGE, ROUTING_KEY, message, amqpMessage -> {
                amqpMessage.getMessageProperties().setHeader(CorrelationIds.HEADER_NAME, CorrelationIds.currentOrNew());
                return amqpMessage;
            });
        } catch (AmqpException e) {
            log.error("No se pudo avisar la resolución de la solicitud de devolución {}", request.getId(), e);
        }
    }
}

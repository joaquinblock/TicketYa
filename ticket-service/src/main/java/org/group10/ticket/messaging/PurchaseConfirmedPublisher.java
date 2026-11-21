package org.group10.ticket.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.group10.ticket.config.RabbitConfig;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Avisa {@code purchase.confirmed} recién cuando se confirma la transacción que dejó la orden {@code PAID}
 * (ADR 0004). Sin outbox: si el broker falla, el aviso se pierde y queda en el log, y el pago responde igual.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseConfirmedPublisher {

    static final String ROUTING_KEY = "purchase.confirmed";

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(PurchaseConfirmed purchase) {
        PurchaseConfirmedMessage message = new PurchaseConfirmedMessage(UUID.randomUUID(), purchase.occurredAt(),
                purchase.orderId(), purchase.userId(), purchase.eventId());
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EVENTS_EXCHANGE, ROUTING_KEY, message);
        } catch (AmqpException e) {
            // Spring atraparía la excepción igual, pero la loguearía sin decir de qué orden era el aviso.
            log.error("No se pudo avisar la compra de la orden {}", purchase.orderId(), e);
        }
    }
}

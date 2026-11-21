package org.group10.ticket.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PurchaseConfirmedPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(PurchaseConfirmed purchase) {
    }
}

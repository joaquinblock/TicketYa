package org.group10.recommendation.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.rabbit.listener.FatalExceptionStrategy;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mensajería con RabbitMQ (ADR 0004), igual que en notification-service.
 * <ul>
 *   <li>El exchange {@code ticketya.events} es de ticket-service, pero se declara también acá (con los mismos
 *       atributos) para que el binding no dependa de qué servicio arranque primero.</li>
 *   <li>Dos colas reciben {@code purchase.confirmed}: {@code recommendation.purchase} arma el historial y
 *       {@code recommendation.favorites} cuenta los favoritos, que necesita a event-service. Así, con event-service
 *       caído, el historial sigue al día. Lo que cada una rechaza sin requeue pasa, por el exchange por defecto, a su
 *       {@code .dlq}.</li>
 *   <li>El reintento ({@code spring.rabbitmq.listener.simple.retry.*}) no repite lo que nunca va a andar: un mensaje
 *       ilegible o uno que el listener rechaza.</li>
 * </ul>
 */
@Slf4j
@Configuration
public class RabbitConfig {

    public static final String EVENTS_EXCHANGE = "ticketya.events";
    public static final String PURCHASE_CONFIRMED = "purchase.confirmed";
    public static final String PURCHASE_QUEUE = "recommendation.purchase";
    public static final String PURCHASE_DLQ = "recommendation.purchase.dlq";
    public static final String FAVORITES_QUEUE = "recommendation.favorites";
    public static final String FAVORITES_DLQ = "recommendation.favorites.dlq";

    /** La autoconfiguración lo usa en el RabbitTemplate y en los listeners. */
    @Bean
    MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }

    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    Queue purchaseQueue() {
        return QueueBuilder.durable(PURCHASE_QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(PURCHASE_DLQ)
                .build();
    }

    @Bean
    Queue purchaseDeadLetterQueue() {
        return QueueBuilder.durable(PURCHASE_DLQ).build();
    }

    @Bean
    Binding purchaseConfirmedBinding(Queue purchaseQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(purchaseQueue).to(eventsExchange).with(PURCHASE_CONFIRMED);
    }

    @Bean
    Queue favoritesQueue() {
        return QueueBuilder.durable(FAVORITES_QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(FAVORITES_DLQ)
                .build();
    }

    @Bean
    Queue favoritesDeadLetterQueue() {
        return QueueBuilder.durable(FAVORITES_DLQ).build();
    }

    @Bean
    Binding favoritesBinding(Queue favoritesQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(favoritesQueue).to(eventsExchange).with(PURCHASE_CONFIRMED);
    }

    /**
     * Lo que Spring AMQP ya considera fatal (JSON inválido, tipo que no se puede convertir) más el rechazo explícito
     * del listener no se reintenta. La excepción llega envuelta en {@code ListenerExecutionFailedException}.
     */
    @Bean
    RabbitListenerRetrySettingsCustomizer skipRetryOnUnreadableMessages() {
        FatalExceptionStrategy fatal = new ConditionalRejectingErrorHandler.DefaultExceptionStrategy();
        return settings -> settings.setExceptionPredicate(e -> !fatal.isFatal(e) && !rejectedByListener(e));
    }

    /**
     * Al agotar los intentos (o si no se reintenta), deja el id de la orden en el log y manda el mensaje a la DLQ de
     * la cola de la que vino. Es uno solo para los dos listeners.
     */
    @Bean
    MessageRecoverer purchaseMessageRecoverer(JsonMapper jsonMapper) {
        return new RejectAndDontRequeueRecoverer() {
            @Override
            public void recover(Message message, Throwable cause) {
                JsonNode body = readBody(jsonMapper, message);
                log.error("El mensaje {} de la orden {} va a {}.dlq: {}", body.path("messageId").asString("desconocido"),
                        body.path("orderId").asString("desconocida"), message.getMessageProperties().getConsumerQueue(),
                        rootCauseType(cause));
                super.recover(message, cause);
            }
        };
    }

    private static boolean rejectedByListener(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof AmqpRejectAndDontRequeueException) {
                return true;
            }
        }
        return false;
    }

    /** Con un JSON inválido devuelve un nodo vacío: el log dice "desconocido" en vez de fallar. */
    private static JsonNode readBody(JsonMapper jsonMapper, Message message) {
        try {
            return jsonMapper.readTree(message.getBody());
        } catch (JacksonException e) {
            return jsonMapper.missingNode();
        }
    }

    private static String rootCauseType(Throwable cause) {
        Throwable root = cause;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }
}

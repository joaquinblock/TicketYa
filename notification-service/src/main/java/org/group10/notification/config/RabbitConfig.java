package org.group10.notification.config;

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
 * Mensajería con RabbitMQ (ADR 0004).
 * <ul>
 *   <li>El exchange {@code ticketya.events} lo declaran ticket-service y event-service, y también acá (con los mismos
 *       atributos) para que el binding no dependa de qué servicio arranque primero.</li>
 *   <li>{@code notification.purchase} recibe {@code purchase.confirmed}, y {@code notification.event} los cambios de
 *       un evento que publica event-service ({@code event.*}). Lo que se rechaza sin requeue pasa, por el exchange
 *       por defecto, a la DLQ de cada cola ({@code <cola>.dlq}).</li>
 *   <li>El reintento ({@code spring.rabbitmq.listener.simple.retry.*}) no repite lo que nunca va a andar: un mensaje
 *       ilegible o uno que el listener rechaza.</li>
 * </ul>
 */
@Slf4j
@Configuration
public class RabbitConfig {

    public static final String EVENTS_EXCHANGE = "ticketya.events";
    public static final String PURCHASE_CONFIRMED = "purchase.confirmed";
    public static final String PURCHASE_QUEUE = "notification.purchase";
    public static final String PURCHASE_DLQ = PURCHASE_QUEUE + ".dlq";
    public static final String EVENT_CHANGES = "event.*";
    public static final String EVENT_QUEUE = "notification.event";
    public static final String EVENT_DLQ = EVENT_QUEUE + ".dlq";

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
    Queue eventQueue() {
        return QueueBuilder.durable(EVENT_QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(EVENT_DLQ)
                .build();
    }

    @Bean
    Queue eventDeadLetterQueue() {
        return QueueBuilder.durable(EVENT_DLQ).build();
    }

    @Bean
    Binding eventChangesBinding(Queue eventQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(eventQueue).to(eventsExchange).with(EVENT_CHANGES);
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
     * Al agotar los intentos (o si no se reintenta), deja en el log el id de la orden o del evento, el que traiga el
     * mensaje, y lo manda a la DLQ de su cola. Es uno solo para todos los listeners: la autoconfiguración usa este bean.
     */
    @Bean
    MessageRecoverer deadLetterMessageRecoverer(JsonMapper jsonMapper) {
        return new RejectAndDontRequeueRecoverer() {
            @Override
            public void recover(Message message, Throwable cause) {
                JsonNode body = readBody(jsonMapper, message);
                log.error("El mensaje {} {} va a {}: {}", body.path("messageId").asString("desconocido"),
                        subjectOf(body), deadLetterQueueOf(message), rootCauseType(cause));
                super.recover(message, cause);
            }
        };
    }

    /** Una compra también trae {@code eventId}: el {@code orderId} va primero. */
    private static String subjectOf(JsonNode body) {
        if (body.hasNonNull("orderId")) {
            return "de la orden " + body.path("orderId").asString();
        }
        if (body.hasNonNull("eventId")) {
            return "del evento " + body.path("eventId").asString();
        }
        return "sin orden ni evento";
    }

    private static String deadLetterQueueOf(Message message) {
        String queue = message.getMessageProperties().getConsumerQueue();
        return queue == null ? "la DLQ" : queue + ".dlq";
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

    /** Solo la clase: el mensaje puede traer la respuesta del SMTP, con el email del comprador. */
    private static String rootCauseType(Throwable cause) {
        Throwable root = cause;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }
}

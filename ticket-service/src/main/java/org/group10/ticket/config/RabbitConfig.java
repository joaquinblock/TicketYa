package org.group10.ticket.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mensajería con RabbitMQ (ADR 0004). ticket-service es dueño del exchange de avisos.
 */
@Slf4j
@Configuration
public class RabbitConfig {

    public static final String EVENTS_EXCHANGE = "ticketya.events";

    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    /** La autoconfiguración lo usa en el RabbitTemplate y en los listeners. */
    @Bean
    MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }

    /**
     * Spring AMQP declara el exchange recién al abrir la primera conexión. Se intenta al arrancar para que
     * exista antes del primer aviso; sin broker no se frena el arranque y se declara cuando conecte.
     */
    @EventListener(ContextRefreshedEvent.class)
    void declareOnStartup(ContextRefreshedEvent event) {
        try {
            event.getApplicationContext().getBean(AmqpAdmin.class).initialize();
        } catch (AmqpException e) {
            log.warn("RabbitMQ no responde: el exchange {} se declara al conectar ({})", EVENTS_EXCHANGE, e.getMessage());
        }
    }
}

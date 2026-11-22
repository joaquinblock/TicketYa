package org.group10.notification.config;

import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mensajería con RabbitMQ (ADR 0004). El exchange lo declara ticket-service, que es quien publica.
 */
@Configuration
public class RabbitConfig {

    /** La autoconfiguración lo usa en el RabbitTemplate y en los listeners. */
    @Bean
    MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }
}

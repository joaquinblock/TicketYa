package org.group10.notification.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, RabbitAutoConfiguration.class))
            .withUserConfiguration(RabbitConfig.class);

    record PurchaseSample(String orderId, int tickets) {
    }

    @Test
    void messagesAreConvertedToJson() {
        contextRunner.run(context -> {
            RabbitTemplate template = context.getBean(RabbitTemplate.class);

            Message message = template.getMessageConverter()
                    .toMessage(new PurchaseSample("o-1", 2), new MessageProperties());

            assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
            assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                    .isEqualTo("{\"orderId\":\"o-1\",\"tickets\":2}");
        });
    }
}

package org.group10.ticket.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.slf4j.MDC;
import org.group10.common.tracing.CorrelationHeaders;

import java.net.ConnectException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class PurchaseConfirmedPublisherTest {

    private static final Instant PAID_AT = Instant.parse("2026-11-21T18:00:00Z");

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private PurchaseConfirmedPublisher publisher;

    @Test
    void publish_confirmedPurchase_sendsItsIdsToTheEventsExchange() {
        publisher.publish(new PurchaseConfirmed(42L, 7L, 3L, PAID_AT));

        ArgumentCaptor<PurchaseConfirmedMessage> message = ArgumentCaptor.forClass(PurchaseConfirmedMessage.class);
        verify(rabbitTemplate).convertAndSend(eq("ticketya.events"), eq("purchase.confirmed"), message.capture(),
                any(MessagePostProcessor.class));
        assertThat(message.getValue().orderId()).isEqualTo(42L);
        assertThat(message.getValue().userId()).isEqualTo(7L);
        assertThat(message.getValue().eventId()).isEqualTo(3L);
        assertThat(message.getValue().occurredAt()).isEqualTo(PAID_AT);
        assertThat(message.getValue().messageId()).isNotNull();
    }

    @Test
    void publish_confirmedPurchase_propagatesCorrelationIdInRabbitHeader() {
        MDC.put(CorrelationHeaders.MDC_KEY, "operation-123");
        ArgumentCaptor<MessagePostProcessor> processor = ArgumentCaptor.forClass(MessagePostProcessor.class);

        publisher.publish(new PurchaseConfirmed(42L, 7L, 3L, PAID_AT));

        verify(rabbitTemplate).convertAndSend(eq("ticketya.events"), eq("purchase.confirmed"),
                any(Object.class), processor.capture());
        Message processed = processor.getValue().postProcessMessage(
                new Message(new byte[0], new MessageProperties()));
        assertThat((Object) processed.getMessageProperties().getHeader(CorrelationHeaders.HEADER))
                .isEqualTo("operation-123");
        MDC.clear();
    }

    @Test
    void publish_twoPurchases_haveDistinctMessageIds() {
        publisher.publish(new PurchaseConfirmed(42L, 7L, 3L, PAID_AT));
        publisher.publish(new PurchaseConfirmed(43L, 7L, 3L, PAID_AT));

        ArgumentCaptor<PurchaseConfirmedMessage> messages = ArgumentCaptor.forClass(PurchaseConfirmedMessage.class);
        verify(rabbitTemplate, times(2)).convertAndSend(anyString(), anyString(), messages.capture(),
                any(MessagePostProcessor.class));
        assertThat(messages.getAllValues()).extracting(PurchaseConfirmedMessage::messageId).doesNotHaveDuplicates();
    }

    @Test
    void publish_brokerDown_doesNotFailAndLogsTheOrder(CapturedOutput output) {
        doThrow(new AmqpConnectException(new ConnectException("Connection refused")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class),
                        any(MessagePostProcessor.class));

        assertThatNoException().isThrownBy(() -> publisher.publish(new PurchaseConfirmed(42L, 7L, 3L, PAID_AT)));
        assertThat(output).contains("No se pudo avisar la compra de la orden 42");
    }
}

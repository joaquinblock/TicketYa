package org.group10.event.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

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
class EventChangedPublisherTest {

    private static final Instant CHANGED_AT = Instant.parse("2026-11-22T18:00:00Z");

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private EventChangedPublisher publisher;

    @ParameterizedTest
    @CsvSource({"CANCELLED, event.cancelled", "SUSPENDED, event.suspended", "RESCHEDULED, event.rescheduled"})
    void publish_change_sendsTheEventIdAndChangeWithItsRoutingKey(EventChange change, String routingKey) {
        publisher.publish(new EventChanged(3L, change, CHANGED_AT));

        ArgumentCaptor<EventChangedMessage> message = ArgumentCaptor.forClass(EventChangedMessage.class);
        verify(rabbitTemplate).convertAndSend(eq("ticketya.events"), eq(routingKey), message.capture());
        assertThat(message.getValue().eventId()).isEqualTo(3L);
        assertThat(message.getValue().change()).isEqualTo(change);
        assertThat(message.getValue().occurredAt()).isEqualTo(CHANGED_AT);
        assertThat(message.getValue().messageId()).isNotNull();
    }

    @Test
    void publish_twoChanges_haveDistinctMessageIds() {
        publisher.publish(new EventChanged(3L, EventChange.SUSPENDED, CHANGED_AT));
        publisher.publish(new EventChanged(3L, EventChange.RESCHEDULED, CHANGED_AT));

        ArgumentCaptor<EventChangedMessage> messages = ArgumentCaptor.forClass(EventChangedMessage.class);
        verify(rabbitTemplate, times(2)).convertAndSend(anyString(), anyString(), messages.capture());
        assertThat(messages.getAllValues()).extracting(EventChangedMessage::messageId).doesNotHaveDuplicates();
    }

    @Test
    void publish_brokerDown_doesNotFailAndLogsTheEvent(CapturedOutput output) {
        doThrow(new AmqpConnectException(new ConnectException("Connection refused")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        assertThatNoException().isThrownBy(() ->
                publisher.publish(new EventChanged(3L, EventChange.CANCELLED, CHANGED_AT)));
        assertThat(output).contains("WARN").contains("No se pudo avisar el cambio CANCELLED del evento 3");
    }
}

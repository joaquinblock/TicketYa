package org.group10.notification.messaging;

import org.group10.notification.client.AuthClient;
import org.group10.notification.client.EventClient;
import org.group10.notification.client.TicketClient;
import org.group10.notification.dto.EventDetail;
import org.group10.notification.dto.UserContact;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Topología, conversión y DLQ de {@code notification.event} contra el RabbitMQ de {@code rabbitmq.compose.yaml}
 * (US-NOT-02). Si el broker no está arriba, lo levanta. No correrlo con notification-service levantado: los dos
 * consumirían de la misma cola.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
@ExtendWith(OutputCaptureExtension.class)
class EventChangedRabbitIntegrationTest {

    private static final String EXCHANGE = "ticketya.events";
    private static final String QUEUE = "notification.event";
    private static final String DLQ = "notification.event.dlq";
    private static final long EVENT_ID = 3L;
    private static final long USER_ID = 7L;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @MockitoBean
    private EventClient eventClient;

    @MockitoBean
    private TicketClient ticketClient;

    @MockitoBean
    private AuthClient authClient;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeAll
    static void startBroker() throws Exception {
        Process process = new ProcessBuilder("docker", "compose", "-f", "../rabbitmq.compose.yaml", "up", "-d", "--wait")
                .directory(new File(System.getProperty("user.dir")))
                .inheritIO()
                .start();
        assertThat(process.waitFor()).as("docker compose up de rabbitmq.compose.yaml").isZero();
    }

    @BeforeEach
    void setUp() {
        amqpAdmin.purgeQueue(QUEUE, false);
        amqpAdmin.purgeQueue(DLQ, false);
        when(eventClient.findEvent(EVENT_ID)).thenReturn(new EventDetail(EVENT_ID, "Los Serranos en vivo",
                LocalDateTime.of(2026, 12, 13, 21, 0),
                new EventDetail.Venue("Estadio del Sur", "Av. del Estadio 1000", "Buenos Aires")));
        when(ticketClient.findBuyers(EVENT_ID)).thenReturn(List.of(USER_ID));
        when(authClient.findContact(USER_ID)).thenReturn(new UserContact(USER_ID, "client@ticketya.com", "Juan", "Pérez"));
    }

    @Test
    void cancellationFromEventServiceReachesTheListenerDespiteItsTypeHeader() {
        publish("event.cancelled", validJson(UUID.randomUUID(), "CANCELLED"));

        verify(mailSender, timeout(10_000)).send(any(SimpleMailMessage.class));
    }

    @Test
    void invalidJsonGoesToTheDlqWithoutRetries() {
        long start = System.nanoTime();

        publish("event.cancelled", "{ esto no es json");

        Message dead = rabbitTemplate.receive(DLQ, 15_000);
        assertThat(dead).isNotNull();
        // Con reintentos tardaría al menos 3 s (1 s + 2 s de espera)
        assertThat(elapsedMillis(start)).isLessThan(2_500);
        verify(eventClient, never()).findEvent(anyLong());
    }

    @Test
    void unknownChangeGoesToTheDlqWithoutRetries() {
        long start = System.nanoTime();

        publish("event.moved", validJson(UUID.randomUUID(), "MOVED"));

        Message dead = rabbitTemplate.receive(DLQ, 15_000);
        assertThat(dead).isNotNull();
        assertThat(elapsedMillis(start)).isLessThan(2_500);
        verify(eventClient, never()).findEvent(anyLong());
    }

    @Test
    void failingEveryTimeIsAttemptedThreeTimesAndLogsTheEvent(CapturedOutput output) {
        when(ticketClient.findBuyers(EVENT_ID)).thenThrow(new ResourceAccessException("Read timed out"));
        UUID messageId = UUID.randomUUID();

        publish("event.suspended", validJson(messageId, "SUSPENDED"));

        Message dead = rabbitTemplate.receive(DLQ, 15_000);
        assertThat(dead).isNotNull();
        verify(ticketClient, times(3)).findBuyers(EVENT_ID);
        assertThat(output.getOut().lines().filter(line -> line.contains("va a " + DLQ)).toList())
                .singleElement().satisfies(line -> assertThat(line)
                        .contains("ERROR", messageId.toString(), "evento " + EVENT_ID));
    }

    /** Publica como lo hace event-service: JSON con el {@code __TypeId__} de su propia clase. */
    private void publish(String routingKey, String json) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setHeader("__TypeId__", "org.group10.event.messaging.EventChangedMessage");
        Message message = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                .andProperties(properties)
                .build();
        rabbitTemplate.send(EXCHANGE, routingKey, message);
    }

    private static String validJson(UUID messageId, String change) {
        return "{\"messageId\":\"" + messageId + "\",\"occurredAt\":\"2026-11-22T18:00:00Z\","
                + "\"eventId\":" + EVENT_ID + ",\"change\":\"" + change + "\"}";
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}

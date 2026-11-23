package org.group10.notification.messaging;

import org.group10.notification.client.AuthClient;
import org.group10.notification.client.EventClient;
import org.group10.notification.client.RecommendationClient;
import org.group10.notification.client.TicketClient;
import org.group10.notification.dto.EventDetail;
import org.group10.notification.dto.UserContact;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
 * Topología y DLQ de {@code notification.favorites} contra el RabbitMQ de {@code rabbitmq.compose.yaml} (US-NOT-03).
 * La publicación también llega a {@code notification.event}, que la descarta.
 * Si el broker no está arriba, lo levanta. No correrlo con notification-service levantado: los dos consumirían de la
 * misma cola.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
class EventPublishedRabbitIntegrationTest {

    private static final String EXCHANGE = "ticketya.events";
    private static final String QUEUE = "notification.favorites";
    private static final String DLQ = "notification.favorites.dlq";
    private static final String EVENT_QUEUE = "notification.event";
    private static final String EVENT_DLQ = "notification.event.dlq";
    private static final long EVENT_ID = 3L;
    private static final long USER_ID = 7L;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @MockitoBean
    private EventClient eventClient;

    @MockitoBean
    private RecommendationClient recommendationClient;

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
        amqpAdmin.purgeQueue(EVENT_QUEUE, false);
        amqpAdmin.purgeQueue(EVENT_DLQ, false);
        when(eventClient.findEvent(EVENT_ID)).thenReturn(new EventDetail(EVENT_ID, "Los Serranos en vivo",
                LocalDateTime.of(2026, 12, 13, 21, 0),
                new EventDetail.Venue("Teatro de la Sierra", "Calle Pinto 650", "Tandil"), "PUBLISHED",
                List.of(new EventDetail.Participant(1L, "Los Serranos"))));
        when(recommendationClient.findFans(List.of(1L), "Tandil")).thenReturn(List.of(USER_ID));
        when(authClient.findContact(USER_ID)).thenReturn(new UserContact(USER_ID, "fan@ticketya.com", "Juan", "Pérez"));
    }

    @Test
    void publicationFromEventServiceReachesTheFansDespiteItsTypeHeader() {
        publish("event.published", "{\"messageId\":\"" + UUID.randomUUID() + "\",\"occurredAt\":\"2026-11-23T18:00:00Z\","
                + "\"eventId\":" + EVENT_ID + ",\"change\":\"PUBLISHED\"}");

        verify(mailSender, timeout(10_000)).send(any(SimpleMailMessage.class));
    }

    @Test
    void publicationIsDiscardedByTheEventChangesQueueWithoutMailingTheBuyers() {
        publish("event.published", "{\"messageId\":\"" + UUID.randomUUID() + "\",\"occurredAt\":\"2026-11-23T18:00:00Z\","
                + "\"eventId\":" + EVENT_ID + ",\"change\":\"PUBLISHED\"}");

        verify(mailSender, timeout(10_000)).send(any(SimpleMailMessage.class));
        assertThat(rabbitTemplate.receive(EVENT_DLQ, 3_000)).isNull();
        assertThat(amqpAdmin.getQueueInfo(EVENT_QUEUE).getMessageCount()).isZero();
        verify(ticketClient, never()).findBuyers(anyLong());
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void messageWithoutEventIdGoesToTheDlqWithoutAskingEventService() {
        publish("event.published", "{\"messageId\":\"" + UUID.randomUUID() + "\",\"occurredAt\":\"2026-11-23T18:00:00Z\","
                + "\"change\":\"PUBLISHED\"}");

        Message dead = rabbitTemplate.receive(DLQ, 15_000);
        assertThat(dead).isNotNull();
        verify(eventClient, never()).findEvent(anyLong());
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
}

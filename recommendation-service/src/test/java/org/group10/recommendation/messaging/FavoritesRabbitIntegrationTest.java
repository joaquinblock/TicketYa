package org.group10.recommendation.messaging;

import org.group10.recommendation.client.EventClient;
import org.group10.recommendation.dto.EventDetail;
import org.group10.recommendation.dto.EventSummary;
import org.group10.recommendation.entity.PurchaseHistory;
import org.group10.recommendation.repository.PurchaseHistoryRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code purchase.confirmed} también llega a {@code recommendation.favorites}, que pide el evento a event-service y
 * cuenta sus participantes, lugar y organización. Contra el RabbitMQ de {@code rabbitmq.compose.yaml} y el MongoDB
 * de {@code compose.yaml}. No correrlo con el servicio prendido: se llevaría los mensajes de la cola.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
class FavoritesRabbitIntegrationTest {

    private static final String EXCHANGE = "ticketya.events";
    private static final String ROUTING_KEY = "purchase.confirmed";
    private static final String QUEUE = "recommendation.favorites";
    private static final String DLQ = "recommendation.favorites.dlq";
    private static final long USER_ID = 7L;
    private static final long EVENT_ID = 3L;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private PurchaseHistoryRepository purchaseHistoryRepository;

    @MockitoBean
    private EventClient eventClient;

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
        purchaseHistoryRepository.deleteById(USER_ID);
    }

    @Test
    void purchaseCountsTheEventsParticipantsVenueAndOrganization() {
        when(eventClient.findEvent(EVENT_ID)).thenReturn(event());

        publish(validJson(UUID.randomUUID()));

        PurchaseHistory.FavoriteCounts counts = awaitCounts();
        assertThat(counts.getParticipants()).containsExactlyInAnyOrderEntriesOf(Map.of("10", 1, "11", 1));
        assertThat(counts.getVenues()).containsExactlyEntriesOf(Map.of("4", 1));
        assertThat(counts.getOrganizations()).containsExactlyEntriesOf(Map.of("1", 1));
    }

    @Test
    void eventServiceDownEveryTimeIsAttemptedThreeTimesAndEndsInTheDlq() {
        when(eventClient.findEvent(EVENT_ID)).thenThrow(new ResourceAccessException("Connection refused"));

        publish(validJson(UUID.randomUUID()));

        // Espera de 1 s y 2 s entre intentos
        Message dead = rabbitTemplate.receive(DLQ, 15_000);
        assertThat(dead).isNotNull();
        verify(eventClient, times(3)).findEvent(EVENT_ID);
    }

    @Test
    void eventThatIsNotPublicGoesToTheDlqWithoutRetries() {
        when(eventClient.findEvent(EVENT_ID)).thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND,
                "Not Found", null, null, null));
        long start = System.nanoTime();

        publish(validJson(UUID.randomUUID()));

        Message dead = rabbitTemplate.receive(DLQ, 15_000);
        assertThat(dead).isNotNull();
        assertThat(elapsedMillis(start)).isLessThan(2_500);
        verify(eventClient, times(1)).findEvent(EVENT_ID);
    }

    @Test
    void messageWithoutEventIdGoesToTheDlqWithoutAskingEventService() {
        publish("{\"messageId\":\"" + UUID.randomUUID() + "\",\"occurredAt\":\"2026-11-21T18:00:00Z\","
                + "\"orderId\":42,\"userId\":" + USER_ID + "}");

        Message dead = rabbitTemplate.receive(DLQ, 15_000);
        assertThat(dead).isNotNull();
        verify(eventClient, never()).findEvent(anyLong());
    }

    /** Espera a que el listener escriba los contadores, hasta 10 s. */
    private PurchaseHistory.FavoriteCounts awaitCounts() {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            PurchaseHistory.FavoriteCounts counts = purchaseHistoryRepository.findById(USER_ID)
                    .map(PurchaseHistory::getCounts)
                    .orElse(null);
            if (counts != null) {
                return counts;
            }
            LockSupport.parkNanos(100_000_000L);
        }
        throw new AssertionError("El listener no escribió los contadores del cliente " + USER_ID);
    }

    /** Publica como lo hace ticket-service: JSON con el {@code __TypeId__} de su propia clase. */
    private void publish(String json) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setHeader("__TypeId__", "org.group10.ticket.messaging.PurchaseConfirmedMessage");
        Message message = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                .andProperties(properties)
                .build();
        rabbitTemplate.send(EXCHANGE, ROUTING_KEY, message);
    }

    private static String validJson(UUID messageId) {
        return "{\"messageId\":\"" + messageId + "\",\"occurredAt\":\"2026-11-21T18:00:00Z\","
                + "\"orderId\":42,\"userId\":" + USER_ID + ",\"eventId\":" + EVENT_ID + "}";
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static EventDetail event() {
        return new EventDetail(EVENT_ID, "Los Serranos en vivo", "CONCERT", 1L,
                new EventSummary.Venue(4L, "Estadio del Sur", "Av. del Estadio 1000", "Buenos Aires", null, null),
                LocalDateTime.of(2026, 12, 13, 21, 0), "PUBLISHED", false, List.of(),
                List.of(new EventDetail.Participant(10L, "Los Serranos"), new EventDetail.Participant(11L, "DJ Marea")));
    }
}

package org.group10.recommendation.messaging;

import org.group10.recommendation.entity.PurchaseHistory;
import org.group10.recommendation.repository.PurchaseHistoryRepository;
import org.group10.recommendation.service.PurchaseHistoryService;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * {@code purchase.confirmed} llega a {@code recommendation.purchase} y queda en {@code purchase_history}, contra el
 * RabbitMQ de {@code rabbitmq.compose.yaml} y el MongoDB de {@code compose.yaml}. Si el broker no está arriba, lo
 * levanta. No correrlo con el servicio prendido: se llevaría los mensajes de la cola.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
class PurchaseConfirmedRabbitIntegrationTest {

    private static final String EXCHANGE = "ticketya.events";
    private static final String ROUTING_KEY = "purchase.confirmed";
    private static final String QUEUE = "recommendation.purchase";
    private static final String DLQ = "recommendation.purchase.dlq";
    private static final long USER_ID = 7L;
    private static final long EVENT_ID = 3L;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private PurchaseHistoryRepository purchaseHistoryRepository;

    @MockitoSpyBean
    private PurchaseHistoryService purchaseHistoryService;

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
    void purchaseFromTicketServiceIsAddedToTheBuyersHistory() {
        publish(validJson(UUID.randomUUID()));

        verify(purchaseHistoryService, timeout(10_000)).record(USER_ID, EVENT_ID);
        assertThat(purchaseHistoryRepository.findById(USER_ID))
                .map(PurchaseHistory::getEventIds)
                .hasValueSatisfying(eventIds -> assertThat(eventIds).containsExactly(EVENT_ID));
    }

    @Test
    void repeatedMessageLeavesTheEventOnce() {
        String json = validJson(UUID.randomUUID());

        publish(json);
        publish(json);

        verify(purchaseHistoryService, timeout(10_000).times(2)).record(USER_ID, EVENT_ID);
        assertThat(purchaseHistoryRepository.findById(USER_ID))
                .map(PurchaseHistory::getEventIds)
                .hasValueSatisfying(eventIds -> assertThat(eventIds).containsExactly(EVENT_ID));
    }

    @Test
    void invalidJsonGoesToTheDlqWithoutRetries() {
        long start = System.nanoTime();

        publish("{ esto no es json");

        Message dead = rabbitTemplate.receive(DLQ, 15_000);
        assertThat(dead).isNotNull();
        // Con reintentos tardaría al menos 3 s (1 s + 2 s de espera)
        assertThat(elapsedMillis(start)).isLessThan(2_500);
        verify(purchaseHistoryService, never()).record(anyLong(), anyLong());
    }

    @Test
    void messageWithoutEventIdGoesToTheDlqWithoutRetries() {
        long start = System.nanoTime();

        publish("{\"messageId\":\"" + UUID.randomUUID() + "\",\"occurredAt\":\"2026-11-21T18:00:00Z\","
                + "\"orderId\":42,\"userId\":" + USER_ID + "}");

        Message dead = rabbitTemplate.receive(DLQ, 15_000);
        assertThat(dead).isNotNull();
        assertThat(elapsedMillis(start)).isLessThan(2_500);
        verify(purchaseHistoryService, never()).record(anyLong(), anyLong());
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
}

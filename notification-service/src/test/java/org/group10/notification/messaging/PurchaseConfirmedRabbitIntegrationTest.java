package org.group10.notification.messaging;

import org.group10.notification.client.AuthClient;
import org.group10.notification.client.TicketClient;
import org.group10.notification.dto.PaidOrder;
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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;

import java.io.File;
import java.math.BigDecimal;
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
 * Topología, conversión y reintento de {@code notification.purchase} contra el RabbitMQ de
 * {@code rabbitmq.compose.yaml} (US-NOT-01). Si el broker no está arriba, lo levanta.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
class PurchaseConfirmedRabbitIntegrationTest {

    private static final String EXCHANGE = "ticketya.events";
    private static final String ROUTING_KEY = "purchase.confirmed";
    private static final String QUEUE = "notification.purchase";
    private static final String DLQ = "notification.purchase.dlq";
    private static final long ORDER_ID = 42L;
    private static final long USER_ID = 7L;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

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
        when(authClient.findContact(USER_ID)).thenReturn(new UserContact(USER_ID, "client@ticketya.com", "Juan", "Pérez"));
    }

    @Test
    void messageFromTicketServiceReachesTheListenerDespiteItsTypeHeader() {
        when(ticketClient.findPaidOrder(ORDER_ID)).thenReturn(paidOrder());

        publish(validJson(UUID.randomUUID()));

        verify(mailSender, timeout(10_000)).send(any(MimeMessagePreparator.class));
    }

    @Test
    void failingEveryTimeIsAttemptedThreeTimesAndEndsInTheDlq() {
        when(ticketClient.findPaidOrder(ORDER_ID)).thenThrow(new ResourceAccessException("Read timed out"));

        publish(validJson(UUID.randomUUID()));

        // Espera de 1 s y 2 s entre intentos
        Message dead = rabbitTemplate.receive(DLQ, 15_000);
        assertThat(dead).isNotNull();
        verify(ticketClient, times(3)).findPaidOrder(ORDER_ID);
        verify(mailSender, never()).send(any(MimeMessagePreparator.class));
    }

    @Test
    void failingOnceAndThenWorkingSendsTheMailAndLeavesTheDlqEmpty() {
        when(ticketClient.findPaidOrder(ORDER_ID))
                .thenThrow(new ResourceAccessException("Read timed out"))
                .thenReturn(paidOrder());

        publish(validJson(UUID.randomUUID()));

        verify(mailSender, timeout(10_000)).send(any(MimeMessagePreparator.class));
        verify(ticketClient, times(2)).findPaidOrder(ORDER_ID);
        assertThat(rabbitTemplate.receive(DLQ)).isNull();
    }

    @Test
    void invalidJsonGoesToTheDlqWithoutRetries() {
        long start = System.nanoTime();

        publish("{ esto no es json");

        Message dead = rabbitTemplate.receive(DLQ, 15_000);
        assertThat(dead).isNotNull();
        // Con reintentos tardaría al menos 3 s (1 s + 2 s de espera)
        assertThat(elapsedMillis(start)).isLessThan(2_500);
        verify(ticketClient, never()).findPaidOrder(anyLong());
    }

    @Test
    void messageWithoutOrderIdGoesToTheDlqWithoutRetries() {
        long start = System.nanoTime();

        publish("{\"messageId\":\"" + UUID.randomUUID() + "\",\"occurredAt\":\"2026-11-21T18:00:00Z\","
                + "\"userId\":7,\"eventId\":3}");

        Message dead = rabbitTemplate.receive(DLQ, 15_000);
        assertThat(dead).isNotNull();
        assertThat(elapsedMillis(start)).isLessThan(2_500);
        verify(ticketClient, never()).findPaidOrder(anyLong());
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
                + "\"orderId\":" + ORDER_ID + ",\"userId\":" + USER_ID + ",\"eventId\":3}";
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static PaidOrder paidOrder() {
        PaidOrder.Event event = new PaidOrder.Event("Los Serranos en vivo", LocalDateTime.of(2026, 12, 13, 21, 0),
                "Estadio del Sur", "Av. del Estadio 1000", "Buenos Aires");
        return new PaidOrder(ORDER_ID, event, new BigDecimal("140000.00"), List.of(
                new PaidOrder.Ticket(1L, "Platea", "A", 15, new BigDecimal("70000.00"), "qr-uno")));
    }
}

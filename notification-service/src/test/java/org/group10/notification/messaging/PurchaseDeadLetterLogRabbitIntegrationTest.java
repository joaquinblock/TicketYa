package org.group10.notification.messaging;

import org.group10.notification.client.AuthClient;
import org.group10.notification.client.TicketClient;
import org.group10.notification.dto.PaidOrder;
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
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * El {@code ERROR} que queda al mandar un mensaje a la DLQ (US-NOT-01, CA3): lleva el {@code messageId} y el id de
 * la orden, y nunca el email del comprador, aunque venga en la respuesta del SMTP.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
@ExtendWith(OutputCaptureExtension.class)
class PurchaseDeadLetterLogRabbitIntegrationTest {

    private static final String EXCHANGE = "ticketya.events";
    private static final String ROUTING_KEY = "purchase.confirmed";
    private static final String QUEUE = "notification.purchase";
    private static final String DLQ = "notification.purchase.dlq";
    private static final long ORDER_ID = 42L;
    private static final long USER_ID = 7L;
    private static final String EMAIL = "client@ticketya.com";

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
        when(authClient.findContact(USER_ID)).thenReturn(new UserContact(USER_ID, EMAIL, "Juan", "Pérez"));
        when(ticketClient.findPaidOrder(ORDER_ID)).thenReturn(paidOrder());
    }

    @Test
    void exhaustedMessageLogsItsIdsWithoutTheBuyerEmail(CapturedOutput output) {
        doThrow(new MailSendException("550 5.1.1 <" + EMAIL + ">: Recipient address rejected"))
                .when(mailSender).send(any(MimeMessagePreparator.class));
        UUID messageId = UUID.randomUUID();

        publish(messageId);

        Message dead = rabbitTemplate.receive(DLQ, 15_000);
        assertThat(dead).isNotNull();
        List<String> deadLetterLines = output.getOut().lines()
                .filter(line -> line.contains("va a " + DLQ))
                .toList();
        assertThat(deadLetterLines).singleElement().satisfies(line -> assertThat(line)
                .contains("ERROR", messageId.toString(), "orden " + ORDER_ID)
                .doesNotContain(EMAIL));
    }

    /** Publica como lo hace ticket-service: JSON con el {@code __TypeId__} de su propia clase. */
    private void publish(UUID messageId) {
        String json = "{\"messageId\":\"" + messageId + "\",\"occurredAt\":\"2026-11-21T18:00:00Z\","
                + "\"orderId\":" + ORDER_ID + ",\"userId\":" + USER_ID + ",\"eventId\":3}";
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setHeader("__TypeId__", "org.group10.ticket.messaging.PurchaseConfirmedMessage");
        Message message = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                .andProperties(properties)
                .build();
        rabbitTemplate.send(EXCHANGE, ROUTING_KEY, message);
    }

    private static PaidOrder paidOrder() {
        PaidOrder.Event event = new PaidOrder.Event("Los Serranos en vivo", LocalDateTime.of(2026, 12, 13, 21, 0),
                "Estadio del Sur", "Av. del Estadio 1000", "Buenos Aires");
        return new PaidOrder(ORDER_ID, event, new BigDecimal("140000.00"), List.of(
                new PaidOrder.Ticket(1L, "Platea", "A", 15, new BigDecimal("70000.00"), "qr-uno")));
    }
}

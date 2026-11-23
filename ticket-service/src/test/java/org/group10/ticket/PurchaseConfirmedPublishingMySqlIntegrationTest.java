package org.group10.ticket;

import org.group10.common.dto.ErrorDto;
import org.group10.ticket.client.EventClient;
import org.group10.ticket.client.PaymentClient;
import org.group10.ticket.entity.OrderStatus;
import org.group10.ticket.messaging.PurchaseConfirmedMessage;
import org.group10.ticket.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "ticketya.reservation.expiration-check=PT1H")
@ActiveProfiles("test")
class PurchaseConfirmedPublishingMySqlIntegrationTest {

    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis() + 2_000_000);
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
    };
    private static final int THREADS = 8;

    @Value("${local.server.port}")
    private int port;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;
    @MockitoBean
    private PaymentClient paymentClient;
    @MockitoBean
    private EventClient eventClient;
    @MockitoSpyBean
    private Clock clock;

    @Autowired
    private OrderRepository orderRepository;

    private RestClient client;
    private long eventId;
    private long customer;
    private long generalSector;

    private record HttpResult(int status, Map<String, Object> body) {

        long orderId() {
            return ((Number) body.get("id")).longValue();
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tickets() {
            return (List<Map<String, Object>>) body.get("tickets");
        }
    }

    @BeforeEach
    void publishEvent() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        eventId = IDS.incrementAndGet();
        customer = IDS.incrementAndGet();
        generalSector = IDS.incrementAndGet();
        int status = client.put().uri("/internal/event-sales/{eventId}", eventId).header("X-Internal-Secret", "dev-internal-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"status": "PUBLISHED", "eventName": "Recital", "venueName": "Estadio",
                         "venueAddress": "Calle 1", "venueCity": "Tandil", "dateTime": "2099-12-12T21:00:00",
                         "highDemand": false,
                         "sectors": [{"eventSectorId": %d, "sectorName": "Campo", "price": 500, "capacity": 20}]}"""
                        .formatted(generalSector))
                .exchange((request, response) -> response.getStatusCode().value());
        assertThat(status).isEqualTo(204);
    }

    @Test
    void pay_approved_publishesOnePurchaseConfirmedWithTheIdsOfTheOrder() {
        long orderId = reservePlaces(2);

        assertThat(pay(orderId, newKey()).status()).isEqualTo(200);

        PurchaseConfirmedMessage message = theOnlyPublishedMessage();
        assertThat(message.orderId()).isEqualTo(orderId);
        assertThat(message.userId()).isEqualTo(customer);
        assertThat(message.eventId()).isEqualTo(eventId);
        assertThat(message.messageId()).isNotNull();
        assertThat(message.occurredAt()).isNotNull();
    }

    @Test
    void pay_rejected_publishesNothing() {
        long orderId = reservePlaces(1);
        doThrow(rejection()).when(paymentClient).charge(any());

        assertThat(pay(orderId, newKey()).status()).isEqualTo(422);

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class),
                any(MessagePostProcessor.class));
    }

    @Test
    void pay_reservationExpiresWhileCharging_refundsAndPublishesNothing() {
        long orderId = reservePlaces(1);
        doAnswer(invocation -> {
            doReturn(Instant.now().plus(Duration.ofMinutes(11))).when(clock).instant();
            return null;
        }).when(paymentClient).charge(any());

        assertThat(pay(orderId, newKey()).status()).isEqualTo(409);

        verify(paymentClient).refund(any());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class),
                any(MessagePostProcessor.class));
    }

    @Test
    void pay_brokerDown_stillAnswersPaidWithIssuedTickets() {
        long orderId = reservePlaces(2);
        doThrow(new AmqpConnectException(new ConnectException("Connection refused")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class),
                        any(MessagePostProcessor.class));

        HttpResult paid = pay(orderId, newKey());

        assertThat(paid.status()).isEqualTo(200);
        assertThat(paid.body().get("status")).isEqualTo("PAID");
        assertThat(paid.tickets()).allSatisfy(ticket -> assertThat(ticket.get("status")).isEqualTo("ISSUED"));
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void pay_twice_publishesASingleMessage() {
        long orderId = reservePlaces(1);
        pay(orderId, newKey());

        assertThat(pay(orderId, newKey()).status()).isEqualTo(200);

        assertThat(theOnlyPublishedMessage().orderId()).isEqualTo(orderId);
    }

    @Test
    void pay_sameAttemptAtTheSameTime_publishesASingleMessage() throws Exception {
        long orderId = reservePlaces(1);
        String key = newKey();

        List<HttpResult> results = payAtTheSameTime(orderId, key);

        assertThat(results).extracting(HttpResult::status).containsOnly(200);
        assertThat(theOnlyPublishedMessage().orderId()).isEqualTo(orderId);
    }

    private PurchaseConfirmedMessage theOnlyPublishedMessage() {
        ArgumentCaptor<PurchaseConfirmedMessage> message = ArgumentCaptor.forClass(PurchaseConfirmedMessage.class);
        verify(rabbitTemplate).convertAndSend(eq("ticketya.events"), eq("purchase.confirmed"), message.capture(),
                any(MessagePostProcessor.class));
        return message.getValue();
    }

    private static HttpClientErrorException rejection() {
        HttpClientErrorException rejection = HttpClientErrorException.create(HttpStatus.UNPROCESSABLE_CONTENT, "rejected",
                new HttpHeaders(), new byte[0], StandardCharsets.UTF_8);
        rejection.setBodyConvertFunction(type -> new ErrorDto("payment_rejected", "Fondos insuficientes", 422,
                Map.of("reason", "Fondos insuficientes", "rejectedAttempts", 1)));
        return rejection;
    }

    private long reservePlaces(int quantity) {
        return post("/api/orders", """
                {"eventId": %d, "idempotencyKey": "%s", "eventSectorId": %d, "quantity": %d}"""
                .formatted(eventId, newKey(), generalSector, quantity)).orderId();
    }

    private HttpResult pay(long orderId, String key) {
        return post("/api/orders/%d/pay".formatted(orderId), """
                {"paymentMethod": "CREDIT_CARD", "idempotencyKey": "%s"}""".formatted(key));
    }

    private HttpResult post(String path, String body) {
        return client.post().uri(path)
                .header("X-User-Id", Long.toString(customer)).header("X-User-Permissions", "TICKET_PURCHASE")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) ->
                        new HttpResult(response.getStatusCode().value(), response.bodyTo(JSON_OBJECT)));
    }

    private List<HttpResult> payAtTheSameTime(long orderId, String key) throws Exception {
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(THREADS)) {
            List<Future<HttpResult>> attempts = new ArrayList<>();
            for (int attempt = 0; attempt < THREADS; attempt++) {
                attempts.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return pay(orderId, key);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<HttpResult> results = new ArrayList<>();
            for (Future<HttpResult> future : attempts) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        }
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }
}

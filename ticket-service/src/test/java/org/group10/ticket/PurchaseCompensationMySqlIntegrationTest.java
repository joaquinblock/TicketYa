package org.group10.ticket;

import org.group10.common.dto.ErrorDto;
import org.group10.ticket.client.EventClient;
import org.group10.ticket.client.PaymentClient;
import org.group10.ticket.entity.Order;
import org.group10.ticket.entity.PaymentMethod;
import org.group10.ticket.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * US-TKT-22: compensar con un reintegro cuando la compra falla después de cobrar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "ticketya.reservation.expiration-check=PT1H",
        "ticketya.compensation.refund-check=PT1H"})
@ActiveProfiles("test")
class PurchaseCompensationMySqlIntegrationTest {

    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis() + 3_000_000);
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
    };
    private static final long CUSTOMER = 701L;

    @Value("${local.server.port}")
    private int port;

    @MockitoBean
    private PaymentClient paymentClient;

    @MockitoBean
    private EventClient eventClient;

    @Autowired
    private OrderRepository orderRepository;

    private RestClient client;
    private long eventId;
    private long generalSector;

    private record HttpResult(int status, Map<String, Object> body) {

        String error() {
            return (String) body.get("error");
        }

        long orderId() {
            return ((Number) body.get("id")).longValue();
        }
    }

    @BeforeEach
    void publishEvent() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        eventId = IDS.incrementAndGet();
        long numberedSector = IDS.incrementAndGet();
        generalSector = IDS.incrementAndGet();
        int status = client.put().uri("/internal/event-sales/{eventId}", eventId)
                .header("X-Internal-Secret", "dev-internal-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"status": "PUBLISHED", "eventName": "Recital", "venueName": "Estadio",
                         "venueAddress": "Calle 1", "venueCity": "Tandil", "dateTime": "2099-12-12T21:00:00",
                         "highDemand": false,
                         "sectors": [
                           {"eventSectorId": %d, "sectorName": "Platea", "price": 1000,
                            "seats": [{"seatId": 1, "row": "A", "number": 1}, {"seatId": 2, "row": "A", "number": 2}]},
                           {"eventSectorId": %d, "sectorName": "Campo", "price": 500, "capacity": 5}
                         ]}""".formatted(numberedSector, generalSector))
                .exchange((request, response) -> response.getStatusCode().value());
        assertThat(status).isEqualTo(204);
    }

    @Test
    void pay_approved_recordsWhenTheChargeWasApproved() {
        long orderId = reserveSeats(1);

        assertThat(pay(orderId, newKey()).status()).isEqualTo(200);

        assertThat(order(orderId).getChargeApprovedAt()).isNotNull();
    }

    @Test
    void pay_rejected_doesNotRecordAnApprovedCharge() {
        long orderId = reserveSeats(1);
        doThrow(rejection()).when(paymentClient).charge(any());

        assertThat(pay(orderId, newKey()).status()).isEqualTo(422);

        assertThat(order(orderId).getChargeApprovedAt()).isNull();
    }

    @Test
    void pay_withoutResult_doesNotRecordAnApprovedCharge() {
        long orderId = reserveSeats(1);
        doThrow(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "down", new HttpHeaders(), new byte[0],
                StandardCharsets.UTF_8)).when(paymentClient).charge(any());

        assertThat(pay(orderId, newKey()).status()).isEqualTo(503);

        assertThat(order(orderId).getChargeApprovedAt()).isNull();
    }

    private HttpClientErrorException rejection() {
        HttpClientErrorException rejection = HttpClientErrorException.create(HttpStatus.UNPROCESSABLE_CONTENT,
                "rejected", new HttpHeaders(), new byte[0], StandardCharsets.UTF_8);
        rejection.setBodyConvertFunction(type -> new ErrorDto("payment_rejected", "Fondos insuficientes", 422,
                Map.of("reason", "Fondos insuficientes", "rejectedAttempts", 1)));
        return rejection;
    }

    private long reserveSeats(long... seatIds) {
        String seats = String.join(", ", java.util.Arrays.stream(seatIds).mapToObj(Long::toString).toList());
        return post("/api/orders", """
                {"eventId": %d, "idempotencyKey": "%s", "seatIds": [%s]}""".formatted(eventId, newKey(), seats)).orderId();
    }

    private long reservePlaces(int quantity) {
        return post("/api/orders", """
                {"eventId": %d, "idempotencyKey": "%s", "eventSectorId": %d, "quantity": %d}"""
                .formatted(eventId, newKey(), generalSector, quantity)).orderId();
    }

    private HttpResult pay(long orderId, String key) {
        return post("/api/orders/%d/pay".formatted(orderId), """
                {"paymentMethod": "%s", "idempotencyKey": "%s"}""".formatted(PaymentMethod.CREDIT_CARD, key));
    }

    private HttpResult post(String path, String body) {
        return client.post().uri(path)
                .header("X-User-Id", Long.toString(CUSTOMER)).header("X-User-Permissions", "TICKET_PURCHASE")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) ->
                        new HttpResult(response.getStatusCode().value(), response.bodyTo(JSON_OBJECT)));
    }

    private Order order(long orderId) {
        return orderRepository.findById(orderId).orElseThrow();
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }
}

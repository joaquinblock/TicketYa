package org.group10.ticket;

import org.group10.common.dto.ErrorDto;
import org.group10.ticket.client.EventClient;
import org.group10.ticket.client.PaymentClient;
import org.group10.ticket.client.dto.CancelPaymentRequest;
import org.group10.ticket.client.dto.PaymentResult;
import org.group10.ticket.client.dto.PaymentResultStatus;
import org.group10.ticket.client.dto.RefundRequest;
import org.group10.ticket.entity.Order;
import org.group10.ticket.entity.OrderStatus;
import org.group10.ticket.entity.PaymentMethod;
import org.group10.ticket.entity.Ticket;
import org.group10.ticket.entity.TicketStatus;
import org.group10.ticket.repository.OrderRepository;
import org.group10.ticket.repository.TicketRepository;
import org.group10.ticket.service.OrderClosingService;
import org.group10.ticket.service.PaymentAttemptResolutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * US-TKT-23: la orden vence aunque el pago siga sin resultado, y el intento se anula después.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "ticketya.reservation.expiration-check=PT1H",
        "ticketya.compensation.refund-check=PT1H",
        "ticketya.payment-attempt.resolution-check=PT1H"})
@ActiveProfiles("test")
class PaymentAttemptResolutionMySqlIntegrationTest {

    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis() + 5_000_000);
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
    };
    private static final long CUSTOMER = 702L;

    @Value("${local.server.port}")
    private int port;

    @MockitoBean
    private PaymentClient paymentClient;

    @MockitoBean
    private EventClient eventClient;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private OrderClosingService orderClosingService;

    @Autowired
    private PaymentAttemptResolutionService paymentAttemptResolutionService;

    private RestClient client;
    private long eventId;
    private long numberedSector;
    private long generalSector;

    private record HttpResult(int status, Map<String, Object> body) {

        long orderId() {
            return ((Number) body.get("id")).longValue();
        }
    }

    @BeforeEach
    void publishEvent() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        eventId = IDS.incrementAndGet();
        numberedSector = IDS.incrementAndGet();
        generalSector = IDS.incrementAndGet();
        assertThat(putEventSale("PUBLISHED")).isEqualTo(204);
    }

    @Test
    void resolve_expiredOrderApprovedLater_refundsEveryTicketAndIssuesNothing() {
        long orderId = orderWithoutPaymentResult();
        orderClosingService.closePendingOrderReleasingItsPlaces(orderId, OrderStatus.EXPIRED);
        paymentWillBe(PaymentResultStatus.APPROVED);

        paymentAttemptResolutionService.resolveAttemptsOfClosedOrders();

        assertThat(order(orderId).getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(order(orderId).getChargeApprovedAt()).isNotNull();
        assertThat(order(orderId).getPaymentAttemptKey()).isNull();
        assertThat(tickets(orderId)).allSatisfy(ticket -> {
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.VOIDED);
            assertThat(ticket.getQrCode()).isNull();
            assertThat(ticket.getCompensatedAt()).isNotNull();
        });
        assertRefundsOfCompensation(orderId);
    }

    @Test
    void resolve_orderOfACancelledEventApprovedLater_refundsEveryTicket() {
        long orderId = orderWithoutPaymentResult();
        assertThat(putEventSale("CANCELLED")).isEqualTo(204);
        paymentWillBe(PaymentResultStatus.APPROVED);

        paymentAttemptResolutionService.resolveAttemptsOfClosedOrders();

        assertThat(order(orderId).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order(orderId).getPaymentAttemptKey()).isNull();
        assertRefundsOfCompensation(orderId);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentResultStatus.class, names = {"CANCELLED", "REJECTED"})
    void resolve_paymentWithoutCharge_clearsTheAttemptWithoutRefunds(PaymentResultStatus result) {
        long orderId = orderWithoutPaymentResult();
        orderClosingService.closePendingOrderReleasingItsPlaces(orderId, OrderStatus.EXPIRED);
        paymentWillBe(result);

        paymentAttemptResolutionService.resolveAttemptsOfClosedOrders();

        assertThat(order(orderId).getPaymentAttemptKey()).isNull();
        assertThat(order(orderId).getChargeApprovedAt()).isNull();
        assertThat(refundsOf(orderId)).isEmpty();
    }

    @Test
    void resolve_paymentStillPending_asksAgainOnTheNextRun() {
        long orderId = orderWithoutPaymentResult();
        String key = order(orderId).getPaymentAttemptKey();
        orderClosingService.closePendingOrderReleasingItsPlaces(orderId, OrderStatus.EXPIRED);
        paymentWillBe(PaymentResultStatus.PENDING);

        paymentAttemptResolutionService.resolveAttemptsOfClosedOrders();
        assertThat(order(orderId).getPaymentAttemptKey()).isEqualTo(key);

        paymentWillBe(PaymentResultStatus.APPROVED);
        paymentAttemptResolutionService.resolveAttemptsOfClosedOrders();

        assertThat(cancellationsOf(key)).isEqualTo(2);
        assertThat(order(orderId).getPaymentAttemptKey()).isNull();
        assertRefundsOfCompensation(orderId);
    }

    @Test
    void resolve_paymentServiceDoesNotAnswer_asksAgainOnTheNextRun() {
        long orderId = orderWithoutPaymentResult();
        String key = order(orderId).getPaymentAttemptKey();
        orderClosingService.closePendingOrderReleasingItsPlaces(orderId, OrderStatus.EXPIRED);
        when(paymentClient.cancel(any()))
                .thenThrow(new ResourceAccessException("payment-service no respondió a tiempo"))
                .thenThrow(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "down", new HttpHeaders(),
                        new byte[0], StandardCharsets.UTF_8))
                .thenReturn(new PaymentResult(PaymentResultStatus.CANCELLED));

        paymentAttemptResolutionService.resolveAttemptsOfClosedOrders();
        paymentAttemptResolutionService.resolveAttemptsOfClosedOrders();
        assertThat(order(orderId).getPaymentAttemptKey()).isEqualTo(key);

        paymentAttemptResolutionService.resolveAttemptsOfClosedOrders();

        assertThat(order(orderId).getPaymentAttemptKey()).isNull();
        assertThat(refundsOf(orderId)).isEmpty();
    }

    @Test
    void resolve_paymentNotFound_clearsTheAttemptWithoutRefunds() {
        long orderId = orderWithoutPaymentResult();
        orderClosingService.closePendingOrderReleasingItsPlaces(orderId, OrderStatus.EXPIRED);
        HttpClientErrorException notFound = HttpClientErrorException.create(HttpStatus.NOT_FOUND, "not found",
                new HttpHeaders(), new byte[0], StandardCharsets.UTF_8);
        notFound.setBodyConvertFunction(type -> new ErrorDto("payment_not_found",
                "No se encontró el pago con clave de idempotencia", 404, null));
        when(paymentClient.cancel(any())).thenThrow(notFound);

        paymentAttemptResolutionService.resolveAttemptsOfClosedOrders();

        assertThat(order(orderId).getPaymentAttemptKey()).isNull();
        assertThat(refundsOf(orderId)).isEmpty();
    }

    @Test
    void resolve_ordersThatAreNotClosedOrHaveNoAttempt_areLeftAlone() {
        long pendingOrderId = orderWithoutPaymentResult();
        String pendingKey = order(pendingOrderId).getPaymentAttemptKey();
        long expiredWithoutAttemptId = reserveSeats(2);
        orderClosingService.closePendingOrderReleasingItsPlaces(expiredWithoutAttemptId, OrderStatus.EXPIRED);
        paymentWillBe(PaymentResultStatus.APPROVED);

        paymentAttemptResolutionService.resolveAttemptsOfClosedOrders();

        assertThat(cancellationsOf(pendingKey)).isZero();
        assertThat(order(pendingOrderId).getPaymentAttemptKey()).isEqualTo(pendingKey);
        assertThat(order(pendingOrderId).getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(refundsOf(pendingOrderId)).isEmpty();
        assertThat(refundsOf(expiredWithoutAttemptId)).isEmpty();
    }

    @Test
    void resolve_approvedAttemptAlreadyResolved_isNotCancelledNorRefundedAgain() {
        long orderId = orderWithoutPaymentResult();
        String key = order(orderId).getPaymentAttemptKey();
        orderClosingService.closePendingOrderReleasingItsPlaces(orderId, OrderStatus.EXPIRED);
        paymentWillBe(PaymentResultStatus.APPROVED);

        paymentAttemptResolutionService.resolveAttemptsOfClosedOrders();
        paymentAttemptResolutionService.resolveAttemptsOfClosedOrders();

        assertThat(cancellationsOf(key)).isEqualTo(1);
        assertRefundsOfCompensation(orderId);
    }

    private long orderWithoutPaymentResult() {
        long orderId = reserveSeats(1, 2);
        doThrow(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "down", new HttpHeaders(),
                new byte[0], StandardCharsets.UTF_8)).when(paymentClient).charge(any());
        assertThat(pay(orderId, UUID.randomUUID().toString()).status()).isEqualTo(503);
        assertThat(order(orderId).getPaymentAttemptKey()).isNotNull();
        return orderId;
    }

    private void paymentWillBe(PaymentResultStatus status) {
        when(paymentClient.cancel(any())).thenReturn(new PaymentResult(status));
    }

    private long cancellationsOf(String key) {
        ArgumentCaptor<CancelPaymentRequest> cancellations = ArgumentCaptor.forClass(CancelPaymentRequest.class);
        verify(paymentClient, atLeast(0)).cancel(cancellations.capture());
        return cancellations.getAllValues().stream().filter(cancel -> cancel.idempotencyKey().equals(key)).count();
    }

    private void assertRefundsOfCompensation(long orderId) {
        List<RefundRequest> refunds = refundsOf(orderId);
        assertThat(refunds).extracting(RefundRequest::ticketId)
                .containsExactlyInAnyOrderElementsOf(tickets(orderId).stream().map(Ticket::getId).toList());
        assertThat(refunds).allSatisfy(refund -> assertThat(refund.reason()).isEqualTo("PURCHASE_COMPENSATION"));
    }

    /**
     * Solo los reintegros de esta orden: el servicio también toma lo que dejaron pendiente otros tests.
     */
    private List<RefundRequest> refundsOf(long orderId) {
        ArgumentCaptor<RefundRequest> refunds = ArgumentCaptor.forClass(RefundRequest.class);
        verify(paymentClient, atLeast(0)).refund(refunds.capture());
        return refunds.getAllValues().stream().filter(refund -> refund.orderId().equals(orderId)).toList();
    }

    private int putEventSale(String status) {
        return client.put().uri("/internal/event-sales/{eventId}", eventId)
                .header("X-Internal-Secret", "dev-internal-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"status": "%s", "eventName": "Recital", "venueName": "Estadio",
                         "venueAddress": "Calle 1", "venueCity": "Tandil", "dateTime": "2099-12-12T21:00:00",
                         "highDemand": false,
                         "sectors": [
                           {"eventSectorId": %d, "sectorName": "Platea", "price": 1000,
                            "seats": [{"seatId": 1, "row": "A", "number": 1}, {"seatId": 2, "row": "A", "number": 2}]},
                           {"eventSectorId": %d, "sectorName": "Campo", "price": 500, "capacity": 5}
                         ]}""".formatted(status, numberedSector, generalSector))
                .exchange((request, response) -> response.getStatusCode().value());
    }

    private long reserveSeats(long... seatIds) {
        String seats = String.join(", ", java.util.Arrays.stream(seatIds).mapToObj(Long::toString).toList());
        HttpResult reservation = post("/api/orders", """
                {"eventId": %d, "idempotencyKey": "%s", "seatIds": [%s]}"""
                .formatted(eventId, UUID.randomUUID(), seats));
        assertThat(reservation.status()).as(reservation.body().toString()).isEqualTo(201);
        return reservation.orderId();
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

    private List<Ticket> tickets(long orderId) {
        return ticketRepository.findByOrderIdOrderById(orderId);
    }
}

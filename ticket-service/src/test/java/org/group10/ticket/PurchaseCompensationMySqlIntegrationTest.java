package org.group10.ticket;

import org.group10.common.dto.ErrorDto;
import org.group10.ticket.client.EventClient;
import org.group10.ticket.client.PaymentClient;
import org.group10.ticket.client.dto.PaymentResult;
import org.group10.ticket.client.dto.PaymentResultStatus;
import org.group10.ticket.client.dto.RefundRequest;
import org.group10.ticket.entity.EventSeatId;
import org.group10.ticket.entity.EventSeatStatus;
import org.group10.ticket.entity.Order;
import org.group10.ticket.entity.OrderStatus;
import org.group10.ticket.entity.PaymentMethod;
import org.group10.ticket.entity.Ticket;
import org.group10.ticket.entity.TicketStatus;
import org.group10.ticket.repository.EventSeatRepository;
import org.group10.ticket.repository.EventSectorStockRepository;
import org.group10.ticket.repository.OrderRepository;
import org.group10.ticket.repository.TicketRepository;
import org.group10.ticket.service.PurchaseCompensationService;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoSpyBean
    private EventSeatRepository eventSeatRepository;

    @Autowired
    private EventSectorStockRepository eventSectorStockRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private PurchaseCompensationService purchaseCompensationService;

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

    @Test
    void pay_issuingFailsAfterTheCharge_answersPurchaseFailedAndReleasesTheSeats() {
        long orderId = reserveSeats(1, 2);
        issuingFails();

        HttpResult result = pay(orderId, newKey());

        assertThat(result.status()).isEqualTo(409);
        assertThat(result.error()).isEqualTo("purchase_failed");
        assertThat(order(orderId).getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(seatStatus(1)).isEqualTo(EventSeatStatus.AVAILABLE);
        assertThat(seatStatus(2)).isEqualTo(EventSeatStatus.AVAILABLE);
        assertThat(tickets(orderId)).allSatisfy(ticket -> assertThat(ticket.getStatus()).isEqualTo(TicketStatus.VOIDED));
    }

    @Test
    void pay_issuingFailsAfterTheCharge_refundsEveryTicketAndMarksThemCompensated() {
        long orderId = reserveSeats(1, 2);
        issuingFails();

        pay(orderId, newKey());

        assertRefundsOfCompensation(orderId, 2);
        assertThat(tickets(orderId)).allSatisfy(ticket -> {
            assertThat(ticket.getCompensatedAt()).isNotNull();
            assertThat(ticket.getQrCode()).isNull();
        });
    }

    @Test
    void pay_issuingPlacesFailsAfterTheCharge_givesThePlacesBackToTheSector() {
        long orderId = reservePlaces(3);
        issuingFails();

        assertThat(pay(orderId, newKey()).error()).isEqualTo("purchase_failed");

        assertThat(eventSectorStockRepository.findById(generalSector).orElseThrow().getTaken()).isZero();
        assertRefundsOfCompensation(orderId, 3);
    }

    @Test
    void pay_issuingFailsAfterTheCharge_doesNotAnnounceThePurchase() {
        long orderId = reserveSeats(1);
        issuingFails();

        pay(orderId, newKey());

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class),
                any(MessagePostProcessor.class));
    }

    @Test
    void pay_issuingFailsAndTheRefundFails_stillAnswersPurchaseFailedAndLeavesItPending() {
        long orderId = reserveSeats(1);
        issuingFails();
        doThrow(new IllegalStateException("payment-service caído")).when(paymentClient).refund(any());

        assertThat(pay(orderId, newKey()).error()).isEqualTo("purchase_failed");

        assertThat(order(orderId).getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(tickets(orderId)).allSatisfy(ticket -> assertThat(ticket.getCompensatedAt()).isNull());
    }

    @Test
    void pay_orderFailed_isNotPayableAndIsNotChargedAgain() {
        long orderId = reserveSeats(1);
        issuingFails();
        pay(orderId, newKey());

        assertThat(pay(orderId, newKey()).error()).isEqualTo("order_not_payable");
        verify(paymentClient, times(1)).charge(any());
    }

    @Test
    void pay_eventCancelledWhileCharging_keepsTheOrderCancelledAndRefundsIt() {
        long orderId = reserveSeats(1);
        doAnswer(invocation -> {
            cancelTheEvent();
            return null;
        }).when(paymentClient).charge(any());

        assertThat(pay(orderId, newKey()).error()).isEqualTo("order_not_payable");

        assertThat(order(orderId).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order(orderId).getChargeApprovedAt()).isNotNull();
        assertRefundsOfCompensation(orderId, 1);
        assertThat(tickets(orderId)).allSatisfy(ticket -> assertThat(ticket.getCompensatedAt()).isNotNull());
    }

    @Test
    void pay_attemptCancelledWhileCharging_doesNotRecordAChargeNorAskForRefunds() {
        long orderId = reserveSeats(1);
        doAnswer(invocation -> {
            cancelTheEvent();
            return new PaymentResult(PaymentResultStatus.CANCELLED);
        }).when(paymentClient).charge(any());

        assertThat(pay(orderId, newKey()).error()).isEqualTo("order_not_payable");

        assertThat(order(orderId).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order(orderId).getChargeApprovedAt()).isNull();
        assertThat(refundsOf(orderId)).isEmpty();
    }

    @Test
    void refundPendingCompensations_refundThatFailedWhilePaying_isRefundedOnTheNextRun() {
        long orderId = reserveSeats(1);
        issuingFails();
        doThrow(new IllegalStateException("payment-service caído")).doNothing().when(paymentClient).refund(any());
        pay(orderId, newKey());

        purchaseCompensationService.refundPendingCompensations();

        assertThat(tickets(orderId)).allSatisfy(ticket -> assertThat(ticket.getCompensatedAt()).isNotNull());
        assertThat(refundsOf(orderId)).hasSize(2);
    }

    @Test
    void refundPendingCompensations_alreadyCompensated_doesNotAskForTheRefundAgain() {
        long orderId = reserveSeats(1, 2);
        issuingFails();
        pay(orderId, newKey());

        purchaseCompensationService.refundPendingCompensations();
        purchaseCompensationService.refundPendingCompensations();

        assertRefundsOfCompensation(orderId, 2);
    }

    @Test
    void refundPendingCompensations_oneTicketFails_refundsTheOthersAndRetriesItLater() {
        long orderId = reserveSeats(1, 2);
        issuingFails();
        doThrow(new IllegalStateException("payment-service caído")).when(paymentClient).refund(any());
        pay(orderId, newKey());
        Long failingTicket = tickets(orderId).getFirst().getId();
        doNothing().when(paymentClient).refund(any());
        doThrow(new IllegalStateException("timeout")).when(paymentClient)
                .refund(argThat(refund -> refund != null && refund.ticketId().equals(failingTicket)));

        purchaseCompensationService.refundPendingCompensations();

        assertThat(tickets(orderId)).filteredOn(ticket -> !ticket.getId().equals(failingTicket))
                .allSatisfy(ticket -> assertThat(ticket.getCompensatedAt()).isNotNull());
        assertThat(compensatedAt(failingTicket)).isNull();

        doNothing().when(paymentClient).refund(any());
        purchaseCompensationService.refundPendingCompensations();

        assertThat(compensatedAt(failingTicket)).isNotNull();
    }

    @Test
    void refundPendingCompensations_cancelledOrderWithAnApprovedCharge_isRefunded() {
        long orderId = reserveSeats(1);
        doAnswer(invocation -> {
            cancelTheEvent();
            return null;
        }).when(paymentClient).charge(any());
        doThrow(new IllegalStateException("payment-service caído")).doNothing().when(paymentClient).refund(any());
        pay(orderId, newKey());

        purchaseCompensationService.refundPendingCompensations();

        assertThat(tickets(orderId)).allSatisfy(ticket -> assertThat(ticket.getCompensatedAt()).isNotNull());
    }

    @Test
    void refundPendingCompensations_ordersThatWereNeverCharged_areLeftAlone() {
        long abandonedOrderId = reserveSeats(1);
        post("/api/orders/%d/cancel".formatted(abandonedOrderId), "");
        long paidOrderId = reserveSeats(2);
        pay(paidOrderId, newKey());

        purchaseCompensationService.refundPendingCompensations();

        assertThat(refundsOf(abandonedOrderId)).isEmpty();
        assertThat(refundsOf(paidOrderId)).isEmpty();
    }

    @Test
    void refundPendingCompensations_orderStillPendingWithAnApprovedCharge_waitsUntilItIsClosed() {
        long orderId = reserveSeats(1);
        transaction.executeWithoutResult(status -> orderRepository.markChargeApproved(orderId, LocalDateTime.now()));

        purchaseCompensationService.refundPendingCompensations();

        assertThat(refundsOf(orderId)).isEmpty();
    }

    @Test
    void refundPendingCompensations_paymentRejectsTheRefund_recordsTheErrorAndStopsRetrying() {
        long orderId = reserveSeats(1);
        issuingFails();
        doThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "conflict", new HttpHeaders(), new byte[0],
                StandardCharsets.UTF_8)).when(paymentClient).refund(any());
        pay(orderId, newKey());

        purchaseCompensationService.refundPendingCompensations();

        assertThat(tickets(orderId)).allSatisfy(ticket -> {
            assertThat(ticket.getRefundError()).contains("409");
            assertThat(ticket.getCompensatedAt()).isNull();
        });
        assertThat(refundsOf(orderId)).hasSize(1);
    }

    @Test
    void refundPendingCompensations_paymentCouldNotCreditIt_keepsRetrying() {
        long orderId = reserveSeats(1);
        issuingFails();
        doThrow(HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "refund_failed", new HttpHeaders(),
                new byte[0], StandardCharsets.UTF_8)).when(paymentClient).refund(any());
        pay(orderId, newKey());

        purchaseCompensationService.refundPendingCompensations();
        purchaseCompensationService.refundPendingCompensations();

        assertThat(tickets(orderId)).allSatisfy(ticket -> assertThat(ticket.getRefundError()).isNull());
        assertThat(refundsOf(orderId)).hasSize(3);
    }

    private LocalDateTime compensatedAt(Long ticketId) {
        return ticketRepository.findById(ticketId).orElseThrow().getCompensatedAt();
    }

    private void issuingFails() {
        doThrow(new IllegalStateException("la base no respondió al emitir"))
                .when(eventSeatRepository).sellSeatsHeldByOrder(anyLong());
    }

    private void cancelTheEvent() {
        int status = client.put().uri("/internal/event-sales/{eventId}", eventId)
                .header("X-Internal-Secret", "dev-internal-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"status": "CANCELLED", "eventName": "Recital", "venueName": "Estadio",
                         "venueAddress": "Calle 1", "venueCity": "Tandil", "dateTime": "2099-12-12T21:00:00",
                         "highDemand": false,
                         "sectors": [
                           {"eventSectorId": %d, "sectorName": "Platea", "price": 1000,
                            "seats": [{"seatId": 1, "row": "A", "number": 1}, {"seatId": 2, "row": "A", "number": 2}]},
                           {"eventSectorId": %d, "sectorName": "Campo", "price": 500, "capacity": 5}
                         ]}""".formatted(generalSector - 1, generalSector))
                .exchange((request, response) -> response.getStatusCode().value());
        assertThat(status).isEqualTo(204);
    }

    private void assertRefundsOfCompensation(long orderId, int tickets) {
        List<RefundRequest> refunds = refundsOf(orderId);
        assertThat(refunds).hasSize(tickets);
        assertThat(refunds).extracting(RefundRequest::ticketId)
                .containsExactlyInAnyOrderElementsOf(tickets(orderId).stream().map(Ticket::getId).toList());
        assertThat(refunds).allSatisfy(refund -> {
            assertThat(refund.orderId()).isEqualTo(orderId);
            assertThat(refund.reason()).isEqualTo("PURCHASE_COMPENSATION");
            assertThat(refund.returnRequestId()).isNull();
        });
    }

    /**
     * Solo los reintegros de esta orden: el job también toma lo que dejaron pendiente otros tests.
     */
    private List<RefundRequest> refundsOf(long orderId) {
        ArgumentCaptor<RefundRequest> refunds = ArgumentCaptor.forClass(RefundRequest.class);
        verify(paymentClient, atLeast(0)).refund(refunds.capture());
        return refunds.getAllValues().stream().filter(refund -> refund.orderId().equals(orderId)).toList();
    }

    private List<Ticket> tickets(long orderId) {
        return ticketRepository.findByOrderIdOrderById(orderId);
    }

    private EventSeatStatus seatStatus(long seatId) {
        return transaction.execute(status ->
                eventSeatRepository.findById(new EventSeatId(eventId, seatId)).orElseThrow().getStatus());
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

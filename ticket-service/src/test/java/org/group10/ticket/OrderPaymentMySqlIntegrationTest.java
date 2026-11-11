package org.group10.ticket;

import org.group10.common.dto.ErrorDto;
import org.group10.ticket.client.EventClient;
import org.group10.ticket.client.PaymentClient;
import org.group10.ticket.client.dto.ChargeRequest;
import org.group10.ticket.client.dto.RefundRequest;
import org.group10.ticket.entity.EventSaleStatus;
import org.group10.ticket.entity.EventSeat;
import org.group10.ticket.entity.EventSeatId;
import org.group10.ticket.entity.EventSeatStatus;
import org.group10.ticket.entity.Order;
import org.group10.ticket.entity.OrderStatus;
import org.group10.ticket.entity.PaymentMethod;
import org.group10.ticket.repository.EventSaleRepository;
import org.group10.ticket.repository.EventSeatRepository;
import org.group10.ticket.repository.EventSectorStockRepository;
import org.group10.ticket.repository.OrderRepository;
import org.group10.ticket.service.SoldOutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
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
import java.util.function.Consumer;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "ticketya.reservation.expiration-check=PT1H")
@ActiveProfiles("test")
class OrderPaymentMySqlIntegrationTest {

    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis() + 1_000_000);
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_LIST = new ParameterizedTypeReference<>() {
    };
    private static final int THREADS = 8;
    private static final long CUSTOMER = 601L;
    private static final long ANOTHER_CUSTOMER = 602L;

    @Value("${local.server.port}")
    private int port;

    @MockitoBean
    private PaymentClient paymentClient;

    @MockitoSpyBean
    private Clock clock;

    @MockitoBean
    private EventClient eventClient;

    @Autowired
    private SoldOutService soldOutService;
    @Autowired
    private EventSaleRepository eventSaleRepository;

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private EventSeatRepository eventSeatRepository;
    @Autowired
    private EventSectorStockRepository eventSectorStockRepository;
    @Autowired
    private TransactionTemplate transaction;

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

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tickets() {
            return (List<Map<String, Object>>) body.get("tickets");
        }

        List<Object> qrCodes() {
            return tickets().stream().map(ticket -> ticket.get("qrCode")).toList();
        }
    }

    @BeforeEach
    void publishEvent() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        eventId = IDS.incrementAndGet();
        long numberedSector = IDS.incrementAndGet();
        generalSector = IDS.incrementAndGet();
        int status = client.put().uri("/internal/event-sales/{eventId}", eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"status": "PUBLISHED", "eventName": "Recital", "venueName": "Estadio",
                         "venueAddress": "Calle 1", "venueCity": "Tandil", "dateTime": "2099-12-12T21:00:00",
                         "highDemand": false,
                         "sectors": [
                           {"eventSectorId": %d, "sectorName": "Platea", "price": 1000,
                            "seats": [{"seatId": 1, "row": "A", "number": 1}, {"seatId": 2, "row": "A", "number": 2},
                                      {"seatId": 3, "row": "B", "number": 1}, {"seatId": 4, "row": "B", "number": 2}]},
                           {"eventSectorId": %d, "sectorName": "Campo", "price": 500, "capacity": 5}
                         ]}""".formatted(numberedSector, generalSector))
                .exchange((request, response) -> response.getStatusCode().value());
        assertThat(status).isEqualTo(204);
    }

    @Test
    void pay_approved_issuesTicketsWithDistinctQrAndSellsTheSeats() {
        long orderId = reserveSeats(CUSTOMER, 1, 2);

        HttpResult paid = pay(CUSTOMER, orderId, newKey());

        assertThat(paid.status()).isEqualTo(200);
        assertThat(paid.body().get("status")).isEqualTo("PAID");
        assertThat(paid.body().get("paymentMethod")).isEqualTo("CREDIT_CARD");
        assertThat(paid.tickets()).allSatisfy(ticket -> assertThat(ticket.get("status")).isEqualTo("ISSUED"));
        assertThat(paid.qrCodes()).doesNotContainNull().doesNotHaveDuplicates();
        assertThat(seat(1).getStatus()).isEqualTo(EventSeatStatus.SOLD);
        assertThat(seat(1).getReservedUntil()).isNull();

        ArgumentCaptor<ChargeRequest> charge = ArgumentCaptor.forClass(ChargeRequest.class);
        verify(paymentClient).charge(charge.capture());
        assertThat(charge.getValue().amount()).isEqualByComparingTo(new BigDecimal("2000"));
        assertThat(charge.getValue().orderId()).isEqualTo(orderId);
    }

    @Test
    void pay_alreadyPaid_answersWithTheSameTicketsWithoutChargingAgain() {
        long orderId = reservePlaces(CUSTOMER, 2);
        HttpResult paid = pay(CUSTOMER, orderId, newKey());

        HttpResult repeated = pay(CUSTOMER, orderId, newKey());

        assertThat(repeated.status()).isEqualTo(200);
        assertThat(repeated.qrCodes()).isEqualTo(paid.qrCodes());
        verify(paymentClient, times(1)).charge(any());
    }

    @Test
    void pay_sameAttemptAtTheSameTime_issuesASingleSetOfTickets() throws Exception {
        long orderId = reserveSeats(CUSTOMER, 1, 2);
        String key = newKey();

        List<HttpResult> results = runAtTheSameTime(attempt -> pay(CUSTOMER, orderId, key));

        assertThat(results).extracting(HttpResult::status).containsOnly(200);
        assertThat(results).extracting(HttpResult::qrCodes).containsOnly(results.getFirst().qrCodes());
        assertThat(results.getFirst().qrCodes()).doesNotContainNull();
    }

    @Test
    void pay_differentAttemptsAtTheSameTime_chargesOnlyOnce() throws Exception {
        long orderId = reservePlaces(CUSTOMER, 1);

        List<HttpResult> results = runAtTheSameTime(attempt -> pay(CUSTOMER, orderId, newKey()));

        assertThat(results).filteredOn(result -> result.status() != 200)
                .allSatisfy(result -> assertThat(result.error()).isEqualTo("payment_in_progress"));
        assertThat(results).extracting(HttpResult::status).contains(200);
        verify(paymentClient, times(1)).charge(any());
    }

    @Test
    void pay_rejected_keepsTheOrderPendingAndAcceptsAnotherMethod() {
        long orderId = reserveSeats(CUSTOMER, 1);
        LocalDateTime expiresAt = order(orderId).getExpiresAt();
        doThrow(rejection(1)).when(paymentClient).charge(any());

        HttpResult rejected = pay(CUSTOMER, orderId, newKey());

        assertThat(order(orderId).getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order(orderId).getPaymentAttemptKey()).isNull();
        assertThat(order(orderId).getExpiresAt()).isEqualTo(expiresAt);
        assertThat(seat(1).getStatus()).isEqualTo(EventSeatStatus.RESERVED);
        assertThat(rejected.status()).isEqualTo(422);

        doNothing().when(paymentClient).charge(any());
        assertThat(pay(CUSTOMER, orderId, newKey()).status()).isEqualTo(200);
    }

    @Test
    void pay_thirdRejection_rejectsTheOrderAndReleasesItsPlaces() {
        long seatsOrderId = reserveSeats(CUSTOMER, 1);
        long placesOrderId = reservePlaces(CUSTOMER, 2);
        doThrow(rejection(3)).when(paymentClient).charge(any());

        pay(CUSTOMER, seatsOrderId, newKey());
        pay(CUSTOMER, placesOrderId, newKey());

        assertThat(order(seatsOrderId).getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(seat(1).getStatus()).isEqualTo(EventSeatStatus.AVAILABLE);
        assertThat(eventSectorStockRepository.findById(generalSector).orElseThrow().getTaken()).isZero();
        assertThat(pay(CUSTOMER, seatsOrderId, newKey()).error()).isEqualTo("order_not_payable");
    }

    @Test
    void pay_withoutResult_onlyAllowsToRepeatThatSameAttempt() {
        long orderId = reserveSeats(CUSTOMER, 1);
        String key = newKey();
        doThrow(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "down", new HttpHeaders(), new byte[0],
                StandardCharsets.UTF_8)).when(paymentClient).charge(any());

        assertThat(pay(CUSTOMER, orderId, key).status()).isEqualTo(503);
        assertThat(order(orderId).getPaymentAttemptKey()).isEqualTo(key);

        doNothing().when(paymentClient).charge(any());
        assertThat(pay(CUSTOMER, orderId, newKey()).error()).isEqualTo("payment_in_progress");
        assertThat(pay(CUSTOMER, orderId, key).status()).isEqualTo(200);
    }

    @Test
    void pay_reservationAlreadyOverdue_returnsExpiredWithoutCharging() {
        long orderId = reserveSeats(CUSTOMER, 1);
        changeOrder(orderId, order -> order.setExpiresAt(LocalDateTime.now().minusSeconds(1)));

        assertThat(pay(CUSTOMER, orderId, newKey()).error()).isEqualTo("reservation_expired");
        verify(paymentClient, never()).charge(any());
    }

    @Test
    void pay_newAttemptInTheLastMinute_returnsWindowClosedWithoutCharging() {
        long orderId = reserveSeats(CUSTOMER, 1);
        changeOrder(orderId, order -> order.setExpiresAt(LocalDateTime.now().plusSeconds(30)));

        assertThat(pay(CUSTOMER, orderId, newKey()).error()).isEqualTo("payment_window_closed");
        verify(paymentClient, never()).charge(any());
    }

    @Test
    void pay_sameAttemptInTheLastMinute_isStillAccepted() {
        long orderId = reserveSeats(CUSTOMER, 1);
        String key = newKey();
        changeOrder(orderId, order -> {
            order.setExpiresAt(LocalDateTime.now().plusSeconds(30));
            order.setPaymentAttemptKey(key);
        });

        assertThat(pay(CUSTOMER, orderId, key).status()).isEqualTo(200);
    }

    @Test
    void pay_reservationExpiresWhileCharging_refundsEveryTicketAndIssuesNothing() {
        long orderId = reserveSeats(CUSTOMER, 1, 2);
        chargeTakesLongerThanTheReservation();

        HttpResult result = pay(CUSTOMER, orderId, newKey());

        assertThat(result.status()).isEqualTo(409);
        assertThat(result.error()).isEqualTo("reservation_expired");
        assertThat(order(orderId).getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(seat(1).getStatus()).isEqualTo(EventSeatStatus.AVAILABLE);
        assertRefundsOfCompensation(orderId, 2);
    }

    @Test
    void pay_seatTakenByAnotherOrderWhileCharging_refundsAndLeavesTheSeatToTheOtherOrder() {
        long orderId = reserveSeats(CUSTOMER, 1);
        doAnswer(invocation -> {
            transaction.executeWithoutResult(status -> eventSeat(1).setReservedUntil(LocalDateTime.now().minusSeconds(1)));
            reserveSeats(ANOTHER_CUSTOMER, 1);
            return null;
        }).when(paymentClient).charge(any());

        HttpResult result = pay(CUSTOMER, orderId, newKey());

        assertThat(result.error()).isEqualTo("reservation_expired");
        assertThat(seat(1).getStatus()).isEqualTo(EventSeatStatus.RESERVED);
        Long holder = transaction.execute(status -> eventSeat(1).getOrder().getUserId());
        assertThat(holder).isEqualTo(ANOTHER_CUSTOMER);
        assertRefundsOfCompensation(orderId, 1);
    }

    @Test
    void pay_refundFails_stillAnswersReservationExpired() {
        long orderId = reservePlaces(CUSTOMER, 1);
        chargeTakesLongerThanTheReservation();
        doThrow(new IllegalStateException("payment-service caído")).when(paymentClient).refund(any());

        assertThat(pay(CUSTOMER, orderId, newKey()).error()).isEqualTo("reservation_expired");
    }

    @Test
    void pay_orderOfAnotherCustomer_returnsNotFoundWithoutCharging() {
        long orderId = reserveSeats(CUSTOMER, 1);

        assertThat(pay(ANOTHER_CUSTOMER, orderId, newKey()).error()).isEqualTo("order_not_found");
        verify(paymentClient, never()).charge(any());
    }

    @Test
    void pay_expiredOrder_returnsReservationExpired() {
        long orderId = reserveSeats(CUSTOMER, 1);
        changeOrder(orderId, order -> order.setStatus(OrderStatus.EXPIRED));

        assertThat(pay(CUSTOMER, orderId, newKey()).error()).isEqualTo("reservation_expired");
    }

    @Test
    void pay_cancelledOrder_returnsNotPayable() {
        long orderId = reserveSeats(CUSTOMER, 1);
        post("/api/orders/%d/cancel".formatted(orderId), CUSTOMER, "");

        assertThat(pay(CUSTOMER, orderId, newKey()).error()).isEqualTo("order_not_payable");
    }

    @Test
    void pay_lastPlaces_marksTheSaleSoldOutAndTheJobNotifiesIt() {
        sellEverything();

        assertThat(eventSaleRepository.findById(eventId).orElseThrow().getStatus()).isEqualTo(EventSaleStatus.SOLD_OUT);

        soldOutService.notifyPendingAvailabilityChanges();
        soldOutService.notifyPendingAvailabilityChanges();

        verify(eventClient, times(1)).markEventSoldOut(eventId);
    }

    @Test
    void pay_withAReservationInProgress_doesNotSellOut() {
        long seatsOrderId = reserveSeats(CUSTOMER, 1, 2, 3, 4);
        reservePlaces(ANOTHER_CUSTOMER, 5);

        pay(CUSTOMER, seatsOrderId, newKey());

        assertThat(eventSaleRepository.findById(eventId).orElseThrow().getStatus()).isEqualTo(EventSaleStatus.PUBLISHED);
    }

    @Test
    void notifyPendingAvailabilityChanges_eventServiceDown_retriesOnTheNextRun() {
        sellEverything();
        doThrow(new ResourceAccessException("event-service caído")).when(eventClient).markEventSoldOut(eventId);

        soldOutService.notifyPendingAvailabilityChanges();

        assertThat(eventSaleRepository.findById(eventId).orElseThrow().isAvailabilityNotificationPending()).isTrue();

        doNothing().when(eventClient).markEventSoldOut(eventId);
        soldOutService.notifyPendingAvailabilityChanges();

        assertThat(eventSaleRepository.findById(eventId).orElseThrow().isAvailabilityNotificationPending()).isFalse();
    }

    @Test
    void notifyPendingAvailabilityChanges_eventServiceRejectsIt_stopsRetrying() {
        sellEverything();
        doThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "conflict", new HttpHeaders(), new byte[0],
                StandardCharsets.UTF_8)).when(eventClient).markEventSoldOut(eventId);

        soldOutService.notifyPendingAvailabilityChanges();

        assertThat(eventSaleRepository.findById(eventId).orElseThrow().isAvailabilityNotificationPending()).isFalse();
    }

    private void sellEverything() {
        long seatsOrderId = reserveSeats(CUSTOMER, 1, 2, 3, 4);
        long placesOrderId = reservePlaces(CUSTOMER, 5);
        pay(CUSTOMER, seatsOrderId, newKey());
        pay(CUSTOMER, placesOrderId, newKey());
    }

    @Test
    void findMyOrders_showsOnlyMineAndTheQrOnlyOnIssuedTickets() {
        long customer = IDS.incrementAndGet();
        long paidOrderId = reserveSeats(customer, 1);
        long pendingOrderId = reserveSeats(customer, 2);
        reserveSeats(ANOTHER_CUSTOMER, 3);
        pay(customer, paidOrderId, newKey());

        List<Map<String, Object>> orders = client.get().uri("/api/orders")
                .header("X-User-Id", Long.toString(customer))
                .exchange((request, response) -> response.bodyTo(JSON_LIST));

        assertThat(orders).extracting(order -> ((Number) order.get("id")).longValue())
                .containsExactly(pendingOrderId, paidOrderId);
        HttpResult pending = new HttpResult(200, orders.getFirst());
        HttpResult paid = new HttpResult(200, orders.getLast());
        assertThat(pending.qrCodes()).containsOnlyNulls();
        assertThat(paid.qrCodes()).doesNotContainNull();
        assertThat(paid.body().get("paymentMethod")).isEqualTo("CREDIT_CARD");
    }

    @Test
    void pay_seats_ticketsShowTheEventTheSectorAndTheSeatToPrintNextToTheQr() {
        long orderId = reserveSeats(CUSTOMER, 3, 4);

        HttpResult paid = pay(CUSTOMER, orderId, newKey());

        assertThat(paid.body().get("event")).isEqualTo(Map.of("name", "Recital", "dateTime", "2099-12-12T21:00:00",
                "venueName", "Estadio", "venueAddress", "Calle 1", "venueCity", "Tandil"));
        assertThat(paid.tickets())
                .extracting(ticket -> ticket.get("sectorName"), ticket -> ticket.get("row"), ticket -> ticket.get("number"))
                .containsExactly(tuple("Platea", "B", 1), tuple("Platea", "B", 2));
    }

    @Test
    void pay_placesWithoutSeat_eachTicketHasItsOwnQrAndOnlyTheSector() {
        long orderId = reservePlaces(CUSTOMER, 2);

        HttpResult paid = pay(CUSTOMER, orderId, newKey());

        assertThat(paid.tickets()).hasSize(2).allSatisfy(ticket -> {
            assertThat(ticket.get("sectorName")).isEqualTo("Campo");
            assertThat(ticket.get("seatId")).isNull();
            assertThat(ticket.get("row")).isNull();
            assertThat(ticket.get("number")).isNull();
        });
        assertThat(paid.qrCodes()).doesNotContainNull().doesNotHaveDuplicates();
    }

    @Test
    void findMyOrder_ofAnotherCustomer_returnsNotFound() {
        long orderId = reserveSeats(CUSTOMER, 1);

        HttpResult result = client.get().uri("/api/orders/{id}", orderId)
                .header("X-User-Id", Long.toString(ANOTHER_CUSTOMER))
                .exchange((request, response) ->
                        new HttpResult(response.getStatusCode().value(), response.bodyTo(JSON_OBJECT)));

        assertThat(result.status()).isEqualTo(404);
        assertThat(result.error()).isEqualTo("order_not_found");
    }

    private HttpClientErrorException rejection(int rejectedAttempts) {
        HttpClientErrorException rejection = HttpClientErrorException.create(HttpStatus.UNPROCESSABLE_CONTENT, "rejected",
                new HttpHeaders(), new byte[0], StandardCharsets.UTF_8);
        rejection.setBodyConvertFunction(type -> new ErrorDto("payment_rejected", "Fondos insuficientes", 422,
                Map.of("reason", "Fondos insuficientes", "rejectedAttempts", rejectedAttempts)));
        return rejection;
    }

    private void assertRefundsOfCompensation(long orderId, int tickets) {
        ArgumentCaptor<RefundRequest> refunds = ArgumentCaptor.forClass(RefundRequest.class);
        verify(paymentClient, times(tickets)).refund(refunds.capture());
        assertThat(refunds.getAllValues()).allSatisfy(refund -> {
            assertThat(refund.orderId()).isEqualTo(orderId);
            assertThat(refund.reason()).isEqualTo("PURCHASE_COMPENSATION");
        });
    }

    private void chargeTakesLongerThanTheReservation() {
        doAnswer(invocation -> {
            doReturn(Instant.now().plus(Duration.ofMinutes(11))).when(clock).instant();
            return null;
        }).when(paymentClient).charge(any());
    }

    private long reserveSeats(long customer, long... seatIds) {
        String seats = String.join(", ", java.util.Arrays.stream(seatIds).mapToObj(Long::toString).toList());
        return post("/api/orders", customer, """
                {"eventId": %d, "idempotencyKey": "%s", "seatIds": [%s]}""".formatted(eventId, newKey(), seats)).orderId();
    }

    private long reservePlaces(long customer, int quantity) {
        return post("/api/orders", customer, """
                {"eventId": %d, "idempotencyKey": "%s", "eventSectorId": %d, "quantity": %d}"""
                .formatted(eventId, newKey(), generalSector, quantity)).orderId();
    }

    private HttpResult pay(long customer, long orderId, String key) {
        return post("/api/orders/%d/pay".formatted(orderId), customer, """
                {"paymentMethod": "%s", "idempotencyKey": "%s"}""".formatted(PaymentMethod.CREDIT_CARD, key));
    }

    private HttpResult post(String path, long customer, String body) {
        return client.post().uri(path)
                .header("X-User-Id", Long.toString(customer))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) ->
                        new HttpResult(response.getStatusCode().value(), response.bodyTo(JSON_OBJECT)));
    }

    private List<HttpResult> runAtTheSameTime(IntFunction<HttpResult> attempt) throws Exception {
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(THREADS)) {
            List<Future<HttpResult>> attempts = new ArrayList<>();
            for (int number = 0; number < THREADS; number++) {
                int attemptNumber = number;
                attempts.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return attempt.apply(attemptNumber);
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

    private void changeOrder(long orderId, Consumer<Order> change) {
        transaction.executeWithoutResult(status -> change.accept(orderRepository.findById(orderId).orElseThrow()));
    }

    private Order order(long orderId) {
        return orderRepository.findById(orderId).orElseThrow();
    }

    private EventSeat seat(long seatId) {
        return transaction.execute(status -> eventSeat(seatId));
    }

    private EventSeat eventSeat(long seatId) {
        return eventSeatRepository.findById(new EventSeatId(eventId, seatId)).orElseThrow();
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }
}

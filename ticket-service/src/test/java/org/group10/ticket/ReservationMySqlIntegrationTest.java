package org.group10.ticket;

import org.group10.ticket.entity.EventSeat;
import org.group10.ticket.entity.EventSeatId;
import org.group10.ticket.entity.EventSectorStock;
import org.group10.ticket.entity.Order;
import org.group10.ticket.entity.OrderStatus;
import org.group10.ticket.repository.EventSaleRepository;
import org.group10.ticket.repository.EventSeatRepository;
import org.group10.ticket.repository.EventSectorStockRepository;
import org.group10.ticket.repository.OrderRepository;
import org.group10.ticket.repository.TicketRepository;
import org.group10.ticket.service.OrderClosingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "ticketya.reservation.expiration-check=PT1H")
@ActiveProfiles("test")
class ReservationMySqlIntegrationTest {

    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis());
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
    };
    private static final int THREADS = 8;
    private static final long CUSTOMER = 501L;
    private static final long ANOTHER_CUSTOMER = 502L;

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private EventSeatRepository eventSeatRepository;
    @Autowired
    private EventSaleRepository eventSaleRepository;
    @Autowired
    private Clock clock;
    @Autowired
    private EventSectorStockRepository eventSectorStockRepository;
    @Autowired
    private OrderClosingService orderClosingService;
    @Autowired
    private TransactionTemplate transaction;

    private RestClient client;
    private long eventId;
    private long numberedSector;
    private long generalSector;

    private record HttpResult(int status, Map<String, Object> body) {

        String error() {
            return (String) body.get("error");
        }

        long orderId() {
            return ((Number) body.get("id")).longValue();
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list(String field) {
            return (List<Map<String, Object>>) body.get(field);
        }
    }

    @BeforeEach
    void publishEventWithTenSeatsAndThreePlaces() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        eventId = IDS.incrementAndGet();
        numberedSector = IDS.incrementAndGet();
        generalSector = IDS.incrementAndGet();
        assertThat(synchronizeSale("PUBLISHED", "null", 1000, 3, 10).status()).isEqualTo(204);
    }

    @Test
    void reserve_freeSeats_createsPendingOrderThatHoldsThem() {
        HttpResult reservation = reserveSeats(CUSTOMER, newKey(), 1, 2);

        assertThat(reservation.status()).isEqualTo(201);
        assertThat(reservation.body().get("status")).isEqualTo("PENDING");
        assertThat(((Number) reservation.body().get("total")).intValue()).isEqualTo(2000);
        assertThat(reservation.list("tickets")).hasSize(2)
                .allSatisfy(ticket -> assertThat(ticket.get("status")).isEqualTo("RESERVED"));

        Map<String, Object> seats = availabilityOf(numberedSector);
        assertThat(((Number) seats.get("availablePlaces")).intValue()).isEqualTo(8);
    }

    @Test
    void reserve_sectorPlaces_takesThemFromTheStock() {
        HttpResult reservation = reservePlaces(CUSTOMER, newKey(), 2);

        assertThat(reservation.status()).isEqualTo(201);
        assertThat(((Number) reservation.body().get("total")).intValue()).isEqualTo(1000);
        assertThat(stock().getTaken()).isEqualTo(2);
        assertThat(((Number) availabilityOf(generalSector).get("availablePlaces")).intValue()).isEqualTo(1);
    }

    @Test
    void reserve_moreSectorPlacesThanLeft_returnsSoldOutAndTakesNothing() {
        HttpResult reservation = reservePlaces(CUSTOMER, newKey(), 4);

        assertThat(reservation.status()).isEqualTo(409);
        assertThat(reservation.error()).isEqualTo("sector_sold_out");
        assertThat(stock().getTaken()).isZero();
    }

    @Test
    void reserve_oneOfTheSeatsTaken_reservesNoneOfThem() {
        reserveSeats(ANOTHER_CUSTOMER, newKey(), 2);
        String key = newKey();

        HttpResult reservation = reserveSeats(CUSTOMER, key, 1, 2, 3);

        assertThat(reservation.status()).isEqualTo(409);
        assertThat(reservation.error()).isEqualTo("seat_unavailable");
        assertThat(orderRepository.findByIdempotencyKey(key)).isEmpty();
        assertThat(seat(1).getOrder()).isNull();
        assertThat(seat(3).getOrder()).isNull();
    }

    @Test
    void reserve_seatThatIsNotInTheEvent_returnsNotFoundWithTheMissingSeat() {
        HttpResult reservation = reserveSeats(CUSTOMER, newKey(), 1, 999);

        assertThat(reservation.status()).isEqualTo(404);
        assertThat(reservation.error()).isEqualTo("resource_not_found");
        assertThat(reservation.body().get("detail").toString()).contains("999");
    }

    @Test
    void reserve_manyCustomersForTheSameSeats_onlyOneGetsThem() throws Exception {
        List<HttpResult> results = runAtTheSameTime(attempt -> reserveSeats(1000L + attempt, newKey(), 4, 5, 6));

        assertThat(results).extracting(HttpResult::status).containsOnlyOnce(201);
        assertThat(results).filteredOn(result -> result.status() != 201)
                .allSatisfy(result -> assertThat(result.error()).isEqualTo("seat_unavailable"));
        HttpResult winner = results.stream().filter(result -> result.status() == 201).findFirst().orElseThrow();
        assertThat(ticketRepository.findByOrderIdOrderById(winner.orderId())).hasSize(3);
        assertThat(seat(4).getOrder().getId()).isEqualTo(winner.orderId());
    }

    @Test
    void reserve_moreCustomersThanPlaces_neverSellsMoreThanTheCapacity() throws Exception {
        List<HttpResult> results = runAtTheSameTime(attempt -> reservePlaces(1000L + attempt, newKey(), 1));

        assertThat(results).filteredOn(result -> result.status() == 201).hasSize(3);
        assertThat(results).filteredOn(result -> result.status() != 201)
                .allSatisfy(result -> assertThat(result.error()).isEqualTo("sector_sold_out"));
        assertThat(stock().getTaken()).isEqualTo(3);
    }

    @Test
    void reserve_sameKeyAtTheSameTime_createsASingleOrder() throws Exception {
        String key = newKey();

        List<HttpResult> results = runAtTheSameTime(attempt -> reserveSeats(CUSTOMER, key, 7, 8));

        assertThat(results).extracting(HttpResult::status).containsOnly(201);
        assertThat(results).extracting(HttpResult::orderId).containsOnly(results.getFirst().orderId());
    }

    @Test
    void reserve_sameKeyAtTheSameTimeInASector_takesThePlacesOnce() throws Exception {
        String key = newKey();

        List<HttpResult> results = runAtTheSameTime(attempt -> reservePlaces(CUSTOMER, key, 2));

        assertThat(results).extracting(HttpResult::status).containsOnly(201);
        assertThat(stock().getTaken()).isEqualTo(2);
    }

    @Test
    void reserve_repeatedKeyWithTheSameData_answersWithTheSameOrder() {
        String key = newKey();
        long orderId = reserveSeats(CUSTOMER, key, 1, 2).orderId();

        HttpResult repeated = reserveSeats(CUSTOMER, key, 2, 1);

        assertThat(repeated.status()).isEqualTo(201);
        assertThat(repeated.orderId()).isEqualTo(orderId);
    }

    @Test
    void reserve_keyUsedByAnotherCustomer_returnsDataConflict() {
        String key = newKey();
        reserveSeats(CUSTOMER, key, 1);

        assertThat(reserveSeats(ANOTHER_CUSTOMER, key, 1).error()).isEqualTo("data_conflict");
    }

    @Test
    void reserve_keyUsedForOtherSeats_returnsDataConflict() {
        String key = newKey();
        reserveSeats(CUSTOMER, key, 1);

        assertThat(reserveSeats(CUSTOMER, key, 2).error()).isEqualTo("data_conflict");
    }

    @Test
    void reserve_seatWithAnExpiredHold_canBeTakenBeforeTheJobRuns() {
        long overdueOrderId = reserveSeats(ANOTHER_CUSTOMER, newKey(), 1).orderId();
        moveHoldToThePast(overdueOrderId, 1);

        HttpResult reservation = reserveSeats(CUSTOMER, newKey(), 1);

        assertThat(reservation.status()).isEqualTo(201);
        assertThat(seat(1).getOrder().getId()).isEqualTo(reservation.orderId());
    }

    @Test
    void reserve_overTheLimitOfTheCustomer_returnsLimitExceeded() {
        assertThat(synchronizeSale("PUBLISHED", "null", 1000, 3, 2).status()).isEqualTo(204);
        reserveSeats(CUSTOMER, newKey(), 1, 2);

        HttpResult reservation = reserveSeats(CUSTOMER, newKey(), 3);

        assertThat(reservation.status()).isEqualTo(409);
        assertThat(reservation.error()).isEqualTo("ticket_limit_exceeded");
    }

    @Test
    void reserve_afterAbandoningAReservation_theLimitIsFreedAgain() {
        assertThat(synchronizeSale("PUBLISHED", "null", 1000, 3, 2).status()).isEqualTo(204);
        long orderId = reserveSeats(CUSTOMER, newKey(), 1, 2).orderId();
        cancel(CUSTOMER, orderId);

        assertThat(reserveSeats(CUSTOMER, newKey(), 3, 4).status()).isEqualTo(201);
    }

    @Test
    void reserve_cancelledSale_returnsEventNotOnSale() {
        assertThat(synchronizeSale("CANCELLED", "null", 1000, 3, 10).status()).isEqualTo(204);

        assertThat(reserveSeats(CUSTOMER, newKey(), 1).error()).isEqualTo("event_not_on_sale");
    }

    @Test
    void reserve_eventAlreadyStartedButStillPublished_returnsEventNotOnSale() {
        transaction.executeWithoutResult(status -> eventSaleRepository.findById(eventId).orElseThrow()
                .setDateTime(LocalDateTime.now(clock).minusMinutes(1)));

        assertThat(reserveSeats(CUSTOMER, newKey(), 1).error()).isEqualTo("event_not_on_sale");
    }

    @Test
    void reserve_beforeTheSaleOpens_returnsSaleNotStarted() {
        assertThat(synchronizeSale("PUBLISHED", "\"2099-01-01T10:00:00\"", 1000, 3, 10).status()).isEqualTo(204);

        assertThat(reserveSeats(CUSTOMER, newKey(), 1).error()).isEqualTo("sale_not_started");
    }

    @Test
    void reserve_eventWithoutSale_returnsNotFound() {
        HttpResult reservation = post("/api/orders", CUSTOMER, """
                {"eventId": %d, "idempotencyKey": "%s", "seatIds": [1]}""".formatted(IDS.incrementAndGet(), newKey()));

        assertThat(reservation.status()).isEqualTo(404);
        assertThat(reservation.error()).isEqualTo("resource_not_found");
    }

    @Test
    void synchronizeSale_repeated_keepsWhatWasAlreadyTakenAndUpdatesThePrice() {
        reserveSeats(CUSTOMER, newKey(), 1);
        reservePlaces(CUSTOMER, newKey(), 2);

        assertThat(synchronizeSale("PUBLISHED", "null", 2500, 3, 10).status()).isEqualTo(204);

        assertThat(stock().getTaken()).isEqualTo(2);
        assertThat(seat(1).getOrder()).isNotNull();
        assertThat(((Number) availabilityOf(numberedSector).get("price")).intValue()).isEqualTo(2500);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAvailability_showsTheSectorNamesAndTheRowAndNumberOfEachSeat() {
        Map<String, Object> numbered = availabilityOf(numberedSector);
        Map<String, Object> general = availabilityOf(generalSector);

        assertThat(numbered.get("sectorName")).isEqualTo("Platea");
        assertThat(general.get("sectorName")).isEqualTo("Campo");
        assertThat(((List<Map<String, Object>>) numbered.get("seats")).getFirst())
                .containsEntry("seatId", 1).containsEntry("row", "A").containsEntry("number", 1);
    }

    @Test
    void findAvailability_eventWithoutSale_returnsNotFound() {
        assertThat(get("/api/availability?eventId=" + IDS.incrementAndGet()).status()).isEqualTo(404);
    }

    @Test
    void expireOverdueReservations_overdueOrder_releasesItsPlacesOnce() {
        long seatsOrderId = reserveSeats(CUSTOMER, newKey(), 1).orderId();
        long placesOrderId = reservePlaces(ANOTHER_CUSTOMER, newKey(), 2).orderId();
        moveHoldToThePast(seatsOrderId, 1);
        changeOrder(placesOrderId, order -> order.setExpiresAt(LocalDateTime.now().minusMinutes(1)));

        orderClosingService.expireOverdueReservations();
        orderClosingService.expireOverdueReservations();

        assertThat(statusOf(seatsOrderId)).isEqualTo(OrderStatus.EXPIRED);
        assertThat(statusOf(placesOrderId)).isEqualTo(OrderStatus.EXPIRED);
        assertThat(seat(1).getOrder()).isNull();
        assertThat(stock().getTaken()).isZero();
    }

    @Test
    void expireOverdueReservations_seatAlreadyTakenByAnotherOrder_doesNotReleaseIt() {
        long overdueOrderId = reserveSeats(ANOTHER_CUSTOMER, newKey(), 1).orderId();
        moveHoldToThePast(overdueOrderId, 1);
        long newOrderId = reserveSeats(CUSTOMER, newKey(), 1).orderId();

        orderClosingService.expireOverdueReservations();

        assertThat(statusOf(overdueOrderId)).isEqualTo(OrderStatus.EXPIRED);
        assertThat(seat(1).getOrder().getId()).isEqualTo(newOrderId);
    }

    @Test
    void cancel_pendingOrder_releasesItsPlacesAndCanBeRepeated() {
        long seatsOrderId = reserveSeats(CUSTOMER, newKey(), 1).orderId();
        long placesOrderId = reservePlaces(CUSTOMER, newKey(), 2).orderId();

        HttpResult cancelled = cancel(CUSTOMER, seatsOrderId);
        cancel(CUSTOMER, placesOrderId);
        HttpResult repeated = cancel(CUSTOMER, placesOrderId);

        assertThat(cancelled.status()).isEqualTo(200);
        assertThat(cancelled.body().get("status")).isEqualTo("CANCELLED");
        assertThat(cancelled.list("tickets")).allSatisfy(ticket -> assertThat(ticket.get("status")).isEqualTo("VOIDED"));
        assertThat(repeated.status()).isEqualTo(200);
        assertThat(seat(1).getOrder()).isNull();
        assertThat(stock().getTaken()).isZero();
    }

    @Test
    void cancel_orderOfAnotherCustomer_returnsNotFoundAndKeepsIt() {
        long orderId = reserveSeats(CUSTOMER, newKey(), 1).orderId();

        HttpResult cancelled = cancel(ANOTHER_CUSTOMER, orderId);

        assertThat(cancelled.status()).isEqualTo(404);
        assertThat(cancelled.error()).isEqualTo("order_not_found");
        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void cancel_orderWithPaymentInProgress_returnsConflictAndKeepsThePlaces() {
        long orderId = reserveSeats(CUSTOMER, newKey(), 1).orderId();
        changeOrder(orderId, order -> order.setPaymentAttemptKey(newKey()));

        assertThat(cancel(CUSTOMER, orderId).error()).isEqualTo("payment_in_progress");
        assertThat(seat(1).getOrder()).isNotNull();
    }

    @Test
    void cancel_expiredOrder_returnsReservationExpired() {
        long orderId = reserveSeats(CUSTOMER, newKey(), 1).orderId();
        changeOrder(orderId, order -> order.setStatus(OrderStatus.EXPIRED));

        assertThat(cancel(CUSTOMER, orderId).error()).isEqualTo("reservation_expired");
    }

    @Test
    void cancel_paidOrder_returnsNotCancellable() {
        long orderId = reserveSeats(CUSTOMER, newKey(), 1).orderId();
        changeOrder(orderId, order -> order.setStatus(OrderStatus.PAID));

        assertThat(cancel(CUSTOMER, orderId).error()).isEqualTo("order_not_cancellable");
    }

    private HttpResult synchronizeSale(String status, String saleStart, int price, int capacity, int maxTickets) {
        return client.put().uri("/internal/event-sales/{eventId}", eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"status": "%s", "eventName": "Recital", "venueName": "Estadio", "venueAddress": "Calle 1",
                         "venueCity": "Tandil", "dateTime": "2099-12-12T21:00:00", "saleStart": %s, "highDemand": false,
                         "maxTicketsPerCustomer": %d,
                         "sectors": [
                           {"eventSectorId": %d, "sectorName": "Platea", "price": %d, "seats": [%s]},
                           {"eventSectorId": %d, "sectorName": "Campo", "price": 500, "capacity": %d}
                         ]}""".formatted(status, saleStart, maxTickets, numberedSector, price, tenSeatsInRowA(),
                        generalSector, capacity))
                .exchange((request, response) -> new HttpResult(response.getStatusCode().value(), Map.of()));
    }

    private static String tenSeatsInRowA() {
        return String.join(", ", java.util.stream.IntStream.rangeClosed(1, 10)
                .mapToObj(seat -> """
                        {"seatId": %d, "row": "A", "number": %d}""".formatted(seat, seat))
                .toList());
    }

    private HttpResult reserveSeats(long customer, String key, long... seatIds) {
        String seats = String.join(", ", java.util.Arrays.stream(seatIds).mapToObj(Long::toString).toList());
        return post("/api/orders", customer, """
                {"eventId": %d, "idempotencyKey": "%s", "seatIds": [%s]}""".formatted(eventId, key, seats));
    }

    private HttpResult reservePlaces(long customer, String key, int quantity) {
        return post("/api/orders", customer, """
                {"eventId": %d, "idempotencyKey": "%s", "eventSectorId": %d, "quantity": %d}"""
                .formatted(eventId, key, generalSector, quantity));
    }

    private HttpResult cancel(long customer, long orderId) {
        return post("/api/orders/%d/cancel".formatted(orderId), customer, "");
    }

    private HttpResult post(String path, long customer, String body) {
        return client.post().uri(path)
                .header("X-User-Id", Long.toString(customer))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) ->
                        new HttpResult(response.getStatusCode().value(), response.bodyTo(JSON_OBJECT)));
    }

    private HttpResult get(String path) {
        return client.get().uri(path)
                .exchange((request, response) ->
                        new HttpResult(response.getStatusCode().value(), response.bodyTo(JSON_OBJECT)));
    }

    private Map<String, Object> availabilityOf(long sector) {
        return get("/api/availability?eventId=" + eventId).list("sectors").stream()
                .filter(availability -> ((Number) availability.get("eventSectorId")).longValue() == sector)
                .findFirst()
                .orElseThrow();
    }

    private List<HttpResult> runAtTheSameTime(java.util.function.IntFunction<HttpResult> attempt) throws Exception {
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(THREADS)) {
            List<Future<HttpResult>> attempts = IntStream.range(0, THREADS)
                    .mapToObj(number -> executor.submit(alignedAttempt(ready, start, number, attempt)))
                    .toList();

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<HttpResult> results = new java.util.ArrayList<>();
            for (Future<HttpResult> future : attempts) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        }
    }

    private static Callable<HttpResult> alignedAttempt(CountDownLatch ready, CountDownLatch start, int number,
                                                       java.util.function.IntFunction<HttpResult> attempt) {
        return () -> {
            ready.countDown();
            start.await();
            return attempt.apply(number);
        };
    }

    private void moveHoldToThePast(long orderId, long seatId) {
        LocalDateTime past = LocalDateTime.now().minusMinutes(1);
        changeOrder(orderId, order -> order.setExpiresAt(past));
        transaction.executeWithoutResult(status -> eventSeatRepository
                .findById(new EventSeatId(eventId, seatId)).orElseThrow().setReservedUntil(past));
    }

    private void changeOrder(long orderId, Consumer<Order> change) {
        transaction.executeWithoutResult(status -> change.accept(orderRepository.findById(orderId).orElseThrow()));
    }

    private OrderStatus statusOf(long orderId) {
        return orderRepository.findById(orderId).orElseThrow().getStatus();
    }

    private EventSeat seat(long seatId) {
        return transaction.execute(status -> {
            EventSeat seat = eventSeatRepository.findById(new EventSeatId(eventId, seatId)).orElseThrow();
            if (seat.getOrder() != null) {
                seat.getOrder().getId();
            }
            return seat;
        });
    }

    private EventSectorStock stock() {
        return eventSectorStockRepository.findById(generalSector).orElseThrow();
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }
}

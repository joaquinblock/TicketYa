package org.group10.ticket;

import org.group10.ticket.client.EventClient;
import org.group10.ticket.client.PaymentClient;
import org.group10.ticket.client.dto.RefundRequest;
import org.group10.ticket.entity.EventSaleStatus;
import org.group10.ticket.entity.EventSeatId;
import org.group10.ticket.entity.EventSeatStatus;
import org.group10.ticket.entity.ReturnRequestStatus;
import org.group10.ticket.entity.TicketStatus;
import org.group10.ticket.repository.EventSaleRepository;
import org.group10.ticket.repository.EventSeatRepository;
import org.group10.ticket.repository.EventSectorStockRepository;
import org.group10.ticket.repository.ReturnRequestRepository;
import org.group10.ticket.repository.TicketRepository;
import org.group10.ticket.service.SoldOutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
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
class ReturnRequestMySqlIntegrationTest {

    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis() + 500_000_000);
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
    };
    private static final LocalDateTime EVENT_DATE = LocalDateTime.of(2099, 12, 12, 21, 0);
    private static final int THREADS = 8;
    private static final long ADMINISTRATOR = 900L;
    private static final long ANOTHER_CUSTOMER = 802L;

    @Value("${local.server.port}")
    private int port;

    @MockitoBean
    private PaymentClient paymentClient;

    @MockitoBean
    private EventClient eventClient;

    @MockitoSpyBean
    private Clock clock;

    @Autowired
    private SoldOutService soldOutService;
    @Autowired
    private EventSaleRepository eventSaleRepository;
    @Autowired
    private EventSeatRepository eventSeatRepository;
    @Autowired
    private EventSectorStockRepository eventSectorStockRepository;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private ReturnRequestRepository returnRequestRepository;
    @Autowired
    private TransactionTemplate transaction;

    private RestClient client;
    private long customer;
    private long eventId;
    private long generalSector;

    private record HttpResult(int status, Map<String, Object> body) {

        String error() {
            return (String) body.get("error");
        }

        long id() {
            return ((Number) body.get("id")).longValue();
        }

        @SuppressWarnings("unchecked")
        List<Long> ticketIds() {
            return ((List<Map<String, Object>>) body.get("tickets")).stream()
                    .map(ticket -> ((Number) ticket.get("id")).longValue())
                    .toList();
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content() {
            return (List<Map<String, Object>>) body.get("content");
        }
    }

    @BeforeEach
    void publishEvent() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        customer = IDS.incrementAndGet();
        eventId = IDS.incrementAndGet();
        long numberedSector = IDS.incrementAndGet();
        generalSector = IDS.incrementAndGet();
        int status = client.put().uri("/internal/event-sales/{eventId}", eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"status": "PUBLISHED", "dateTime": "%s", "highDemand": false,
                         "sectors": [
                           {"eventSectorId": %d, "price": 1000, "seatIds": [1, 2]},
                           {"eventSectorId": %d, "price": 500, "capacity": 3}
                         ]}""".formatted(EVENT_DATE, numberedSector, generalSector))
                .exchange((request, response) -> response.getStatusCode().value());
        assertThat(status).isEqualTo(204);
    }

    @Test
    void requestReturn_issuedTicket_staysPendingAndTheTicketStaysIssued() {
        long ticketId = buySeats(1).getFirst();

        HttpResult result = requestReturn(customer, ticketId);

        assertThat(result.status()).isEqualTo(201);
        assertThat(result.body().get("status")).isEqualTo("PENDING");
        assertThat(((Number) result.body().get("eventId")).longValue()).isEqualTo(eventId);
        assertThat(((Number) result.body().get("requestedBy")).longValue()).isEqualTo(customer);
        assertThat(ticketStatus(ticketId)).isEqualTo(TicketStatus.ISSUED);
    }

    @ParameterizedTest
    @EnumSource(value = EventSaleStatus.class, names = {"PUBLISHED", "SOLD_OUT", "SUSPENDED"})
    void requestReturn_saleThatAcceptsReturns_isAccepted(EventSaleStatus status) {
        long ticketId = buySeats(1).getFirst();
        changeSaleStatus(status);

        assertThat(requestReturn(customer, ticketId).status()).isEqualTo(201);
    }

    @ParameterizedTest
    @EnumSource(value = EventSaleStatus.class, names = {"CANCELLED", "FINISHED"})
    void requestReturn_saleThatDoesNotAcceptReturns_returnsRefundNotAllowed(EventSaleStatus status) {
        long ticketId = buySeats(1).getFirst();
        changeSaleStatus(status);

        assertThat(requestReturn(customer, ticketId).error()).isEqualTo("refund_not_allowed");
    }

    @Test
    void requestReturn_exactly72HoursBefore_isAccepted() {
        long ticketId = buySeats(1).getFirst();
        clockAt(EVENT_DATE.minusHours(72));

        assertThat(requestReturn(customer, ticketId).status()).isEqualTo(201);
    }

    @Test
    void requestReturn_oneSecondAfterTheLimit_returnsRefundNotAllowed() {
        long ticketId = buySeats(1).getFirst();
        clockAt(EVENT_DATE.minusHours(72).plusSeconds(1));

        assertThat(requestReturn(customer, ticketId).error()).isEqualTo("refund_not_allowed");
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, names = {"USED", "VOIDED", "REFUNDED"})
    void requestReturn_ticketThatIsNotIssued_returnsRefundNotAllowed(TicketStatus status) {
        long ticketId = buySeats(1).getFirst();
        changeTicketStatus(ticketId, status);

        assertThat(requestReturn(customer, ticketId).error()).isEqualTo("refund_not_allowed");
    }

    @Test
    void requestReturn_reservedTicket_returnsRefundNotAllowed() {
        HttpResult order = reserveSeats(1);

        assertThat(requestReturn(customer, order.ticketIds().getFirst()).error()).isEqualTo("refund_not_allowed");
    }

    @Test
    void requestReturn_ticketOfAnotherCustomerOrUnknown_returnsTicketNotFound() {
        long ticketId = buySeats(1).getFirst();

        assertThat(requestReturn(ANOTHER_CUSTOMER, ticketId).error()).isEqualTo("ticket_not_found");
        assertThat(requestReturn(customer, Long.MAX_VALUE).error()).isEqualTo("ticket_not_found");
    }

    @Test
    void requestReturn_requestAlreadyPending_returns409() {
        long ticketId = buySeats(1).getFirst();
        requestReturn(customer, ticketId);

        HttpResult repeated = requestReturn(customer, ticketId);

        assertThat(repeated.status()).isEqualTo(409);
        assertThat(repeated.error()).isEqualTo("return_request_already_pending");
    }

    @Test
    void requestReturn_sameTicketAtTheSameTime_createsASingleRequest() throws Exception {
        long ticketId = buySeats(1).getFirst();

        List<HttpResult> results = runAtTheSameTime(attempt -> requestReturn(customer, ticketId));

        assertThat(results).filteredOn(result -> result.status() == 201).hasSize(1);
        assertThat(results).filteredOn(result -> result.status() != 201)
                .allSatisfy(result -> assertThat(result.error()).isEqualTo("return_request_already_pending"));
    }

    @Test
    void requestReturn_afterARejection_isAcceptedAgain() {
        long ticketId = buySeats(1).getFirst();
        reject(requestReturn(customer, ticketId).id());

        assertThat(requestReturn(customer, ticketId).status()).isEqualTo(201);
    }

    @Test
    void findByStatus_withoutStatus_showsPendingRequestsOldestFirst() {
        List<Long> tickets = buySeats(1, 2);
        long oldest = requestReturn(customer, tickets.getFirst()).id();
        long newest = requestReturn(customer, tickets.getLast()).id();
        long rejected = requestReturn(customer, buyPlaces(1).getFirst()).id();
        reject(rejected);

        HttpResult page = list("/api/return-requests?size=100");

        List<Long> ids = page.content().stream().map(request -> ((Number) request.get("id")).longValue()).toList();
        assertThat(ids).containsSubsequence(oldest, newest).doesNotContain(rejected);
    }

    @Test
    void findByStatus_withStatus_showsOnlyThatStatusAndPaginates() {
        List<Long> tickets = buySeats(1, 2);
        reject(requestReturn(customer, tickets.getFirst()).id());
        reject(requestReturn(customer, tickets.getLast()).id());

        HttpResult page = list("/api/return-requests?status=REJECTED&page=0&size=1");

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().get("status")).isEqualTo("REJECTED");
        assertThat(((Number) page.body().get("totalElements")).longValue()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void approve_seat_refundsThePriceReleasesTheSeatAndLeavesTheOtherTicketsAlone() {
        List<Long> tickets = buySeats(1, 2);
        long requestId = requestReturn(customer, tickets.getFirst()).id();

        HttpResult approved = approve(requestId);

        assertThat(approved.status()).isEqualTo(200);
        assertThat(approved.body().get("status")).isEqualTo("APPROVED");
        assertThat(((Number) approved.body().get("resolvedBy")).longValue()).isEqualTo(ADMINISTRATOR);
        assertThat(approved.body().get("resolvedAt")).isNotNull();
        assertThat(ticketStatus(tickets.getFirst())).isEqualTo(TicketStatus.REFUNDED);
        assertThat(ticketStatus(tickets.getLast())).isEqualTo(TicketStatus.ISSUED);
        assertThat(seatStatus(1)).isEqualTo(EventSeatStatus.AVAILABLE);
        assertThat(seatStatus(2)).isEqualTo(EventSeatStatus.SOLD);

        ArgumentCaptor<RefundRequest> refund = ArgumentCaptor.forClass(RefundRequest.class);
        verify(paymentClient).refund(refund.capture());
        assertThat(refund.getValue().ticketId()).isEqualTo(tickets.getFirst());
        assertThat(refund.getValue().amount()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(refund.getValue().reason()).isEqualTo("RETURN_REQUEST");
        assertThat(refund.getValue().returnRequestId()).isEqualTo(requestId);
    }

    @Test
    void approve_placeWithoutSeat_releasesOnePlaceOfTheSector() {
        List<Long> tickets = buyPlaces(2);
        long requestId = requestReturn(customer, tickets.getFirst()).id();

        approve(requestId);

        assertThat(placesTaken()).isEqualTo(1);
    }

    @Test
    void approve_soldOutEvent_putsTheSaleBackOnSaleAndTheJobNotifiesIt() {
        List<Long> tickets = buySeats(1, 2);
        buyPlaces(3);
        assertThat(saleStatus()).isEqualTo(EventSaleStatus.SOLD_OUT);
        soldOutService.notifyPendingAvailabilityChanges();
        long requestId = requestReturn(customer, tickets.getFirst()).id();

        approve(requestId);

        assertThat(saleStatus()).isEqualTo(EventSaleStatus.PUBLISHED);
        soldOutService.notifyPendingAvailabilityChanges();
        soldOutService.notifyPendingAvailabilityChanges();
        verify(eventClient, times(1)).markEventBackOnSale(eventId);
        assertThat(reserveSeats(1).status()).isEqualTo(201);
    }

    @Test
    void notifyPendingAvailabilityChanges_saleChangesWhileNotifying_keepsTheNewChangePending() {
        buySeats(1, 2);
        buyPlaces(3);
        doAnswer(invocation -> {
            transaction.executeWithoutResult(status -> eventSaleRepository.markSoldOutSaleAsPublished(eventId));
            return null;
        }).when(eventClient).markEventSoldOut(eventId);

        soldOutService.notifyPendingAvailabilityChanges();

        assertThat(eventSaleRepository.findById(eventId).orElseThrow().isAvailabilityNotificationPending()).isTrue();
        soldOutService.notifyPendingAvailabilityChanges();
        verify(eventClient).markEventBackOnSale(eventId);
    }

    @Test
    void approve_unknownRequest_returnsNotFound() {
        assertThat(approve(Long.MAX_VALUE).error()).isEqualTo("return_request_not_found");
    }

    @Test
    void approve_alreadyResolved_returnsRefundNotAllowedWithoutRefunding() {
        long requestId = requestReturn(customer, buySeats(1).getFirst()).id();
        reject(requestId);

        assertThat(approve(requestId).error()).isEqualTo("refund_not_allowed");
        verify(paymentClient, never()).refund(any());
    }

    @Test
    void approve_usedTicket_returnsAlreadyUsedWithoutRefunding() {
        long ticketId = buySeats(1).getFirst();
        long requestId = requestReturn(customer, ticketId).id();
        changeTicketStatus(ticketId, TicketStatus.USED);

        assertThat(approve(requestId).error()).isEqualTo("ticket_already_used");
        verify(paymentClient, never()).refund(any());
        assertThat(requestStatus(requestId)).isEqualTo(ReturnRequestStatus.PENDING);
    }

    @Test
    void approve_paymentServiceDown_answers503AndTheRetryCompletesIt() {
        long ticketId = buySeats(1).getFirst();
        long requestId = requestReturn(customer, ticketId).id();
        doThrow(new ResourceAccessException("payment-service caído")).when(paymentClient).refund(any());

        HttpResult whileDown = approve(requestId);

        assertThat(whileDown.status()).isEqualTo(503);
        assertThat(requestStatus(requestId)).isEqualTo(ReturnRequestStatus.PENDING);
        assertThat(ticketStatus(ticketId)).isEqualTo(TicketStatus.ISSUED);
        assertThat(seatStatus(1)).isEqualTo(EventSeatStatus.SOLD);

        doNothing().when(paymentClient).refund(any());
        assertThat(approve(requestId).status()).isEqualTo(200);
        assertThat(ticketStatus(ticketId)).isEqualTo(TicketStatus.REFUNDED);
    }

    @Test
    void approve_atTheSameTime_releasesThePlaceOnlyOnce() throws Exception {
        List<Long> tickets = buyPlaces(2);
        long requestId = requestReturn(customer, tickets.getFirst()).id();

        List<HttpResult> results = runAtTheSameTime(attempt -> approve(requestId));

        assertThat(results).extracting(HttpResult::status).contains(200);
        assertThat(results).filteredOn(result -> result.status() != 200)
                .allSatisfy(result -> assertThat(result.error()).isEqualTo("refund_not_allowed"));
        assertThat(placesTaken()).isEqualTo(1);
        assertThat(ticketStatus(tickets.getLast())).isEqualTo(TicketStatus.ISSUED);
    }

    @Test
    void approve_rejectedWhileRefunding_keepsTheTicketRefundedBecauseTheMoneyWasReturned() {
        long ticketId = buySeats(1).getFirst();
        long requestId = requestReturn(customer, ticketId).id();
        doAnswer(invocation -> {
            reject(requestId);
            return null;
        }).when(paymentClient).refund(any());

        HttpResult result = approve(requestId);

        assertThat(result.error()).isEqualTo("refund_not_allowed");
        assertThat(ticketStatus(ticketId)).isEqualTo(TicketStatus.REFUNDED);
        assertThat(seatStatus(1)).isEqualTo(EventSeatStatus.AVAILABLE);
    }

    @Test
    void reject_pendingRequest_keepsTheTicketAndThePlaceWithoutRefunding() {
        long ticketId = buySeats(1).getFirst();
        long requestId = requestReturn(customer, ticketId).id();

        HttpResult rejected = reject(requestId);

        assertThat(rejected.status()).isEqualTo(200);
        assertThat(rejected.body().get("status")).isEqualTo("REJECTED");
        assertThat(((Number) rejected.body().get("resolvedBy")).longValue()).isEqualTo(ADMINISTRATOR);
        assertThat(rejected.body().get("resolvedAt")).isNotNull();
        assertThat(ticketStatus(ticketId)).isEqualTo(TicketStatus.ISSUED);
        assertThat(seatStatus(1)).isEqualTo(EventSeatStatus.SOLD);
        verify(paymentClient, never()).refund(any());
    }

    @Test
    void reject_unknownRequest_returnsNotFound() {
        assertThat(reject(Long.MAX_VALUE).error()).isEqualTo("return_request_not_found");
    }

    @Test
    void reject_alreadyResolved_returnsRefundNotAllowed() {
        long requestId = requestReturn(customer, buySeats(1).getFirst()).id();
        approve(requestId);

        assertThat(reject(requestId).error()).isEqualTo("refund_not_allowed");
    }

    private List<Long> buySeats(long... seatIds) {
        return pay(reserveSeats(seatIds));
    }

    private List<Long> buyPlaces(int quantity) {
        return pay(post("/api/orders", customer, """
                {"eventId": %d, "idempotencyKey": "%s", "eventSectorId": %d, "quantity": %d}"""
                .formatted(eventId, newKey(), generalSector, quantity)));
    }

    private HttpResult reserveSeats(long... seatIds) {
        String seats = String.join(", ", Arrays.stream(seatIds).mapToObj(Long::toString).toList());
        return post("/api/orders", customer, """
                {"eventId": %d, "idempotencyKey": "%s", "seatIds": [%s]}""".formatted(eventId, newKey(), seats));
    }

    private List<Long> pay(HttpResult order) {
        HttpResult paid = post("/api/orders/%d/pay".formatted(order.id()), customer, """
                {"paymentMethod": "CREDIT_CARD", "idempotencyKey": "%s"}""".formatted(newKey()));
        assertThat(paid.status()).isEqualTo(200);
        return paid.ticketIds();
    }

    private HttpResult requestReturn(long user, long ticketId) {
        return post("/api/tickets/%d/return-requests".formatted(ticketId), user, "");
    }

    private HttpResult approve(long requestId) {
        return post("/api/return-requests/%d/approve".formatted(requestId), ADMINISTRATOR, "");
    }

    private HttpResult reject(long requestId) {
        return post("/api/return-requests/%d/reject".formatted(requestId), ADMINISTRATOR, "");
    }

    private HttpResult list(String path) {
        return client.get().uri(path).exchange((request, response) ->
                new HttpResult(response.getStatusCode().value(), response.bodyTo(JSON_OBJECT)));
    }

    private HttpResult post(String path, long user, String body) {
        return client.post().uri(path)
                .header("X-User-Id", Long.toString(user))
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

    private void clockAt(LocalDateTime moment) {
        doReturn(moment.atZone(clock.getZone()).toInstant()).when(clock).instant();
    }

    private void changeSaleStatus(EventSaleStatus status) {
        transaction.executeWithoutResult(tx -> eventSaleRepository.findById(eventId).orElseThrow().setStatus(status));
    }

    private void changeTicketStatus(long ticketId, TicketStatus status) {
        transaction.executeWithoutResult(tx -> ticketRepository.findById(ticketId).orElseThrow().setStatus(status));
    }

    private TicketStatus ticketStatus(long ticketId) {
        return ticketRepository.findById(ticketId).orElseThrow().getStatus();
    }

    private ReturnRequestStatus requestStatus(long requestId) {
        return returnRequestRepository.findById(requestId).orElseThrow().getStatus();
    }

    private EventSaleStatus saleStatus() {
        return eventSaleRepository.findById(eventId).orElseThrow().getStatus();
    }

    private EventSeatStatus seatStatus(long seatId) {
        return eventSeatRepository.findById(new EventSeatId(eventId, seatId)).orElseThrow().getStatus();
    }

    private int placesTaken() {
        return eventSectorStockRepository.findById(generalSector).orElseThrow().getTaken();
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }
}

package org.group10.ticket;

import org.group10.ticket.client.EventClient;
import org.group10.ticket.client.PaymentClient;
import org.group10.ticket.client.dto.RefundRequest;
import org.group10.ticket.entity.EventSeatId;
import org.group10.ticket.entity.EventSeatStatus;
import org.group10.ticket.entity.OrderStatus;
import org.group10.ticket.entity.ReturnRequestStatus;
import org.group10.ticket.entity.TicketStatus;
import org.group10.ticket.repository.EventSeatRepository;
import org.group10.ticket.repository.EventSectorStockRepository;
import org.group10.ticket.repository.OrderRepository;
import org.group10.ticket.repository.ReturnRequestRepository;
import org.group10.ticket.repository.TicketRepository;
import org.group10.ticket.service.EventCancellationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"ticketya.reservation.expiration-check=PT1H", "ticketya.cancellation.refund-check=PT1H"})
@ActiveProfiles("test")
class EventCancellationMySqlIntegrationTest {

    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis() + 900_000_000);
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
    };
    private static final long ADMINISTRATOR = 900L;

    @Value("${local.server.port}")
    private int port;

    @MockitoBean
    private PaymentClient paymentClient;

    @MockitoBean
    private EventClient eventClient;

    @Autowired
    private EventCancellationService eventCancellationService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private ReturnRequestRepository returnRequestRepository;
    @Autowired
    private EventSeatRepository eventSeatRepository;
    @Autowired
    private EventSectorStockRepository eventSectorStockRepository;
    @Autowired
    private TransactionTemplate transaction;

    private RestClient client;
    private long customer;
    private long eventId;
    private long numberedSector;
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
    }

    @BeforeEach
    void publishEvent() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        customer = IDS.incrementAndGet();
        eventId = IDS.incrementAndGet();
        numberedSector = IDS.incrementAndGet();
        generalSector = IDS.incrementAndGet();
        assertThat(synchronizeSale(eventId, "PUBLISHED")).isEqualTo(204);
    }

    @Test
    void refundTicketsOfCancelledEvents_issuedAndUsedTickets_refundsEachOneAtItsPrice() {
        List<Long> seats = buySeats(1, 2);
        changeTicketStatus(seats.getLast(), TicketStatus.USED);
        cancelEvent();

        eventCancellationService.refundTicketsOfCancelledEvents();

        assertThat(ticketStatus(seats.getFirst())).isEqualTo(TicketStatus.REFUNDED);
        assertThat(ticketStatus(seats.getLast())).isEqualTo(TicketStatus.REFUNDED);
        ArgumentCaptor<RefundRequest> refunds = ArgumentCaptor.forClass(RefundRequest.class);
        verify(paymentClient, atLeast(2)).refund(refunds.capture());
        List<RefundRequest> refundsOfTheEvent = refunds.getAllValues().stream()
                .filter(refund -> seats.contains(refund.ticketId()))
                .toList();
        assertThat(refundsOfTheEvent).extracting(RefundRequest::ticketId).containsExactlyInAnyOrderElementsOf(seats);
        assertThat(refundsOfTheEvent).allSatisfy(refund -> {
            assertThat(refund.reason()).isEqualTo("EVENT_CANCELLED");
            assertThat(refund.returnRequestId()).isNull();
            assertThat(refund.amount()).isEqualByComparingTo(new BigDecimal("1000"));
        });
    }

    @Test
    void synchronizeEventSale_cancelled_closesPendingOrdersAndReleasesTheirPlaces() {
        HttpResult seats = reserveSeats(1);
        HttpResult places = reservePlaces(2);

        cancelEvent();

        assertThat(orderStatus(seats.id())).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orderStatus(places.id())).isEqualTo(OrderStatus.CANCELLED);
        assertThat(ticketStatus(seats.ticketIds().getFirst())).isEqualTo(TicketStatus.VOIDED);
        assertThat(ticketStatus(places.ticketIds().getFirst())).isEqualTo(TicketStatus.VOIDED);
        assertThat(seatStatus(1)).isEqualTo(EventSeatStatus.AVAILABLE);
        assertThat(eventSectorStockRepository.findById(generalSector).orElseThrow().getTaken()).isZero();
        eventCancellationService.refundTicketsOfCancelledEvents();
        verify(paymentClient, never()).refund(argThat(refund -> refund.orderId().equals(seats.id())
                || refund.orderId().equals(places.id())));
    }

    @Test
    void synchronizeEventSale_cancelled_cancelsPendingReturnRequestsAndKeepsTheResolvedOnes() {
        List<Long> tickets = buySeats(1, 2);
        long pending = requestReturn(tickets.getFirst());
        long rejected = requestReturn(tickets.getLast());
        post("/api/return-requests/%d/reject".formatted(rejected), ADMINISTRATOR, "");

        cancelEvent();

        assertThat(requestStatus(pending)).isEqualTo(ReturnRequestStatus.CANCELLED);
        assertThat(requestStatus(rejected)).isEqualTo(ReturnRequestStatus.REJECTED);
    }

    @Test
    void refundTicketsOfCancelledEvents_ticketAlreadyRefunded_doesNotRefundItAgain() {
        List<Long> tickets = buySeats(1, 2);
        post("/api/return-requests/%d/approve".formatted(requestReturn(tickets.getFirst())), ADMINISTRATOR, "");
        cancelEvent();

        eventCancellationService.refundTicketsOfCancelledEvents();

        verify(paymentClient, times(1)).refund(argThat(refund -> refund.ticketId().equals(tickets.getFirst())));
        verify(paymentClient).refund(argThat(refund -> refund.ticketId().equals(tickets.getFirst())
                && refund.reason().equals("RETURN_REQUEST")));
        verify(paymentClient).refund(argThat(refund -> refund.ticketId().equals(tickets.getLast())
                && refund.reason().equals("EVENT_CANCELLED")));
    }

    @Test
    void refundTicketsOfCancelledEvents_cancellationInformedTwice_refundsEachTicketOnce() {
        long ticketId = buySeats(1).getFirst();
        cancelEvent();
        eventCancellationService.refundTicketsOfCancelledEvents();

        cancelEvent();
        eventCancellationService.refundTicketsOfCancelledEvents();

        verify(paymentClient, times(1)).refund(argThat(refund -> refund.ticketId().equals(ticketId)));
    }

    @Test
    void refundTicketsOfCancelledEvents_oneRefundFails_refundsTheOthersAndTheNextRunCompletesIt() {
        List<Long> tickets = buySeats(1, 2);
        doThrow(new ResourceAccessException("payment-service caído")).when(paymentClient)
                .refund(argThat(refund -> refund.ticketId().equals(tickets.getFirst())));
        cancelEvent();

        eventCancellationService.refundTicketsOfCancelledEvents();

        assertThat(ticketStatus(tickets.getFirst())).isEqualTo(TicketStatus.ISSUED);
        assertThat(ticketStatus(tickets.getLast())).isEqualTo(TicketStatus.REFUNDED);

        doNothing().when(paymentClient).refund(any());
        eventCancellationService.refundTicketsOfCancelledEvents();

        assertThat(ticketStatus(tickets.getFirst())).isEqualTo(TicketStatus.REFUNDED);
    }

    @Test
    void pay_eventCancelledWhileCharging_issuesNothingAndCompensatesThePayment() {
        HttpResult order = reserveSeats(1);
        doAnswer(invocation -> {
            cancelEvent();
            return null;
        }).when(paymentClient).charge(any());

        HttpResult result = pay(order.id());

        assertThat(result.status()).isEqualTo(409);
        assertThat(result.error()).isEqualTo("order_not_payable");
        assertThat(orderStatus(order.id())).isEqualTo(OrderStatus.CANCELLED);
        assertThat(ticketStatus(order.ticketIds().getFirst())).isEqualTo(TicketStatus.VOIDED);
        ArgumentCaptor<RefundRequest> refund = ArgumentCaptor.forClass(RefundRequest.class);
        verify(paymentClient).refund(refund.capture());
        assertThat(refund.getValue().reason()).isEqualTo("PURCHASE_COMPENSATION");
        assertThat(refund.getValue().orderId()).isEqualTo(order.id());
    }

    @Test
    void refundTicketsOfCancelledEvents_ticketsOfAnotherPublishedEvent_areNotTouched() {
        long ticketOfThisEvent = buySeats(1).getFirst();

        eventCancellationService.refundTicketsOfCancelledEvents();

        assertThat(ticketStatus(ticketOfThisEvent)).isEqualTo(TicketStatus.ISSUED);
        verify(paymentClient, never()).refund(argThat(refund -> refund.ticketId().equals(ticketOfThisEvent)));
    }

    private void cancelEvent() {
        assertThat(synchronizeSale(eventId, "CANCELLED")).isEqualTo(204);
    }

    private int synchronizeSale(long event, String status) {
        return client.put().uri("/internal/event-sales/{eventId}", event)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"status": "%s", "dateTime": "2099-12-12T21:00:00", "highDemand": false,
                         "sectors": [
                           {"eventSectorId": %d, "price": 1000, "seatIds": [1, 2]},
                           {"eventSectorId": %d, "price": 500, "capacity": 3}
                         ]}""".formatted(status, numberedSector, generalSector))
                .exchange((request, response) -> response.getStatusCode().value());
    }

    private List<Long> buySeats(long... seatIds) {
        HttpResult paid = pay(reserveSeats(seatIds).id());
        assertThat(paid.status()).isEqualTo(200);
        return paid.ticketIds();
    }

    private HttpResult reserveSeats(long... seatIds) {
        String seats = String.join(", ", Arrays.stream(seatIds).mapToObj(Long::toString).toList());
        return post("/api/orders", customer, """
                {"eventId": %d, "idempotencyKey": "%s", "seatIds": [%s]}""".formatted(eventId, newKey(), seats));
    }

    private HttpResult reservePlaces(int quantity) {
        return post("/api/orders", customer, """
                {"eventId": %d, "idempotencyKey": "%s", "eventSectorId": %d, "quantity": %d}"""
                .formatted(eventId, newKey(), generalSector, quantity));
    }

    private HttpResult pay(long orderId) {
        return post("/api/orders/%d/pay".formatted(orderId), customer, """
                {"paymentMethod": "CREDIT_CARD", "idempotencyKey": "%s"}""".formatted(newKey()));
    }

    private long requestReturn(long ticketId) {
        return post("/api/tickets/%d/return-requests".formatted(ticketId), customer, "").id();
    }

    private HttpResult post(String path, long user, String body) {
        return client.post().uri(path)
                .header("X-User-Id", Long.toString(user))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) ->
                        new HttpResult(response.getStatusCode().value(), response.bodyTo(JSON_OBJECT)));
    }

    private void changeTicketStatus(long ticketId, TicketStatus status) {
        transaction.executeWithoutResult(tx -> ticketRepository.findById(ticketId).orElseThrow().setStatus(status));
    }

    private TicketStatus ticketStatus(long ticketId) {
        return ticketRepository.findById(ticketId).orElseThrow().getStatus();
    }

    private OrderStatus orderStatus(long orderId) {
        return orderRepository.findById(orderId).orElseThrow().getStatus();
    }

    private ReturnRequestStatus requestStatus(long requestId) {
        return returnRequestRepository.findById(requestId).orElseThrow().getStatus();
    }

    private EventSeatStatus seatStatus(long seatId) {
        return eventSeatRepository.findById(new EventSeatId(eventId, seatId)).orElseThrow().getStatus();
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }
}

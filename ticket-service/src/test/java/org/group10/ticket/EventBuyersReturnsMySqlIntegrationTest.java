package org.group10.ticket;

import org.group10.ticket.entity.Order;
import org.group10.ticket.entity.OrderStatus;
import org.group10.ticket.entity.ReturnRequest;
import org.group10.ticket.entity.ReturnRequestStatus;
import org.group10.ticket.entity.Ticket;
import org.group10.ticket.entity.TicketStatus;
import org.group10.ticket.repository.OrderRepository;
import org.group10.ticket.repository.ReturnRequestRepository;
import org.group10.ticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class EventBuyersReturnsMySqlIntegrationTest {

    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis() + 2_100_000_000);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ReturnRequestRepository returnRequestRepository;

    private long eventId;

    @BeforeEach
    void setUp() {
        eventId = IDS.incrementAndGet();
    }

    @Test
    void findDistinctUserIdsOfPaidOrdersOfEvent_allTicketsReturned_buyerIsLeftOut() {
        Order order = addPaidOrder(10L);
        addReturnedTicket(order, ReturnRequestStatus.APPROVED);
        addReturnedTicket(order, ReturnRequestStatus.APPROVED);
        addTicket(addPaidOrder(20L), TicketStatus.ISSUED);

        assertThat(orderRepository.findDistinctUserIdsOfPaidOrdersOfEvent(eventId)).containsExactly(20L);
    }

    @Test
    void findDistinctUserIdsOfPaidOrdersOfEvent_oneOfTwoTicketsReturned_buyerStays() {
        Order order = addPaidOrder(10L);
        addReturnedTicket(order, ReturnRequestStatus.APPROVED);
        addTicket(order, TicketStatus.ISSUED);

        assertThat(orderRepository.findDistinctUserIdsOfPaidOrdersOfEvent(eventId)).containsExactly(10L);
    }

    @Test
    void findDistinctUserIdsOfPaidOrdersOfEvent_returnedOrderButAnotherOrderValid_buyerStays() {
        addReturnedTicket(addPaidOrder(10L), ReturnRequestStatus.APPROVED);
        addTicket(addPaidOrder(10L), TicketStatus.USED);

        assertThat(orderRepository.findDistinctUserIdsOfPaidOrdersOfEvent(eventId)).containsExactly(10L);
    }

    @ParameterizedTest
    @EnumSource(value = ReturnRequestStatus.class, names = "APPROVED", mode = EnumSource.Mode.EXCLUDE)
    void findDistinctUserIdsOfPaidOrdersOfEvent_returnNotApproved_buyerStays(ReturnRequestStatus status) {
        addReturnedTicket(addPaidOrder(10L), status);

        assertThat(orderRepository.findDistinctUserIdsOfPaidOrdersOfEvent(eventId)).containsExactly(10L);
    }

    @Test
    void findDistinctUserIdsOfPaidOrdersOfEvent_ticketsRefundedByCancellation_buyerStays() {
        Order order = addPaidOrder(10L);
        addTicket(order, TicketStatus.REFUNDED);
        addTicket(order, TicketStatus.REFUNDED);

        assertThat(orderRepository.findDistinctUserIdsOfPaidOrdersOfEvent(eventId)).containsExactly(10L);
    }

    private Order addPaidOrder(long userId) {
        return orderRepository.saveAndFlush(Order.builder()
                .userId(userId)
                .eventId(eventId)
                .status(OrderStatus.PAID)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .total(new BigDecimal("200.00"))
                .idempotencyKey(UUID.randomUUID().toString())
                .build());
    }

    private Ticket addTicket(Order order, TicketStatus status) {
        return ticketRepository.saveAndFlush(Ticket.builder()
                .order(order)
                .eventSectorId(IDS.incrementAndGet())
                .sectorName("Platea")
                .price(new BigDecimal("100.00"))
                .qrCode(UUID.randomUUID().toString())
                .status(status)
                .build());
    }

    private void addReturnedTicket(Order order, ReturnRequestStatus status) {
        TicketStatus ticketStatus = status == ReturnRequestStatus.APPROVED ? TicketStatus.REFUNDED : TicketStatus.ISSUED;
        Ticket ticket = addTicket(order, ticketStatus);
        returnRequestRepository.saveAndFlush(ReturnRequest.builder()
                .ticket(ticket)
                .requestedBy(order.getUserId())
                .requestedAt(LocalDateTime.now())
                .status(status)
                .build());
    }
}

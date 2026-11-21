package org.group10.ticket;

import org.group10.ticket.entity.Order;
import org.group10.ticket.entity.OrderStatus;
import org.group10.ticket.entity.Ticket;
import org.group10.ticket.entity.TicketStatus;
import org.group10.ticket.repository.EventSectorReportAggregate;
import org.group10.ticket.repository.OrderRepository;
import org.group10.ticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class EventReportMySqlIntegrationTest {

    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis() + 1_800_000_000);

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private TicketRepository ticketRepository;

    private long eventId;
    private long sectorId;

    @BeforeEach
    void setUp() {
        eventId = IDS.incrementAndGet();
        sectorId = IDS.incrementAndGet();
    }

    @Test
    void findReportAggregatesByEventId_countsSoldAndRefundedButExcludesReservationsAndVoids() {
        addTicket(TicketStatus.ISSUED, new BigDecimal("100.00"));
        addTicket(TicketStatus.USED, new BigDecimal("250.00"));
        addTicket(TicketStatus.REFUNDED, new BigDecimal("75.00"));
        addTicket(TicketStatus.RESERVED, new BigDecimal("500.00"));
        addTicket(TicketStatus.VOIDED, new BigDecimal("90.00"));

        assertThat(ticketRepository.findReportAggregatesByEventId(eventId))
                .containsExactly(new EventSectorReportAggregate(sectorId, 2L, 1L, new BigDecimal("350.00")));
    }

    @Test
    void findReportAggregatesByEventId_refundedOnlySectorHasNoRevenue() {
        addTicket(TicketStatus.REFUNDED, new BigDecimal("75.00"));

        assertThat(ticketRepository.findReportAggregatesByEventId(eventId))
                .containsExactly(new EventSectorReportAggregate(sectorId, 0L, 1L, null));
    }

    private void addTicket(TicketStatus ticketStatus, BigDecimal price) {
        Order order = orderRepository.save(Order.builder()
                .userId(10L)
                .eventId(eventId)
                .status(ticketStatus == TicketStatus.RESERVED ? OrderStatus.PENDING : OrderStatus.PAID)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .total(price)
                .idempotencyKey(UUID.randomUUID().toString())
                .build());
        ticketRepository.save(Ticket.builder()
                .order(order)
                .eventSectorId(sectorId)
                .sectorName("Platea")
                .price(price)
                .status(ticketStatus)
                .build());
    }
}

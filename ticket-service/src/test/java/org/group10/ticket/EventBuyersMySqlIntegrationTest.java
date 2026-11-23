package org.group10.ticket;

import org.group10.ticket.entity.Order;
import org.group10.ticket.entity.OrderStatus;
import org.group10.ticket.repository.OrderRepository;
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
class EventBuyersMySqlIntegrationTest {

    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis() + 1_900_000_000);

    @Autowired
    private OrderRepository orderRepository;

    private long eventId;
    private long otherEventId;

    @BeforeEach
    void setUp() {
        eventId = IDS.incrementAndGet();
        otherEventId = IDS.incrementAndGet();
    }

    @Test
    void findDistinctUserIdsOfPaidOrdersOfEvent_buyerWithSeveralOrders_appearsOnce() {
        addOrder(eventId, 20L, OrderStatus.PAID);
        addOrder(eventId, 20L, OrderStatus.PAID);
        addOrder(eventId, 10L, OrderStatus.PAID);

        assertThat(orderRepository.findDistinctUserIdsOfPaidOrdersOfEvent(eventId)).containsExactly(10L, 20L);
    }

    @Test
    void findDistinctUserIdsOfPaidOrdersOfEvent_ordersNotPaid_areLeftOut() {
        addOrder(eventId, 10L, OrderStatus.PAID);
        for (OrderStatus status : OrderStatus.values()) {
            if (status != OrderStatus.PAID) {
                addOrder(eventId, 30L + status.ordinal(), status);
            }
        }

        assertThat(orderRepository.findDistinctUserIdsOfPaidOrdersOfEvent(eventId)).containsExactly(10L);
    }

    @Test
    void findDistinctUserIdsOfPaidOrdersOfEvent_buyersOfAnotherEvent_areLeftOut() {
        addOrder(eventId, 10L, OrderStatus.PAID);
        addOrder(otherEventId, 20L, OrderStatus.PAID);

        assertThat(orderRepository.findDistinctUserIdsOfPaidOrdersOfEvent(eventId)).containsExactly(10L);
    }

    @Test
    void findDistinctUserIdsOfPaidOrdersOfEvent_eventWithoutOrders_isEmpty() {
        assertThat(orderRepository.findDistinctUserIdsOfPaidOrdersOfEvent(eventId)).isEmpty();
    }

    private void addOrder(long event, long userId, OrderStatus status) {
        orderRepository.saveAndFlush(Order.builder()
                .userId(userId)
                .eventId(event)
                .status(status)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .total(new BigDecimal("100.00"))
                .idempotencyKey(UUID.randomUUID().toString())
                .build());
    }
}

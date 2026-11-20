package org.group10.ticket.service;

import org.group10.common.exception.TicketYaException;
import org.group10.ticket.dto.OrderResponse;
import org.group10.ticket.entity.EventSale;
import org.group10.ticket.entity.Order;
import org.group10.ticket.entity.OrderStatus;
import org.group10.ticket.entity.Ticket;
import org.group10.ticket.entity.TicketStatus;
import org.group10.ticket.exception.TicketErrorCode;
import org.group10.ticket.repository.EventSaleRepository;
import org.group10.ticket.repository.OrderRepository;
import org.group10.ticket.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private EventSaleRepository eventSaleRepository;

    @InjectMocks
    private OrderQueryService orderQueryService;

    @Test
    void findPaidOrderDetail_paidOrder_returnsTheEventAndTheTicketsWithTheirQr() {
        Order order = order(OrderStatus.PAID);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(ticketRepository.findByOrderIdOrderById(10L)).thenReturn(List.of(
                ticket(order, 1L, TicketStatus.ISSUED, "qr-1"),
                ticket(order, 2L, TicketStatus.REFUNDED, "qr-2")));
        when(eventSaleRepository.findById(1L)).thenReturn(Optional.of(sale()));

        OrderResponse response = orderQueryService.findPaidOrderDetail(10L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.event().name()).isEqualTo("Los Serranos en vivo");
        assertThat(response.event().venueCity()).isEqualTo("Buenos Aires");
        assertThat(response.tickets()).extracting("qrCode").containsExactly("qr-1", null);
    }

    @Test
    void findPaidOrderDetail_missingOrder_throwsOrderNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderQueryService.findPaidOrderDetail(99L))
                .isInstanceOf(TicketYaException.class)
                .extracting("errorCode").isEqualTo(TicketErrorCode.ORDER_NOT_FOUND);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "PAID", mode = EnumSource.Mode.EXCLUDE)
    void findPaidOrderDetail_orderNotPaid_throwsOrderNotPaidWithoutLoadingTheTickets(OrderStatus status) {
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order(status)));

        assertThatThrownBy(() -> orderQueryService.findPaidOrderDetail(10L))
                .isInstanceOf(TicketYaException.class)
                .extracting("errorCode").isEqualTo(TicketErrorCode.ORDER_NOT_PAID);
        verifyNoInteractions(ticketRepository, eventSaleRepository);
    }

    private static Order order(OrderStatus status) {
        return Order.builder().id(10L).userId(1L).eventId(1L).status(status).total(new BigDecimal("140000.00"))
                .build();
    }

    private static Ticket ticket(Order order, Long id, TicketStatus status, String qrCode) {
        return Ticket.builder().id(id).order(order).eventSectorId(2L).sectorName("Platea")
                .price(new BigDecimal("70000.00")).status(status).qrCode(qrCode).build();
    }

    private static EventSale sale() {
        return EventSale.builder().eventId(1L).eventName("Los Serranos en vivo").venueName("Estadio del Sur")
                .venueAddress("Av. del Estadio 1000").venueCity("Buenos Aires")
                .dateTime(LocalDateTime.of(2026, 12, 13, 21, 0)).build();
    }
}

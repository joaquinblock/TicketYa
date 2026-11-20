package org.group10.ticket.controller;

import org.group10.common.exception.TicketYaException;
import org.group10.ticket.dto.OrderResponse;
import org.group10.ticket.dto.OrderResponse.EventOfOrder;
import org.group10.ticket.dto.TicketResponse;
import org.group10.ticket.entity.OrderStatus;
import org.group10.ticket.entity.PaymentMethod;
import org.group10.ticket.entity.TicketStatus;
import org.group10.ticket.exception.TicketErrorCode;
import org.group10.ticket.service.OrderQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La orden pagada que pide el notification-service para el mail: solo con el secreto entre servicios.
 */
@WebMvcTest(InternalOrderController.class)
class InternalOrderControllerTest {

    private static final String SECRET = "dev-internal-secret";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderQueryService orderQueryService;

    @Test
    void findPaidOrder_withTheSecret_returns200WithTheEventAndTheQrOfEachTicket() throws Exception {
        when(orderQueryService.findPaidOrderDetail(10L)).thenReturn(paidOrder());

        mockMvc.perform(get("/internal/orders/10").header("X-Internal-Secret", SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.event.name").value("Los Serranos en vivo"))
                .andExpect(jsonPath("$.tickets[0].qrCode").value("3f1c2d3e-4b5a-4c6d-8e7f-0a1b2c3d4e5f"))
                .andExpect(jsonPath("$.tickets[0].row").value("A"));
    }

    @Test
    void findPaidOrder_withoutTheSecret_returns401WithoutLookingUpTheOrder() throws Exception {
        mockMvc.perform(get("/internal/orders/10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        verifyNoInteractions(orderQueryService);
    }

    @Test
    void findPaidOrder_customerHeadersAreNotEnough_returns401() throws Exception {
        mockMvc.perform(get("/internal/orders/10").header("X-User-Id", 1)
                        .header("X-User-Permissions", "TICKET_PURCHASE"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(orderQueryService);
    }

    @Test
    void findPaidOrder_missingOrder_returns404() throws Exception {
        when(orderQueryService.findPaidOrderDetail(99L)).thenThrow(new TicketYaException(
                TicketErrorCode.ORDER_NOT_FOUND, "No se encontró la orden con id 99"));

        mockMvc.perform(get("/internal/orders/99").header("X-Internal-Secret", SECRET))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("order_not_found"));
    }

    @Test
    void findPaidOrder_orderNotPaid_returns409() throws Exception {
        when(orderQueryService.findPaidOrderDetail(10L)).thenThrow(new TicketYaException(
                TicketErrorCode.ORDER_NOT_PAID, "La orden 10 no está pagada: está PENDING"));

        mockMvc.perform(get("/internal/orders/10").header("X-Internal-Secret", SECRET))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("order_not_paid"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void findPaidOrder_nonPositiveId_returns400(String id) throws Exception {
        mockMvc.perform(get("/internal/orders/" + id).header("X-Internal-Secret", SECRET))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
        verifyNoInteractions(orderQueryService);
    }

    private static OrderResponse paidOrder() {
        EventOfOrder event = new EventOfOrder("Los Serranos en vivo", LocalDateTime.of(2026, 12, 13, 21, 0),
                "Estadio del Sur", "Av. del Estadio 1000", "Buenos Aires");
        TicketResponse ticket = new TicketResponse(1L, 2L, "Platea", 15L, "A", 15, new BigDecimal("70000.00"),
                TicketStatus.ISSUED, "3f1c2d3e-4b5a-4c6d-8e7f-0a1b2c3d4e5f");
        return new OrderResponse(10L, 1L, event, OrderStatus.PAID, null, new BigDecimal("70000.00"),
                PaymentMethod.CREDIT_CARD, List.of(ticket));
    }
}

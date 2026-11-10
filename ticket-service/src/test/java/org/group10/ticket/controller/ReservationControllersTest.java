package org.group10.ticket.controller;

import org.group10.common.exception.TicketYaException;
import org.group10.ticket.dto.OrderResponse;
import org.group10.ticket.entity.OrderStatus;
import org.group10.ticket.exception.TicketErrorCode;
import org.group10.ticket.service.AvailabilityService;
import org.group10.ticket.service.EventSaleService;
import org.group10.ticket.service.OrderClosingService;
import org.group10.ticket.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({OrderController.class, AvailabilityController.class, InternalEventSaleController.class})
class ReservationControllersTest {

    private static final String USER_HEADER = "X-User-Id";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;

    @MockitoBean
    private OrderClosingService orderClosingService;

    @MockitoBean
    private AvailabilityService availabilityService;

    @MockitoBean
    private EventSaleService eventSaleService;

    @Test
    void reserve_validSeats_returnsCreatedOrder() throws Exception {
        when(reservationService.reserve(eq(7L), any())).thenReturn(pendingOrder());

        mockMvc.perform(post("/api/orders").header(USER_HEADER, 7)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId": 1, "idempotencyKey": "key-1", "seatIds": [1, 2]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void reserve_validSectorPlaces_returnsCreatedOrder() throws Exception {
        when(reservationService.reserve(eq(7L), any())).thenReturn(pendingOrder());

        mockMvc.perform(post("/api/orders").header(USER_HEADER, 7)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId": 1, "idempotencyKey": "key-1", "eventSectorId": 3, "quantity": 2}"""))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"idempotencyKey\": \"k\", \"seatIds\": [1]}",
            "{\"eventId\": 0, \"idempotencyKey\": \"k\", \"seatIds\": [1]}",
            "{\"eventId\": -1, \"idempotencyKey\": \"k\", \"seatIds\": [1]}",
            "{\"eventId\": 1, \"seatIds\": [1]}",
            "{\"eventId\": 1, \"idempotencyKey\": \"\", \"seatIds\": [1]}",
            "{\"eventId\": 1, \"idempotencyKey\": \"   \", \"seatIds\": [1]}",
            "{\"eventId\": 1, \"idempotencyKey\": \"k\"}",
            "{\"eventId\": 1, \"idempotencyKey\": \"k\", \"seatIds\": []}",
            "{\"eventId\": 1, \"idempotencyKey\": \"k\", \"seatIds\": [0]}",
            "{\"eventId\": 1, \"idempotencyKey\": \"k\", \"seatIds\": [null]}",
            "{\"eventId\": 1, \"idempotencyKey\": \"k\", \"seatIds\": [1, 1]}",
            "{\"eventId\": 1, \"idempotencyKey\": \"k\", \"eventSectorId\": 3}",
            "{\"eventId\": 1, \"idempotencyKey\": \"k\", \"quantity\": 1}",
            "{\"eventId\": 1, \"idempotencyKey\": \"k\", \"eventSectorId\": 3, \"quantity\": 0}",
            "{\"eventId\": 1, \"idempotencyKey\": \"k\", \"seatIds\": [1], \"eventSectorId\": 3, \"quantity\": 1}",
            "{\"eventId\": 1, \"idempotencyKey\": \"k\", \"seatIds\": [1], \"quantity\": 1}"
    })
    void reserve_invalidBody_returnsValidationErrorWithoutReserving(String body) throws Exception {
        mockMvc.perform(post("/api/orders").header(USER_HEADER, 7)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
        verifyNoInteractions(reservationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "[]",
            "{\"eventId\": ",
            "no es json",
            "{\"eventId\": 1.5, \"idempotencyKey\": \"k\", \"seatIds\": [1]}",
            "{\"eventId\": \"abc\", \"idempotencyKey\": \"k\", \"seatIds\": [1]}",
            "{\"eventId\": 99999999999999999999, \"idempotencyKey\": \"k\", \"seatIds\": [1]}"
    })
    void reserve_unreadableBody_returnsBadRequestWithoutReserving(String body) throws Exception {
        mockMvc.perform(post("/api/orders").header(USER_HEADER, 7)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reservationService);
    }

    @Test
    void reserve_idempotencyKeyOfTheColumnLength_isAccepted() throws Exception {
        when(reservationService.reserve(eq(7L), any())).thenReturn(pendingOrder());

        mockMvc.perform(post("/api/orders").header(USER_HEADER, 7)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithKeyOfLength(255)))
                .andExpect(status().isCreated());
    }

    @Test
    void reserve_idempotencyKeyLongerThanTheColumn_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/orders").header(USER_HEADER, 7)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithKeyOfLength(256)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reservationService);
    }

    @Test
    void reserve_withoutUserHeader_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId": 1, "idempotencyKey": "k", "seatIds": [1]}"""))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reservationService);
    }

    @Test
    void reserve_notPositiveUserHeader_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/orders").header(USER_HEADER, 0)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId": 1, "idempotencyKey": "k", "seatIds": [1]}"""))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reservationService);
    }

    @Test
    void reserve_seatTaken_returnsConflictWithItsCode() throws Exception {
        when(reservationService.reserve(eq(7L), any()))
                .thenThrow(new TicketYaException(TicketErrorCode.SEAT_UNAVAILABLE, "Ocupado"));

        mockMvc.perform(post("/api/orders").header(USER_HEADER, 7)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId": 1, "idempotencyKey": "k", "seatIds": [1]}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("seat_unavailable"));
    }

    @Test
    void cancel_paymentInProgress_returnsConflictWithItsCode() throws Exception {
        when(orderClosingService.cancelReservation(7L, 5L))
                .thenThrow(new TicketYaException(TicketErrorCode.PAYMENT_IN_PROGRESS, "En curso"));

        mockMvc.perform(post("/api/orders/5/cancel").header(USER_HEADER, 7))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("payment_in_progress"));
    }

    @Test
    void cancel_notPositiveOrderId_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/orders/0/cancel").header(USER_HEADER, 7))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(orderClosingService);
    }

    @Test
    void findAvailability_withoutEventId_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/availability"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(availabilityService);
    }

    @Test
    void synchronizeEventSale_sectorWithCapacityAndSeats_returnsValidationError() throws Exception {
        mockMvc.perform(put("/internal/event-sales/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "PUBLISHED", "dateTime": "2099-12-12T21:00:00", "highDemand": false,
                                 "sectors": [{"eventSectorId": 1, "price": 100, "capacity": 5, "seatIds": [1]}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
        verifyNoInteractions(eventSaleService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "published", "0"})
    void synchronizeEventSale_statusThatIsNotASaleStatus_returnsBadRequest(String saleStatus) throws Exception {
        mockMvc.perform(put("/internal/event-sales/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "%s", "dateTime": "2099-12-12T21:00:00", "highDemand": false, "sectors": []}"""
                                .formatted(saleStatus)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(eventSaleService);
    }

    @Test
    void synchronizeEventSale_numberedSectorWithoutSeatsYet_returnsNoContent() throws Exception {
        mockMvc.perform(put("/internal/event-sales/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "CANCELLED", "dateTime": "2099-12-12T21:00:00", "highDemand": false,
                                 "sectors": [{"eventSectorId": 1, "price": 100, "seatIds": []}]}"""))
                .andExpect(status().isNoContent());
    }

    private static String bodyWithKeyOfLength(int length) {
        return """
                {"eventId": 1, "idempotencyKey": "%s", "seatIds": [1]}""".formatted("k".repeat(length));
    }

    private static OrderResponse pendingOrder() {
        return new OrderResponse(1L, 1L, OrderStatus.PENDING, LocalDateTime.now().plusMinutes(10),
                new BigDecimal("2000"), List.of());
    }
}

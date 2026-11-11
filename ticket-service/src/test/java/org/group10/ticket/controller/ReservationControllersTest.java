package org.group10.ticket.controller;

import org.group10.ticket.service.AvailabilityService;
import org.group10.ticket.service.EventSaleService;
import org.group10.ticket.service.OrderClosingService;
import org.group10.ticket.service.OrderPaymentService;
import org.group10.ticket.service.OrderQueryService;
import org.group10.ticket.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({OrderController.class, AvailabilityController.class, InternalEventSaleController.class})
class ReservationControllersTest {

    private static final String USER_HEADER = "X-User-Id";
    private static final String PUBLISHED_SALE_DATA = """
            "status": "PUBLISHED", "eventName": "Recital", "venueName": "Estadio", "venueAddress": "Calle 1",
             "venueCity": "Tandil", "dateTime": "2099-12-12T21:00:00", "highDemand": false""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;

    @MockitoBean
    private OrderClosingService orderClosingService;

    @MockitoBean
    private OrderPaymentService orderPaymentService;

    @MockitoBean
    private OrderQueryService orderQueryService;

    @MockitoBean
    private AvailabilityService availabilityService;

    @MockitoBean
    private EventSaleService eventSaleService;

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"eventId\": 1, \"idempotencyKey\": \"k\", \"seatIds\": []}",
            "{\"eventId\": 1, \"idempotencyKey\": \"k\", \"seatIds\": [1, 1]}",
            "{\"eventId\": 1, \"idempotencyKey\": \"k\", \"eventSectorId\": 3, \"quantity\": 0}",
            "{\"eventId\": 1, \"idempotencyKey\": \"k\", \"seatIds\": [1], \"eventSectorId\": 3, \"quantity\": 1}",
            "{\"eventId\": 1, \"idempotencyKey\": \"   \", \"seatIds\": [1]}"
    })
    void reserve_invalidBody_returnsValidationErrorWithoutReserving(String body) throws Exception {
        mockMvc.perform(post("/api/orders").header(USER_HEADER, 7)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
        verifyNoInteractions(reservationService);
    }

    @Test
    void reserve_idempotencyKeyLongerThanTheColumn_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/orders").header(USER_HEADER, 7)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId": 1, "idempotencyKey": "%s", "seatIds": [1]}""".formatted("k".repeat(256))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reservationService);
    }

    @Test
    void synchronizeEventSale_sectorWithCapacityAndSeats_returnsValidationError() throws Exception {
        mockMvc.perform(put("/internal/event-sales/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {%s, "sectors": [{"eventSectorId": 1, "sectorName": "Platea", "price": 100,
                                 "capacity": 5, "seats": [{"seatId": 1, "row": "A", "number": 1}]}]}"""
                                .formatted(PUBLISHED_SALE_DATA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
        verifyNoInteractions(eventSaleService);
    }
}

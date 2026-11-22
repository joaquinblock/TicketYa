package org.group10.ticket.controller;

import org.group10.ticket.service.OrderQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los compradores de un evento que pide el notification-service para avisar un cambio: solo con el secreto entre
 * servicios.
 */
@WebMvcTest(InternalEventController.class)
class InternalEventControllerTest {

    private static final String SECRET = "dev-internal-secret";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderQueryService orderQueryService;

    @Test
    void findBuyers_withTheSecret_returns200WithTheUserIds() throws Exception {
        when(orderQueryService.findBuyersOfEvent(3L)).thenReturn(List.of(1L, 7L));

        mockMvc.perform(get("/internal/events/3/buyers").header("X-Internal-Secret", SECRET))
                .andExpect(status().isOk())
                .andExpect(content().json("[1, 7]"));
    }

    @Test
    void findBuyers_eventWithoutBuyers_returns200WithAnEmptyList() throws Exception {
        when(orderQueryService.findBuyersOfEvent(3L)).thenReturn(List.of());

        mockMvc.perform(get("/internal/events/3/buyers").header("X-Internal-Secret", SECRET))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void findBuyers_withoutTheSecret_returns401WithoutLookingUpTheBuyers() throws Exception {
        mockMvc.perform(get("/internal/events/3/buyers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        verifyNoInteractions(orderQueryService);
    }

    @Test
    void findBuyers_customerHeadersAreNotEnough_returns401() throws Exception {
        mockMvc.perform(get("/internal/events/3/buyers").header("X-User-Id", 1)
                        .header("X-User-Permissions", "EVENT_ADMIN"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(orderQueryService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void findBuyers_nonPositiveEventId_returns400(String id) throws Exception {
        mockMvc.perform(get("/internal/events/" + id + "/buyers").header("X-Internal-Secret", SECRET))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
        verifyNoInteractions(orderQueryService);
    }
}

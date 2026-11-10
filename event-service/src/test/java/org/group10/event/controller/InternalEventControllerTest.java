package org.group10.event.controller;

import org.group10.common.exception.TicketYaException;
import org.group10.event.client.AuthClient;
import org.group10.event.exception.EventErrorCode;
import org.group10.event.service.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalEventController.class)
class InternalEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private AuthClient authClient;

    @Test
    void markSoldOut_existingEvent_returnsNoContent() throws Exception {
        mockMvc.perform(put("/internal/events/3/sold-out"))
                .andExpect(status().isNoContent());
        verify(eventService).markSoldOut(3L);
    }

    @Test
    void markSoldOut_invalidTransition_returnsConflictWithItsCode() throws Exception {
        doThrow(new TicketYaException(EventErrorCode.INVALID_STATUS_TRANSITION, "No se puede"))
                .when(eventService).markSoldOut(3L);

        mockMvc.perform(put("/internal/events/3/sold-out"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("invalid_status_transition"));
    }
}

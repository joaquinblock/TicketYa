package org.group10.event.controller;

import org.group10.common.security.Caller;
import org.group10.event.service.EventSearchService;
import org.group10.event.service.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
class OwnEventsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private EventSearchService eventSearchService;

    @Test
    void findOwn_withoutIdentity_returns401() throws Exception {
        mockMvc.perform(get("/api/events/mine"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        verifyNoInteractions(eventService);
    }

    @Test
    void findOwn_clientWithoutEventManage_returns403() throws Exception {
        mockMvc.perform(get("/api/events/mine").header("X-User-Id", 1).header("X-User-Role", "CLIENT")
                        .header("X-User-Permissions", "EVENT_SEARCH,TICKET_PURCHASE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
        verifyNoInteractions(eventService);
    }

    @Test
    void findOwn_organizer_passesItsCallerAndDoesNotReadMineAsAnId() throws Exception {
        mockMvc.perform(get("/api/events/mine").header("X-User-Id", 2).header("X-User-Role", "ORGANIZER")
                        .header("X-User-Permissions", "EVENT_MANAGE,EVENT_SEARCH").header("X-Organization-Id", 1))
                .andExpect(status().isOk());

        verify(eventService).findOwn(new Caller(2L, "ORGANIZER", Set.of("EVENT_MANAGE", "EVENT_SEARCH"), 1L));
        verify(eventService, never()).findById(any(), any());
    }
}

package org.group10.event.controller;

import org.group10.event.dto.AdminEventResponse;
import org.group10.event.entity.EventStatus;
import org.group10.event.entity.EventType;
import org.group10.event.service.EventSearchService;
import org.group10.event.service.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El listado del administrador incluye los suspendidos, que la búsqueda pública oculta. */
@WebMvcTest(EventController.class)
class AdminEventListControllerTest {

    private static final String ADMIN_EVENTS = "/api/events/admin";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private EventSearchService eventSearchService;

    @Test
    void listForAdmin_withEventAdmin_returnsEveryStatus() throws Exception {
        when(eventSearchService.listForAdmin()).thenReturn(List.of(new AdminEventResponse(1L, "Los Serranos en vivo",
                EventType.CONCERT, EventStatus.SUSPENDED, LocalDateTime.of(2026, 12, 12, 21, 0), "Estadio del Sur",
                "Buenos Aires", 1L)));

        mockMvc.perform(get(ADMIN_EVENTS).header("X-User-Id", 3).header("X-User-Role", "ADMIN")
                        .header("X-User-Permissions", "EVENT_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("SUSPENDED"))
                .andExpect(jsonPath("$[0].venueName").value("Estadio del Sur"));
    }

    @Test
    void listForAdmin_withoutEventAdmin_returns403() throws Exception {
        mockMvc.perform(get(ADMIN_EVENTS).header("X-User-Id", 2).header("X-User-Role", "ORGANIZER")
                        .header("X-User-Permissions", "EVENT_MANAGE,EVENT_SEARCH"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(eventSearchService);
    }
}

package org.group10.event.controller;

import org.group10.common.security.Caller;
import org.group10.event.dto.EventCreateRequest;
import org.group10.event.dto.EventSearchRequest;
import org.group10.event.entity.EventType;
import org.group10.event.service.EventSearchService;
import org.group10.event.service.EventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
class EventControllerTest {

    private static final String VALID_EVENT = """
            {"name": "Los Serranos en vivo", "description": "Presentación del nuevo disco", "type": "CONCERT",
             "venueId": 1, "dateTime": "2099-12-12T21:00:00", "highDemand": true,
             "saleStart": "2099-11-20T10:00:00", "maxTicketsPerCustomer": 4}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private EventSearchService eventSearchService;

    @Test
    void search_repeatedTypeAndParticipantId_arriveAsLists() throws Exception {
        mockMvc.perform(get("/api/events?type=CONCERT&type=THEATER&participantId=1&participantId=4"))
                .andExpect(status().isOk());

        ArgumentCaptor<EventSearchRequest> request = ArgumentCaptor.forClass(EventSearchRequest.class);
        verify(eventSearchService).search(request.capture());
        assertThat(request.getValue().type()).containsExactly(EventType.CONCERT, EventType.THEATER);
        assertThat(request.getValue().participantId()).containsExactly(1L, 4L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"participantId=abc", "participantId=0", "participantId=-1", "type=OPERA"})
    void search_invalidRepeatedFilter_returns400(String query) throws Exception {
        mockMvc.perform(get("/api/events?" + query))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
        verifyNoInteractions(eventSearchService);
    }

    @Test
    void create_withoutIdentity_returns401AndDoesNotCreate() throws Exception {
        mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(VALID_EVENT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        verifyNoInteractions(eventService);
    }

    @Test
    void create_userWithoutEventManage_returns403AndDoesNotCreate() throws Exception {
        mockMvc.perform(asClient(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(VALID_EVENT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"))
                .andExpect(jsonPath("$.message").value("El usuario 1 no tiene el permiso EVENT_MANAGE"));
        verifyNoInteractions(eventService);
    }

    @Test
    void create_withoutPermissionAndInvalidBody_returns403BeforeValidating() throws Exception {
        String body = VALID_EVENT.replace("\"Los Serranos en vivo\"", "\"  \"");

        mockMvc.perform(asClient(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
    }

    @Test
    void create_organizer_isCreatedWithTheCallerFromTheGatewayHeaders() throws Exception {
        mockMvc.perform(asOrganizer(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(VALID_EVENT)))
                .andExpect(status().isCreated());

        ArgumentCaptor<Caller> caller = ArgumentCaptor.forClass(Caller.class);
        verify(eventService).create(caller.capture(), any(EventCreateRequest.class));
        assertThat(caller.getValue())
                .isEqualTo(new Caller(2L, "ORGANIZER", Set.of("EVENT_MANAGE", "EVENT_SEARCH"), 1L));
    }

    @Test
    void findById_withoutIdentity_isPublicAndHasNoCaller() throws Exception {
        mockMvc.perform(get("/api/events/3")).andExpect(status().isOk());

        verify(eventService).findById(3L, Optional.empty());
    }

    @Test
    void findById_withIdentity_passesTheCallerToSeeDrafts() throws Exception {
        mockMvc.perform(asOrganizer(get("/api/events/3"))).andExpect(status().isOk());

        verify(eventService).findById(eq(3L), eq(Optional.of(
                new Caller(2L, "ORGANIZER", Set.of("EVENT_MANAGE", "EVENT_SEARCH"), 1L))));
    }

    @Test
    void cancel_admin_isAllowedWithEventAdmin() throws Exception {
        mockMvc.perform(post("/api/events/3/cancel").header("X-User-Id", 3).header("X-User-Role", "ADMIN")
                        .header("X-User-Permissions", "EVENT_ADMIN"))
                .andExpect(status().isOk());

        verify(eventService).cancel(3L, new Caller(3L, "ADMIN", Set.of("EVENT_ADMIN"), null));
    }

    private static MockHttpServletRequestBuilder asClient(MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", 1).header("X-User-Role", "CLIENT")
                .header("X-User-Permissions", "EVENT_SEARCH,TICKET_PURCHASE");
    }

    private static MockHttpServletRequestBuilder asOrganizer(MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", 2).header("X-User-Role", "ORGANIZER")
                .header("X-User-Permissions", "EVENT_MANAGE,EVENT_SEARCH").header("X-Organization-Id", 1);
    }
}

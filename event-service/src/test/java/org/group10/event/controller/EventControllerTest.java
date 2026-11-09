package org.group10.event.controller;

import org.group10.common.exception.TicketYaException;
import org.group10.event.dto.EventResponse;
import org.group10.event.dto.VenueSummaryResponse;
import org.group10.event.entity.EventStatus;
import org.group10.event.entity.EventType;
import org.group10.event.exception.EventErrorCode;
import org.group10.event.service.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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

    @Test
    void create_validEvent_returns201InDraft() throws Exception {
        when(eventService.create(eq(1L), eq(2L), any())).thenReturn(response());

        mockMvc.perform(createRequest(VALID_EVENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.venue.id").value(1));
    }

    @Test
    void create_withoutOrganizationHeader_returns400() throws Exception {
        mockMvc.perform(post("/api/events").header("X-User-Id", 2)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_EVENT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_parameter"));
        verifyNoInteractions(eventService);
    }

    @Test
    void create_withoutUserHeader_returns400() throws Exception {
        mockMvc.perform(post("/api/events").header("X-Organization-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_EVENT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_parameter"));
        verifyNoInteractions(eventService);
    }

    @Test
    void create_withPastDate_returns400WithTheField() throws Exception {
        String body = VALID_EVENT.replace("2099-12-12T21:00:00", "2020-01-01T21:00:00");

        mockMvc.perform(createRequest(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"))
                .andExpect(jsonPath("$.detail.dateTime").exists());
    }

    @Test
    void create_withoutName_returns400WithTheField() throws Exception {
        String body = VALID_EVENT.replace("\"Los Serranos en vivo\"", "\"  \"");

        mockMvc.perform(createRequest(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail.name").exists());
    }

    @Test
    void create_withUnknownType_returns400WithTheAllowedValues() throws Exception {
        String body = VALID_EVENT.replace("CONCERT", "PICNIC");

        mockMvc.perform(createRequest(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail.allowedValues").isArray());
    }

    @Test
    void create_organizerOfAnotherOrganization_returns403() throws Exception {
        when(eventService.create(eq(1L), eq(2L), any())).thenThrow(new TicketYaException(
                EventErrorCode.ORGANIZER_NOT_IN_ORGANIZATION, "El organizador 2 no pertenece a la organización 1"));

        mockMvc.perform(createRequest(VALID_EVENT))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("organizer_not_in_organization"));
    }

    @Test
    void create_authServiceDown_returns503() throws Exception {
        when(eventService.create(eq(1L), eq(2L), any())).thenThrow(new ResourceAccessException("Connection refused"));

        mockMvc.perform(createRequest(VALID_EVENT))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("service_unavailable"));
    }

    private static MockHttpServletRequestBuilder createRequest(String body) {
        return post("/api/events")
                .header("X-Organization-Id", 1)
                .header("X-User-Id", 2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static EventResponse response() {
        return new EventResponse(5L, "Los Serranos en vivo", "Presentación del nuevo disco", EventType.CONCERT, 1L, 2L,
                new VenueSummaryResponse(1L, "Estadio del Sur", "Av. del Estadio 1000", "Buenos Aires", -34.5453, -58.4498),
                LocalDateTime.of(2099, 12, 12, 21, 0), EventStatus.DRAFT, true,
                LocalDateTime.of(2099, 11, 20, 10, 0), 4);
    }
}

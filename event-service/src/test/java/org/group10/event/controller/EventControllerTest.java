package org.group10.event.controller;

import org.group10.common.exception.TicketYaException;
import org.group10.event.client.AuthClient;
import org.group10.event.client.dto.UserPermissionsResponse;
import org.group10.event.dto.EventDetailResponse;
import org.group10.event.dto.EventResponse;
import org.group10.event.dto.EventSectorResponse;
import org.group10.event.dto.ParticipantResponse;
import org.group10.event.dto.VenueSummaryResponse;
import org.group10.event.entity.EventStatus;
import org.group10.event.entity.EventType;
import org.group10.event.entity.ParticipantType;
import org.group10.event.exception.EventErrorCode;
import org.group10.event.service.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    private AuthClient authClient;

    @BeforeEach
    void organizerWithEventManage() {
        when(authClient.getPermissions(2L)).thenReturn(
                new UserPermissionsResponse(2L, "ORGANIZER", Set.of("EVENT_SEARCH", "EVENT_MANAGE")));
    }

    @Test
    void create_userWithoutEventManage_returns403AndDoesNotCreate() throws Exception {
        when(authClient.getPermissions(2L)).thenReturn(
                new UserPermissionsResponse(2L, "CLIENT", Set.of("EVENT_SEARCH", "TICKET_PURCHASE")));

        mockMvc.perform(createRequest(VALID_EVENT))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"))
                .andExpect(jsonPath("$.message").value("El usuario 2 no tiene el permiso EVENT_MANAGE"));
        verifyNoInteractions(eventService);
    }

    @Test
    void create_withoutPermissionAndInvalidBody_returns403BeforeValidating() throws Exception {
        when(authClient.getPermissions(2L)).thenReturn(
                new UserPermissionsResponse(2L, "CLIENT", Set.of("EVENT_SEARCH")));
        String body = VALID_EVENT.replace("\"Los Serranos en vivo\"", "\"  \"");

        mockMvc.perform(createRequest(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
    }

    @Test
    void create_withNonNumericUserHeader_returns400WithoutCallingAuth() throws Exception {
        mockMvc.perform(post("/api/events").header("X-Organization-Id", 1).header("X-User-Id", "abc")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_EVENT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_parameter"));
        verifyNoInteractions(authClient, eventService);
    }

    @Test
    void create_authServiceDownWhileCheckingPermissions_returns503() throws Exception {
        when(authClient.getPermissions(2L)).thenThrow(new ResourceAccessException("Connection refused"));

        mockMvc.perform(createRequest(VALID_EVENT))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("service_unavailable"));
        verifyNoInteractions(eventService);
    }

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
                .andExpect(jsonPath("$.error").value("invalid_parameter"))
                .andExpect(jsonPath("$.message").value("Falta el header obligatorio 'X-User-Id'"));
        verifyNoInteractions(authClient, eventService);
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
    void create_withSeveralInvalidFields_returns400WithEachOne() throws Exception {
        String body = VALID_EVENT.replace("\"Los Serranos en vivo\"", "\"  \"")
                .replace("2099-12-12T21:00:00", "2020-01-01T21:00:00");

        mockMvc.perform(createRequest(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Campos inválidos"))
                .andExpect(jsonPath("$.detail.name").exists())
                .andExpect(jsonPath("$.detail.dateTime").exists());
        verifyNoInteractions(eventService);
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

    @Test
    void replaceParticipants_valid_returns200WithTheParticipants() throws Exception {
        when(eventService.replaceParticipants(eq(10L), eq(2L), any()))
                .thenReturn(List.of(new ParticipantResponse(1L, "Los Serranos", ParticipantType.ARTIST)));

        mockMvc.perform(put("/api/events/10/participants").header("X-User-Id", 2)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"participantIds\": [1]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Los Serranos"));
    }

    @Test
    void replaceParticipants_userWithoutEventManage_returns403() throws Exception {
        when(authClient.getPermissions(1L)).thenReturn(
                new UserPermissionsResponse(1L, "CLIENT", Set.of("EVENT_SEARCH")));

        mockMvc.perform(put("/api/events/10/participants").header("X-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"participantIds\": [1]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
        verifyNoInteractions(eventService);
    }

    @Test
    void replaceParticipants_withoutTheList_returns400() throws Exception {
        mockMvc.perform(put("/api/events/10/participants").header("X-User-Id", 2)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail.participantIds").exists());
        verifyNoInteractions(eventService);
    }

    @Test
    void replaceParticipants_notADraft_returns409() throws Exception {
        when(eventService.replaceParticipants(eq(10L), eq(2L), any())).thenThrow(new TicketYaException(
                EventErrorCode.EVENT_NOT_EDITABLE, "El evento 10 está PUBLISHED"));

        mockMvc.perform(put("/api/events/10/participants").header("X-User-Id", 2)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"participantIds\": [1]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("event_not_editable"));
    }

    @Test
    void findById_isPublic_soItDoesNotCallAuth() throws Exception {
        when(eventService.findById(1L)).thenReturn(detail(EventStatus.PUBLISHED));

        mockMvc.perform(get("/api/events/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(5080))
                .andExpect(jsonPath("$.sectors[0].price").value(45000));
        verifyNoInteractions(authClient);
    }

    @Test
    void findById_missing_returns404() throws Exception {
        when(eventService.findById(99L)).thenThrow(new TicketYaException(EventErrorCode.EVENT_NOT_FOUND,
                "No se encontró el evento con id 99"));

        mockMvc.perform(get("/api/events/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("event_not_found"));
    }

    @Test
    void update_valid_returns200() throws Exception {
        when(eventService.update(eq(3L), eq(2L), any())).thenReturn(detail(EventStatus.DRAFT));

        mockMvc.perform(put("/api/events/3").header("X-User-Id", 2)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_EVENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void update_withInvalidBody_returns400WithoutCallingTheService() throws Exception {
        mockMvc.perform(put("/api/events/3").header("X-User-Id", 2)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_EVENT.replace("\"Los Serranos en vivo\"", "\" \"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail.name").exists());
        verifyNoInteractions(eventService);
    }

    @Test
    void delete_draft_returns204() throws Exception {
        mockMvc.perform(delete("/api/events/10").header("X-User-Id", 2)).andExpect(status().isNoContent());
        verify(eventService).delete(10L, 2L);
    }

    @Test
    void publish_notReady_returns409WithTheProblems() throws Exception {
        when(eventService.publish(10L, 2L)).thenThrow(new TicketYaException(EventErrorCode.EVENT_NOT_PUBLISHABLE,
                "El evento 10 todavía no se puede publicar", Map.of("problems", List.of("No tiene sectores a la venta"))));

        mockMvc.perform(post("/api/events/10/publish").header("X-User-Id", 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("event_not_publishable"))
                .andExpect(jsonPath("$.detail.problems[0]").value("No tiene sectores a la venta"));
    }

    @Test
    void publish_withoutEventManage_returns403() throws Exception {
        when(authClient.getPermissions(3L)).thenReturn(
                new UserPermissionsResponse(3L, "ADMIN", Set.of("EVENT_SEARCH", "EVENT_ADMIN")));

        mockMvc.perform(post("/api/events/10/publish").header("X-User-Id", 3))
                .andExpect(status().isForbidden());
        verifyNoInteractions(eventService);
    }

    @Test
    void cancel_byAnAdmin_returns200() throws Exception {
        when(authClient.getPermissions(3L)).thenReturn(
                new UserPermissionsResponse(3L, "ADMIN", Set.of("EVENT_SEARCH", "EVENT_ADMIN")));
        when(eventService.cancel(1L)).thenReturn(detail(EventStatus.CANCELLED));

        mockMvc.perform(post("/api/events/1/cancel").header("X-User-Id", 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancel_byAnOrganizer_returns403BecauseOnlyAdminsCancel() throws Exception {
        mockMvc.perform(post("/api/events/1/cancel").header("X-User-Id", 2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("El usuario 2 no tiene el permiso EVENT_ADMIN"));
        verifyNoInteractions(eventService);
    }

    @Test
    void cancel_draft_returns409() throws Exception {
        when(authClient.getPermissions(3L)).thenReturn(
                new UserPermissionsResponse(3L, "ADMIN", Set.of("EVENT_ADMIN")));
        when(eventService.cancel(10L)).thenThrow(new TicketYaException(EventErrorCode.INVALID_STATUS_TRANSITION,
                "Un borrador no se cancela: se borra con DELETE /api/events/10"));

        mockMvc.perform(post("/api/events/10/cancel").header("X-User-Id", 3))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("invalid_status_transition"));
    }

    @Test
    void replaceSectors_valid_returns200WithTheDetail() throws Exception {
        when(eventService.replaceSectors(eq(10L), eq(2L), any())).thenReturn(detail(EventStatus.DRAFT));

        mockMvc.perform(put("/api/events/10/sectors").header("X-User-Id", 2).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectors\": [{\"sectorId\": 3, \"price\": 20000.00}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(5080));
    }

    @Test
    void replaceSectors_withZeroOrTooManyDecimalsInThePrice_returns400() throws Exception {
        mockMvc.perform(put("/api/events/10/sectors").header("X-User-Id", 2).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectors\": [{\"sectorId\": 3, \"price\": 0}, {\"sectorId\": 4, \"price\": 10.555}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"))
                .andExpect(jsonPath("$.detail['sectors[0].price']").exists())
                .andExpect(jsonPath("$.detail['sectors[1].price']").exists());
        verifyNoInteractions(eventService);
    }

    @Test
    void replaceSectors_withoutTheList_returns400() throws Exception {
        mockMvc.perform(put("/api/events/10/sectors").header("X-User-Id", 2).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail.sectors").exists());
    }

    @Test
    void replaceSectors_withoutEventManage_returns403() throws Exception {
        when(authClient.getPermissions(1L)).thenReturn(
                new UserPermissionsResponse(1L, "CLIENT", Set.of("EVENT_SEARCH")));

        mockMvc.perform(put("/api/events/10/sectors").header("X-User-Id", 1).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectors\": []}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(eventService);
    }

    private static EventDetailResponse detail(EventStatus status) {
        return new EventDetailResponse(1L, "Los Serranos en vivo", "Presentación del nuevo disco", EventType.CONCERT, 1L,
                2L, new VenueSummaryResponse(1L, "Estadio del Sur", "Av. del Estadio 1000", "Buenos Aires", -34.5453, -58.4498),
                LocalDateTime.of(2099, 12, 12, 21, 0), status, true, LocalDateTime.of(2099, 11, 20, 10, 0), 4, 5080,
                List.of(new EventSectorResponse(1L, "Campo", false, 5000, new BigDecimal("45000.00")),
                        new EventSectorResponse(2L, "Platea", true, 80, new BigDecimal("70000.00"))),
                List.of(new ParticipantResponse(1L, "Los Serranos", ParticipantType.ARTIST)));
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

package org.group10.auth.controller;

import org.group10.auth.dto.AccessAssignmentOutcome;
import org.group10.auth.dto.AccessAssignmentResponse;
import org.group10.auth.exception.AuthErrorCode;
import org.group10.auth.service.AccessAssignmentService;
import org.group10.common.dto.ErrorDto;
import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.group10.common.security.Caller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccessAssignmentController.class)
class AccessAssignmentControllerTest {

    private static final String ASSIGN_STAFF_4_TO_EVENT_10 = "/api/users/4/access-assignments/10";
    private static final Caller ORGANIZER_OF_ORGANIZATION_1 =
            new Caller(2L, "ORGANIZER", Set.of("EVENT_MANAGE", "REPORT_VIEW"), 1L);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccessAssignmentService accessAssignmentService;

    @Test
    void assign_firstTime_returns201WithTheAssignment() throws Exception {
        when(accessAssignmentService.assign(ORGANIZER_OF_ORGANIZATION_1, 4L, 10L))
                .thenReturn(new AccessAssignmentOutcome(new AccessAssignmentResponse(4L, 10L), true));

        mockMvc.perform(asOrganizer(put(ASSIGN_STAFF_4_TO_EVENT_10)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.staffId").value(4))
                .andExpect(jsonPath("$.eventId").value(10));
    }

    @Test
    void assign_alreadyAssigned_returns200WithTheAssignment() throws Exception {
        when(accessAssignmentService.assign(ORGANIZER_OF_ORGANIZATION_1, 4L, 10L))
                .thenReturn(new AccessAssignmentOutcome(new AccessAssignmentResponse(4L, 10L), false));

        mockMvc.perform(asOrganizer(put(ASSIGN_STAFF_4_TO_EVENT_10)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staffId").value(4))
                .andExpect(jsonPath("$.eventId").value(10));
    }

    @Test
    void assign_withoutIdentity_returns401() throws Exception {
        mockMvc.perform(put(ASSIGN_STAFF_4_TO_EVENT_10))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        verifyNoInteractions(accessAssignmentService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EVENT_SEARCH,TICKET_PURCHASE,REFUND_REQUEST", "TICKET_VALIDATE"})
    void assign_roleWithoutEventManage_returns403(String permissions) throws Exception {
        mockMvc.perform(put(ASSIGN_STAFF_4_TO_EVENT_10).header("X-User-Id", 4)
                        .header("X-User-Permissions", permissions))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
        verifyNoInteractions(accessAssignmentService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/users/0/access-assignments/10", "/api/users/-1/access-assignments/10",
            "/api/users/4/access-assignments/0", "/api/users/4/access-assignments/-1",
            "/api/users/abc/access-assignments/10", "/api/users/4/access-assignments/abc",
            "/api/users/9223372036854775808/access-assignments/10"})
    void assign_invalidIds_returns400(String path) throws Exception {
        mockMvc.perform(asOrganizer(put(path)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
        verifyNoInteractions(accessAssignmentService);
    }

    @Test
    void assign_staffNotFound_returns404AccessStaffNotFound() throws Exception {
        when(accessAssignmentService.assign(any(), eq(4L), eq(10L)))
                .thenThrow(new TicketYaException(AuthErrorCode.ACCESS_STAFF_NOT_FOUND,
                        "El usuario 4 no es personal de acceso"));

        mockMvc.perform(asOrganizer(put(ASSIGN_STAFF_4_TO_EVENT_10)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("access_staff_not_found"));
    }

    @Test
    void assign_staffOrEventOfAnotherOrganization_returns403() throws Exception {
        when(accessAssignmentService.assign(any(), eq(4L), eq(10L)))
                .thenThrow(new TicketYaException(CommonErrorCode.FORBIDDEN,
                        "El usuario 2 no pertenece a la organización del evento 10"));

        mockMvc.perform(asOrganizer(put(ASSIGN_STAFF_4_TO_EVENT_10)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
    }

    @Test
    void assign_eventServiceSaysEventNotFound_forwards404EventNotFound() throws Exception {
        HttpClientErrorException notFound = HttpClientErrorException.create(HttpStatus.NOT_FOUND, "not found",
                new HttpHeaders(), new byte[0], StandardCharsets.UTF_8);
        notFound.setBodyConvertFunction(type ->
                new ErrorDto("event_not_found", "No se encontró el evento con id 10", 404, null));
        when(accessAssignmentService.assign(any(), eq(4L), eq(10L))).thenThrow(notFound);

        mockMvc.perform(asOrganizer(put(ASSIGN_STAFF_4_TO_EVENT_10)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("event_not_found"));
    }

    @Test
    void assign_eventServiceDoesNotRespond_returns503() throws Exception {
        when(accessAssignmentService.assign(any(), eq(4L), eq(10L)))
                .thenThrow(new ResourceAccessException("event-service no respondió a tiempo"));

        mockMvc.perform(asOrganizer(put(ASSIGN_STAFF_4_TO_EVENT_10)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("service_unavailable"));
    }

    @Test
    void assign_eventServiceAnswers5xx_returns503() throws Exception {
        when(accessAssignmentService.assign(any(), eq(4L), eq(10L)))
                .thenThrow(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "down",
                        new HttpHeaders(), new byte[0], StandardCharsets.UTF_8));

        mockMvc.perform(asOrganizer(put(ASSIGN_STAFF_4_TO_EVENT_10)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("service_unavailable"));
    }

    private static MockHttpServletRequestBuilder asOrganizer(MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", 2).header("X-User-Role", "ORGANIZER")
                .header("X-User-Permissions", "EVENT_MANAGE,REPORT_VIEW").header("X-Organization-Id", 1);
    }
}

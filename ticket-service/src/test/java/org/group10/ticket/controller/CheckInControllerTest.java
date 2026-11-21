package org.group10.ticket.controller;

import org.group10.common.exception.TicketYaException;
import org.group10.common.dto.ErrorDto;
import org.group10.ticket.client.AuthClient;
import org.mockito.InOrder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import java.nio.charset.StandardCharsets;
import org.group10.ticket.dto.CheckInResponse;
import org.group10.ticket.exception.TicketErrorCode;
import org.group10.ticket.service.CheckInService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ingreso con QR (US-TKT-19): solo el personal de acceso, a su nombre.
 */
@WebMvcTest(CheckInController.class)
class CheckInControllerTest {

    private static final String QR = "859c3438-0059-403e-af24-f76673ca852e";
    private static final String BODY = """
            {"eventId": 1, "qrCode": "%s"}""".formatted(QR);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CheckInService checkInService;

    @MockitoBean
    private AuthClient authClient;

    @Test
    void checkIn_staff_validatesAsTheUserOfTheTokenAndReturnsThePlace() throws Exception {
        when(checkInService.checkIn(5L, 1L, QR))
                .thenReturn(new CheckInResponse(10L, "Platea", "A", 15, LocalDateTime.of(2026, 12, 13, 20, 41)));

        mockMvc.perform(asStaff(checkIn(BODY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(10))
                .andExpect(jsonPath("$.sectorName").value("Platea"))
                .andExpect(jsonPath("$.row").value("A"))
                .andExpect(jsonPath("$.number").value(15))
                .andExpect(jsonPath("$.usedAt").value("2026-12-13T20:41:00"));
        verify(checkInService).checkIn(5L, 1L, QR);
    }

    @Test
    void checkIn_withoutIdentity_returns401() throws Exception {
        mockMvc.perform(checkIn(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        verifyNoInteractions(checkInService);
    }

    @Test
    void checkIn_client_returns403() throws Exception {
        mockMvc.perform(checkIn(BODY).header("X-User-Id", 7)
                        .header("X-User-Permissions", "EVENT_SEARCH,TICKET_PURCHASE,REFUND_REQUEST"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("El usuario 7 no tiene el permiso TICKET_VALIDATE"));
        verifyNoInteractions(checkInService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"qrCode\": \"" + QR + "\"}",
            "{\"eventId\": 1}",
            "{\"eventId\": 1, \"qrCode\": \"\"}",
            "{\"eventId\": 1, \"qrCode\": \"   \"}",
            "{\"eventId\": 0, \"qrCode\": \"" + QR + "\"}",
            "{\"eventId\": -1, \"qrCode\": \"" + QR + "\"}"})
    void checkIn_invalidBody_returns400(String body) throws Exception {
        mockMvc.perform(asStaff(checkIn(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
        verifyNoInteractions(checkInService);
    }

    @Test
    void checkIn_invalidQr_returns400() throws Exception {
        when(checkInService.checkIn(5L, 1L, QR))
                .thenThrow(new TicketYaException(TicketErrorCode.INVALID_QR, "QR inválido"));

        mockMvc.perform(asStaff(checkIn(BODY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_qr"));
    }

    @Test
    void checkIn_usedTicket_returns409() throws Exception {
        when(checkInService.checkIn(5L, 1L, QR))
                .thenThrow(new TicketYaException(TicketErrorCode.TICKET_ALREADY_USED, "Ya se usó"));

        mockMvc.perform(asStaff(checkIn(BODY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ticket_already_used"));
    }

    @Test
    void checkIn_asksAuthForTheTokenUserAndTheBodyEventBeforeValidatingTheQr() throws Exception {
        mockMvc.perform(asStaff(checkIn(BODY))).andExpect(status().isOk());

        InOrder order = inOrder(authClient, checkInService);
        order.verify(authClient).ensureStaffAssigned(5L, 1L);
        order.verify(checkInService).checkIn(5L, 1L, QR);
    }

    @Test
    void checkIn_staffNotAssigned_forwards403StaffNotAssignedWithoutLookingUpTheQr() throws Exception {
        HttpClientErrorException notAssigned = HttpClientErrorException.create(HttpStatus.FORBIDDEN, "forbidden",
                new HttpHeaders(), new byte[0], StandardCharsets.UTF_8);
        notAssigned.setBodyConvertFunction(type ->
                new ErrorDto("staff_not_assigned", "El usuario 5 no está asignado al evento 1", 403, null));
        doThrow(notAssigned).when(authClient).ensureStaffAssigned(5L, 1L);

        mockMvc.perform(asStaff(checkIn(BODY)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("staff_not_assigned"));
        verifyNoInteractions(checkInService);
    }

    @Test
    void checkIn_authServiceDoesNotRespond_returns503WithoutLookingUpTheQr() throws Exception {
        doThrow(new ResourceAccessException("auth-service no respondió a tiempo"))
                .when(authClient).ensureStaffAssigned(5L, 1L);

        mockMvc.perform(asStaff(checkIn(BODY)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("service_unavailable"));
        verifyNoInteractions(checkInService);
    }

    @Test
    void checkIn_authServiceAnswers5xx_returns503WithoutLookingUpTheQr() throws Exception {
        doThrow(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "down", new HttpHeaders(),
                new byte[0], StandardCharsets.UTF_8)).when(authClient).ensureStaffAssigned(5L, 1L);

        mockMvc.perform(asStaff(checkIn(BODY)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("service_unavailable"));
        verifyNoInteractions(checkInService);
    }

    private static MockHttpServletRequestBuilder checkIn(String body) {
        return post("/api/tickets/check-in").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder asStaff(MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", 5).header("X-User-Permissions", "TICKET_VALIDATE");
    }
}

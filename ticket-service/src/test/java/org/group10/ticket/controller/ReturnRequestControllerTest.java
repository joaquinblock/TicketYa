package org.group10.ticket.controller;

import org.group10.common.exception.TicketYaException;
import org.group10.ticket.dto.ReturnRequestPageResponse;
import org.group10.ticket.dto.ReturnRequestResponse;
import org.group10.ticket.entity.ReturnRequestStatus;
import org.group10.ticket.exception.TicketErrorCode;
import org.group10.ticket.service.ReturnRequestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReturnRequestController.class)
class ReturnRequestControllerTest {

    private static final String USER_HEADER = "X-User-Id";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReturnRequestService returnRequestService;

    @Test
    void requestReturn_validTicket_returns201WithTheRequest() throws Exception {
        when(returnRequestService.requestReturn(7L, 5L)).thenReturn(requestIn(ReturnRequestStatus.PENDING));

        mockMvc.perform(post("/api/tickets/5/return-requests").header(USER_HEADER, 7))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.ticketId").value(5))
                .andExpect(jsonPath("$.eventId").value(9))
                .andExpect(jsonPath("$.requestedBy").value(7));
    }

    @Test
    void requestReturn_withoutUserHeader_returns400() throws Exception {
        mockMvc.perform(post("/api/tickets/5/return-requests")).andExpect(status().isBadRequest());

        verifyNoInteractions(returnRequestService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5"})
    void requestReturn_invalidTicketId_returns400(String ticketId) throws Exception {
        mockMvc.perform(post("/api/tickets/%s/return-requests".formatted(ticketId)).header(USER_HEADER, 7))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(returnRequestService);
    }

    @Test
    void requestReturn_requestAlreadyPending_returns409() throws Exception {
        when(returnRequestService.requestReturn(7L, 5L)).thenThrow(new TicketYaException(
                TicketErrorCode.RETURN_REQUEST_ALREADY_PENDING, "Ya hay una solicitud"));

        mockMvc.perform(post("/api/tickets/5/return-requests").header(USER_HEADER, 7))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("return_request_already_pending"));
    }

    @Test
    void findByStatus_withoutParameters_asksForThePendingOnesOfTheFirstPage() throws Exception {
        when(returnRequestService.findByStatus(ReturnRequestStatus.PENDING, 0, 20)).thenReturn(
                new ReturnRequestPageResponse(List.of(requestIn(ReturnRequestStatus.PENDING)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/return-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"status=OTHER", "status=pending", "page=-1", "page=abc", "size=0", "size=101"})
    void findByStatus_invalidParameter_returns400(String query) throws Exception {
        mockMvc.perform(get("/api/return-requests?" + query)).andExpect(status().isBadRequest());

        verifyNoInteractions(returnRequestService);
    }

    @Test
    void approve_validRequest_returns200WithTheResolvedRequest() throws Exception {
        when(returnRequestService.approve(1L, 3L)).thenReturn(requestIn(ReturnRequestStatus.APPROVED));

        mockMvc.perform(post("/api/return-requests/3/approve").header(USER_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void approve_unknownRequest_returns404() throws Exception {
        when(returnRequestService.approve(1L, 3L)).thenThrow(new TicketYaException(
                TicketErrorCode.RETURN_REQUEST_NOT_FOUND, "No existe"));

        mockMvc.perform(post("/api/return-requests/3/approve").header(USER_HEADER, 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("return_request_not_found"));
    }

    @Test
    void reject_validRequest_returns200WithTheResolvedRequest() throws Exception {
        when(returnRequestService.reject(1L, 3L)).thenReturn(requestIn(ReturnRequestStatus.REJECTED));

        mockMvc.perform(post("/api/return-requests/3/reject").header(USER_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    private ReturnRequestResponse requestIn(ReturnRequestStatus status) {
        return new ReturnRequestResponse(3L, 5L, 9L, 7L, LocalDateTime.of(2026, 1, 1, 10, 0), status, null, null);
    }
}

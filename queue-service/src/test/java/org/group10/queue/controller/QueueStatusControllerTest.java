package org.group10.queue.controller;

import org.group10.common.exception.TicketYaException;
import org.group10.queue.dto.QueueEntryResponse;
import org.group10.queue.exception.QueueErrorCode;
import org.group10.queue.service.QueueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QueueController.class)
class QueueStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QueueService queueService;

    @Test
    void getStatus_enabledCustomer_returnsTurnAndFixedWindow() throws Exception {
        Instant enabledUntil = Instant.parse("2026-11-22T13:10:30Z");
        when(queueService.getStatus(10L, 7L))
                .thenReturn(new QueueEntryResponse("ENABLED", null, "signed-token", enabledUntil));

        mockMvc.perform(get("/api/queue/10/me")
                        .header("X-User-Id", 7)
                        .header("X-User-Permissions", "TICKET_PURCHASE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENABLED"))
                .andExpect(jsonPath("$.queueToken").value("signed-token"))
                .andExpect(jsonPath("$.enabledUntil").value(enabledUntil.toString()));

        verify(queueService).getStatus(10L, 7L);
    }

    @Test
    void getStatus_withoutQueueEntry_returnsNotFound() throws Exception {
        when(queueService.getStatus(10L, 7L)).thenThrow(new TicketYaException(
                QueueErrorCode.QUEUE_ENTRY_NOT_FOUND, "El cliente no ingresó a la fila"));

        mockMvc.perform(get("/api/queue/10/me")
                        .header("X-User-Id", 7)
                        .header("X-User-Permissions", "TICKET_PURCHASE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("queue_entry_not_found"));
    }

    @Test
    void getStatus_withoutTicketPurchasePermission_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/queue/10/me")
                        .header("X-User-Id", 7)
                        .header("X-User-Permissions", "EVENT_SEARCH"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));

        verifyNoInteractions(queueService);
    }
}

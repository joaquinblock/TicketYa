package org.group10.queue.controller;

import org.group10.common.exception.TicketYaException;
import org.group10.queue.exception.QueueErrorCode;
import org.group10.queue.service.QueueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QueueController.class)
class QueueEventNotFoundControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QueueService queueService;

    @Test
    void join_eventDoesNotExist_returnsEventNotFound() throws Exception {
        when(queueService.join(10L, 7L)).thenThrow(new TicketYaException(
                QueueErrorCode.EVENT_NOT_FOUND, "No existe el evento 10"));

        mockMvc.perform(post("/api/queue/10/join")
                        .header("X-User-Id", 7)
                        .header("X-User-Permissions", "TICKET_PURCHASE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("event_not_found"));
    }
}

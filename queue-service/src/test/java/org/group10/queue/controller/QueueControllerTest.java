package org.group10.queue.controller;

import org.group10.queue.dto.QueueEntryResponse;
import org.group10.queue.service.QueueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebMvcTest(QueueController.class)
class QueueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QueueService queueService;

    @Test
    void join_newCustomer_returnsWaitingAtPositionOne() throws Exception {
        when(queueService.join(10L, 7L)).thenReturn(new QueueEntryResponse("WAITING", 1, null, null));

        mockMvc.perform(post("/api/queue/10/join")
                        .header("X-User-Id", 7)
                        .header("X-User-Permissions", "TICKET_PURCHASE"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.position").value(1));
        verify(queueService).join(10L, 7L);
    }
}

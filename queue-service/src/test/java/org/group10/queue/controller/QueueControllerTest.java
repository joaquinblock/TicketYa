package org.group10.queue.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
class QueueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void join_newCustomer_returnsWaitingAtPositionOne() throws Exception {
        mockMvc.perform(post("/api/queue/10/join")
                        .header("X-User-Id", 7)
                        .header("X-User-Permissions", "TICKET_PURCHASE"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.position").value(1));
    }
}

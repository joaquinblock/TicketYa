package org.group10.queue.controller;

import org.group10.queue.service.QueueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QueueController.class)
class QueuePermissionsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QueueService queueService;

    @Test
    void join_withoutIdentity_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/queue/10/join"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        verifyNoInteractions(queueService);
    }

    @Test
    void join_withoutTicketPurchasePermission_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/queue/10/join")
                        .header("X-User-Id", 7)
                        .header("X-User-Permissions", "EVENT_SEARCH"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
        verifyNoInteractions(queueService);
    }
}

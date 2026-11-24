package org.group10.notification.controller;

import org.group10.notification.dto.NotificationResponse;
import org.group10.notification.service.NotificationInbox;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La bandeja del usuario del token: cualquier rol autenticado ve solo la suya. */
@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    private static final String NOTIFICATIONS = "/api/notifications";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationInbox inbox;

    @Test
    void findMine_withTheGatewayIdentity_returnsTheCallersNotifications() throws Exception {
        when(inbox.findFor(1L)).thenReturn(List.of(new NotificationResponse(7L, "Tu compra está confirmada",
                "Tus entradas ya están en Mis entradas.", Instant.parse("2026-11-23T21:05:00Z"), false)));

        mockMvc.perform(get(NOTIFICATIONS).header("X-User-Id", 1).header("X-User-Role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].title").value("Tu compra está confirmada"))
                .andExpect(jsonPath("$[0].read").value(false));
    }

    @Test
    void findMine_withoutIdentity_returns401() throws Exception {
        mockMvc.perform(get(NOTIFICATIONS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        verifyNoInteractions(inbox);
    }

    @Test
    void markAllRead_withTheGatewayIdentity_returns204AndMarksTheCallersOnes() throws Exception {
        mockMvc.perform(post(NOTIFICATIONS + "/read-all").header("X-User-Id", 4).header("X-User-Role", "ACCESS_STAFF"))
                .andExpect(status().isNoContent());
        verify(inbox).markAllRead(4L);
    }
}

package org.group10.auth.controller;

import org.group10.auth.dto.AccessAssignmentResponse;
import org.group10.auth.service.AccessAssignmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccessAssignmentController.class)
class OwnAccessAssignmentsControllerTest {

    private static final String OWN_ASSIGNMENTS = "/api/users/me/access-assignments";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccessAssignmentService accessAssignmentService;

    @Test
    void findOwn_accessStaff_returnsItsAssignments() throws Exception {
        when(accessAssignmentService.findOwn(4L)).thenReturn(List.of(
                new AccessAssignmentResponse(4L, 1L), new AccessAssignmentResponse(4L, 6L)));

        mockMvc.perform(get(OWN_ASSIGNMENTS).header("X-User-Id", 4).header("X-User-Role", "ACCESS_STAFF")
                        .header("X-User-Permissions", "TICKET_VALIDATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value(1))
                .andExpect(jsonPath("$[1].eventId").value(6));
    }

    @Test
    void findOwn_withoutIdentity_returns401() throws Exception {
        mockMvc.perform(get(OWN_ASSIGNMENTS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        verifyNoInteractions(accessAssignmentService);
    }

    @Test
    void findOwn_organizerWithoutTicketValidate_returns403() throws Exception {
        mockMvc.perform(get(OWN_ASSIGNMENTS).header("X-User-Id", 2).header("X-User-Role", "ORGANIZER")
                        .header("X-User-Permissions", "EVENT_MANAGE").header("X-Organization-Id", 1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
        verifyNoInteractions(accessAssignmentService);
    }
}

package org.group10.auth.controller;

import org.group10.auth.exception.AuthErrorCode;
import org.group10.auth.service.AccessAssignmentService;
import org.group10.common.exception.TicketYaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalAccessStaffController.class)
class InternalAccessStaffControllerTest {

    private static final String SECRET = "dev-internal-secret";
    private static final String ASSIGNMENT_OF_STAFF_4_TO_EVENT_10 = "/internal/access-staff/4/events/10";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccessAssignmentService accessAssignmentService;

    @Test
    void ensureAssigned_assignedStaff_returns204() throws Exception {
        mockMvc.perform(get(ASSIGNMENT_OF_STAFF_4_TO_EVENT_10).header("X-Internal-Secret", SECRET))
                .andExpect(status().isNoContent());

        verify(accessAssignmentService).ensureAssigned(4L, 10L);
    }

    @Test
    void ensureAssigned_staffNotAssigned_returns403StaffNotAssigned() throws Exception {
        doThrow(new TicketYaException(AuthErrorCode.STAFF_NOT_ASSIGNED, "El usuario 4 no está asignado al evento 10"))
                .when(accessAssignmentService).ensureAssigned(4L, 10L);

        mockMvc.perform(get(ASSIGNMENT_OF_STAFF_4_TO_EVENT_10).header("X-Internal-Secret", SECRET))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("staff_not_assigned"));
    }

    @Test
    void ensureAssigned_withoutTheSecret_returns401WithoutLookingUpTheAssignment() throws Exception {
        mockMvc.perform(get(ASSIGNMENT_OF_STAFF_4_TO_EVENT_10))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        verifyNoInteractions(accessAssignmentService);
    }

    @Test
    void ensureAssigned_withAWrongSecret_returns401() throws Exception {
        mockMvc.perform(get(ASSIGNMENT_OF_STAFF_4_TO_EVENT_10).header("X-Internal-Secret", "otro"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(accessAssignmentService);
    }

    @Test
    void ensureAssigned_userTokenHeadersAreNotEnough_returns401() throws Exception {
        mockMvc.perform(get(ASSIGNMENT_OF_STAFF_4_TO_EVENT_10).header("X-User-Id", 4)
                        .header("X-User-Role", "ACCESS_STAFF").header("X-User-Permissions", "TICKET_VALIDATE"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(accessAssignmentService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/internal/access-staff/0/events/10", "/internal/access-staff/-1/events/10",
            "/internal/access-staff/4/events/0", "/internal/access-staff/4/events/-1"})
    void ensureAssigned_nonPositiveIds_returns400(String path) throws Exception {
        mockMvc.perform(get(path).header("X-Internal-Secret", SECRET))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
        verifyNoInteractions(accessAssignmentService);
    }
}

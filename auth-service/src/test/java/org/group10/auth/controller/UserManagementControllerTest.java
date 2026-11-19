package org.group10.auth.controller;

import org.group10.auth.dto.OrganizerResponse;
import org.group10.auth.service.OrganizationService;
import org.group10.auth.service.OrganizerService;
import org.group10.auth.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lo que solo puede hacer el admin (USER_MANAGE): promover y quitar organizadores, y ver los permisos de un usuario.
 */
@WebMvcTest({OrganizationController.class, UserController.class})
class UserManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrganizationService organizationService;

    @MockitoBean
    private OrganizerService organizerService;

    @MockitoBean
    private UserService userService;

    @Test
    void promote_admin_returns201WithTheNewOrganizer() throws Exception {
        when(organizerService.promote(1L, 7L)).thenReturn(new OrganizerResponse(7L, "Ana", "Gómez", 1L));

        mockMvc.perform(asAdmin(promote("{\"userId\": 7}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.organizationId").value(1));
    }

    @Test
    void promote_withoutIdentity_returns401() throws Exception {
        mockMvc.perform(promote("{\"userId\": 7}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        verifyNoInteractions(organizerService);
    }

    @Test
    void promote_organizer_returns403() throws Exception {
        mockMvc.perform(promote("{\"userId\": 7}").header("X-User-Id", 2)
                        .header("X-User-Permissions", "EVENT_MANAGE,PARTICIPANT_MANAGE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("El usuario 2 no tiene el permiso USER_MANAGE"));
        verifyNoInteractions(organizerService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"userId\": 0}", "{\"userId\": \"abc\"}", "{\"userId\": 1.5}", ""})
    void promote_invalidBody_returns400(String body) throws Exception {
        mockMvc.perform(asAdmin(promote(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
        verifyNoInteractions(organizerService);
    }

    @Test
    void remove_admin_returns204() throws Exception {
        mockMvc.perform(asAdmin(delete("/api/organizations/1/organizers/2")))
                .andExpect(status().isNoContent());

        verify(organizerService).remove(1L, 2L);
    }

    @Test
    void remove_organizer_returns403() throws Exception {
        mockMvc.perform(delete("/api/organizations/1/organizers/6").header("X-User-Id", 2)
                        .header("X-User-Permissions", "EVENT_MANAGE"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(organizerService);
    }

    @Test
    void permissionsOfAUser_withoutIdentity_returns401() throws Exception {
        mockMvc.perform(get("/api/users/1/permissions"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(userService);
    }

    @Test
    void permissionsOfAUser_admin_returns200() throws Exception {
        mockMvc.perform(asAdmin(get("/api/users/1/permissions"))).andExpect(status().isOk());

        verify(userService).findPermissions(1L);
    }

    @Test
    void organization_isStillPublic() throws Exception {
        mockMvc.perform(get("/api/organizations/1")).andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder promote(String body) {
        return post("/api/organizations/1/organizers").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", 3).header("X-User-Role", "ADMIN")
                .header("X-User-Permissions", "EVENT_ADMIN,USER_MANAGE");
    }
}

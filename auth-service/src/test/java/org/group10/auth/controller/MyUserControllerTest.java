package org.group10.auth.controller;

import org.group10.auth.dto.UserContactResponse;
import org.group10.auth.exception.AuthErrorCode;
import org.group10.auth.service.UserService;
import org.group10.common.exception.TicketYaException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los datos del usuario que llama, tomados del token: cualquier rol autenticado puede verlos.
 */
@WebMvcTest(UserController.class)
class MyUserControllerTest {

    private static final String ME = "/api/users/me";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void findMe_withTheGatewayIdentity_returns200WithTheCallersEmailAndName() throws Exception {
        when(userService.findContact(1L))
                .thenReturn(new UserContactResponse(1L, "client@ticketya.com", "Juan", "Pérez"));

        mockMvc.perform(get(ME).header("X-User-Id", 1).header("X-User-Role", "CLIENT")
                        .header("X-User-Permissions", "EVENT_SEARCH,TICKET_PURCHASE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.email").value("client@ticketya.com"))
                .andExpect(jsonPath("$.firstName").value("Juan"))
                .andExpect(jsonPath("$.lastName").value("Pérez"));
    }

    @Test
    void findMe_withoutIdentity_returns401WithoutLookingUpTheUser() throws Exception {
        mockMvc.perform(get(ME))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        verifyNoInteractions(userService);
    }

    @Test
    void findMe_deletedUser_returns404() throws Exception {
        when(userService.findContact(8L)).thenThrow(new TicketYaException(AuthErrorCode.USER_NOT_FOUND,
                "No se encontró el usuario con id 8"));

        mockMvc.perform(get(ME).header("X-User-Id", 8).header("X-User-Role", "CLIENT"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("user_not_found"));
    }
}

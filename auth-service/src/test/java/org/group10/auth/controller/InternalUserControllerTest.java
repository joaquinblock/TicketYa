package org.group10.auth.controller;

import org.group10.auth.dto.UserContactResponse;
import org.group10.auth.exception.AuthErrorCode;
import org.group10.auth.service.UserService;
import org.group10.common.exception.TicketYaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
 * El contacto de un usuario, que pide el notification-service: solo con el secreto entre servicios.
 */
@WebMvcTest(InternalUserController.class)
class InternalUserControllerTest {

    private static final String SECRET = "dev-internal-secret";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void findContact_withTheSecret_returns200WithEmailAndName() throws Exception {
        when(userService.findContact(1L))
                .thenReturn(new UserContactResponse(1L, "client@ticketya.com", "Juan", "Pérez"));

        mockMvc.perform(get("/internal/users/1").header("X-Internal-Secret", SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.email").value("client@ticketya.com"))
                .andExpect(jsonPath("$.firstName").value("Juan"))
                .andExpect(jsonPath("$.lastName").value("Pérez"));
    }

    @Test
    void findContact_withoutTheSecret_returns401WithoutLookingUpTheUser() throws Exception {
        mockMvc.perform(get("/internal/users/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        verifyNoInteractions(userService);
    }

    @Test
    void findContact_withAWrongSecret_returns401() throws Exception {
        mockMvc.perform(get("/internal/users/1").header("X-Internal-Secret", "otro"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(userService);
    }

    @Test
    void findContact_userTokenHeadersAreNotEnough_returns401() throws Exception {
        mockMvc.perform(get("/internal/users/1").header("X-User-Id", 3).header("X-User-Role", "ADMIN")
                        .header("X-User-Permissions", "USER_MANAGE"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(userService);
    }

    @Test
    void findContact_missingOrDeletedUser_returns404() throws Exception {
        when(userService.findContact(99L)).thenThrow(new TicketYaException(AuthErrorCode.USER_NOT_FOUND,
                "No se encontró el usuario con id 99"));

        mockMvc.perform(get("/internal/users/99").header("X-Internal-Secret", SECRET))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("user_not_found"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void findContact_nonPositiveId_returns400(String id) throws Exception {
        mockMvc.perform(get("/internal/users/" + id).header("X-Internal-Secret", SECRET))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
        verifyNoInteractions(userService);
    }

    @Test
    void findContact_nonNumericId_returns400() throws Exception {
        mockMvc.perform(get("/internal/users/abc").header("X-Internal-Secret", SECRET))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(userService);
    }
}

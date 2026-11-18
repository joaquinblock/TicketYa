package org.group10.auth.controller;

import org.group10.auth.dto.TokenResponse;
import org.group10.auth.exception.AuthErrorCode;
import org.group10.auth.service.AuthService;
import org.group10.common.exception.TicketYaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DevAuthController.class)
@ActiveProfiles("dev")
class DevAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void devLogin_existingUser_returnsTheBearerToken() throws Exception {
        when(authService.issueTokenFor(2L)).thenReturn(new TokenResponse("signed-token", "Bearer", 1800));

        mockMvc.perform(devLogin("""
                        {"userId": 2}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("signed-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800));
    }

    @Test
    void devLogin_missingOrDeletedUser_returns404UserNotFound() throws Exception {
        when(authService.issueTokenFor(99L))
                .thenThrow(new TicketYaException(AuthErrorCode.USER_NOT_FOUND, "No se encontró el usuario con id 99"));

        mockMvc.perform(devLogin("""
                        {"userId": 99}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("user_not_found"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"userId\": null}",
            "{\"userId\": 0}",
            "{\"userId\": -1}",
            "{\"userId\": \"abc\"}",
            "{\"userId\": 1.5}",
            "{\"userId\": ",
            "[]",
            ""
    })
    void devLogin_invalidBody_returns400WithoutIssuing(String body) throws Exception {
        mockMvc.perform(devLogin(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
        verifyNoInteractions(authService);
    }

    private static MockHttpServletRequestBuilder devLogin(String body) {
        return post("/api/auth/dev-login").contentType(MediaType.APPLICATION_JSON).content(body);
    }
}

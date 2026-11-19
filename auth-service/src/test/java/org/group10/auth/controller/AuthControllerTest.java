package org.group10.auth.controller;

import org.group10.auth.dto.LoginResponse;
import org.group10.auth.exception.AuthErrorCode;
import org.group10.auth.service.AuthService;
import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void googleLogin_validToken_returnsTheTokenAndWhetherTheUserIsNew() throws Exception {
        when(authService.loginWithGoogle("google-id-token"))
                .thenReturn(new LoginResponse("signed-token", "Bearer", 1800, true));

        mockMvc.perform(googleLogin("""
                        {"idToken": "google-id-token"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("signed-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.newUser").value(true));
    }

    @Test
    void googleLogin_invalidToken_returns401InvalidCredentials() throws Exception {
        when(authService.loginWithGoogle("bad"))
                .thenThrow(new TicketYaException(AuthErrorCode.INVALID_CREDENTIALS, "Token inválido"));

        mockMvc.perform(googleLogin("""
                        {"idToken": "bad"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }

    @Test
    void googleLogin_deletedUser_returns403UserDisabled() throws Exception {
        when(authService.loginWithGoogle("token"))
                .thenThrow(new TicketYaException(AuthErrorCode.USER_DISABLED, "Tu cuenta está dada de baja"));

        mockMvc.perform(googleLogin("""
                        {"idToken": "token"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("user_disabled"));
    }

    @Test
    void googleLogin_notConfigured_returns503() throws Exception {
        when(authService.loginWithGoogle("token")).thenThrow(new TicketYaException(
                CommonErrorCode.SERVICE_UNAVAILABLE, "El login con Google no está configurado"));

        mockMvc.perform(googleLogin("""
                        {"idToken": "token"}"""))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("service_unavailable"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"idToken\": null}", "{\"idToken\": \"\"}", "{\"idToken\": \"   \"}",
            "{\"idToken\": ", ""})
    void googleLogin_withoutToken_returns400WithoutCallingTheService(String body) throws Exception {
        mockMvc.perform(googleLogin(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
        verifyNoInteractions(authService);
    }

    private static MockHttpServletRequestBuilder googleLogin(String body) {
        return post("/api/auth/google").contentType(MediaType.APPLICATION_JSON).content(body);
    }
}

package org.group10.payment.controller;

import org.group10.payment.gateway.MockGatewayBehavior;
import org.group10.payment.gateway.MockPaymentGateway;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DevMockGatewayController.class)
@ActiveProfiles("dev")
class DevMockGatewayControllerTest {

    private static final String PATH = "/internal/mock/gateway";
    private static final String SECRET_HEADER = "X-Internal-Secret";
    private static final String SECRET = "dev-internal-secret";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MockPaymentGateway mockPaymentGateway;

    @Test
    void behavior_returnsHowTheGatewayIsAnsweringNow() throws Exception {
        when(mockPaymentGateway.behavior()).thenReturn(new MockGatewayBehavior(1500, true));

        mockMvc.perform(get(PATH).header(SECRET_HEADER, SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delayMs").value(1500))
                .andExpect(jsonPath("$.down").value(true));
    }

    @Test
    void changeBehavior_validBody_changesTheGatewayAndReturnsTheNewBehavior() throws Exception {
        MockGatewayBehavior newBehavior = new MockGatewayBehavior(0, true);
        when(mockPaymentGateway.behavior()).thenReturn(newBehavior);

        mockMvc.perform(changeBehavior("""
                        {"delayMs": 0, "down": true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delayMs").value(0))
                .andExpect(jsonPath("$.down").value(true));
        verify(mockPaymentGateway).changeBehavior(newBehavior);
    }

    @Test
    void changeBehavior_maximumDelay_isAccepted() throws Exception {
        when(mockPaymentGateway.behavior()).thenReturn(new MockGatewayBehavior(60_000, false));

        mockMvc.perform(changeBehavior("""
                        {"delayMs": 60000, "down": false}"""))
                .andExpect(status().isOk());
        verify(mockPaymentGateway).changeBehavior(new MockGatewayBehavior(60_000, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"delayMs\": -1, \"down\": false}",
            "{\"delayMs\": 60001, \"down\": false}",
            "{\"delayMs\": 1.5, \"down\": false}",
            "{\"down\": false}",
            "{\"delayMs\": 0}",
            "{}"
    })
    void changeBehavior_invalidBody_returns400WithoutChangingTheGateway(String body) throws Exception {
        mockMvc.perform(changeBehavior(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
        verify(mockPaymentGateway, never()).changeBehavior(any());
    }

    @Test
    void changeBehavior_withoutTheServiceSecret_returns401WithoutChangingTheGateway() throws Exception {
        mockMvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content("""
                        {"delayMs": 0, "down": true}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        verifyNoInteractions(mockPaymentGateway);
    }

    private static MockHttpServletRequestBuilder changeBehavior(String body) {
        return put(PATH).header(SECRET_HEADER, SECRET).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}

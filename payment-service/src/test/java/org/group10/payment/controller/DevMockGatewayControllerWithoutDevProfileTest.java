package org.group10.payment.controller;

import org.group10.payment.gateway.MockPaymentGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DevMockGatewayController.class)
class DevMockGatewayControllerWithoutDevProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MockPaymentGateway mockPaymentGateway;

    @Test
    void changeBehavior_withoutDevProfile_doesNotExist() throws Exception {
        mockMvc.perform(put("/internal/mock/gateway").header("X-Internal-Secret", "dev-internal-secret")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"delayMs": 0, "down": true}"""))
                .andExpect(status().isNotFound());
        verifyNoInteractions(mockPaymentGateway);
    }
}

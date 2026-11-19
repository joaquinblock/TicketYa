package org.group10.payment.controller;

import org.group10.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ver los pagos de una orden es solo del administrador (US-PAY-14): lo usa para resolver reclamos y devoluciones.
 */
@WebMvcTest(PaymentQueryController.class)
class PaymentQueryPermissionsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void paymentsOfAnOrder_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/payments").param("orderId", "2"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        verifyNoInteractions(paymentService);
    }

    @Test
    void paymentsOfAnOrder_client_returns403() throws Exception {
        mockMvc.perform(get("/api/payments").param("orderId", "2").header("X-User-Id", 1)
                        .header("X-User-Permissions", "EVENT_SEARCH,TICKET_PURCHASE,REFUND_REQUEST"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("El usuario 1 no tiene el permiso REFUND_RESOLVE"));
        verifyNoInteractions(paymentService);
    }

    @Test
    void paymentsOfAnOrder_admin_returnsThem() throws Exception {
        when(paymentService.findPaymentsOfOrder(2L)).thenReturn(List.of());

        mockMvc.perform(get("/api/payments").param("orderId", "2").header("X-User-Id", 3)
                        .header("X-User-Permissions", "EVENT_ADMIN,REFUND_RESOLVE,USER_MANAGE"))
                .andExpect(status().isOk());

        verify(paymentService).findPaymentsOfOrder(2L);
    }
}

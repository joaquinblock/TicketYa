package org.group10.payment.controller;

import org.group10.common.exception.TicketYaException;
import org.group10.payment.exception.PaymentErrorCode;
import org.group10.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void charge_rejectedPayment_returns422WithTheReasonAndTheRejectedAttemptsThatTicketServiceReads() throws Exception {
        when(paymentService.charge(any())).thenThrow(new TicketYaException(PaymentErrorCode.PAYMENT_REJECTED,
                "El pago fue rechazado: Fondos insuficientes",
                Map.of("reason", "Fondos insuficientes", "rejectedAttempts", 1L)));

        mockMvc.perform(post("/internal/payments").contentType(MediaType.APPLICATION_JSON).content("""
                        {"orderId": 10, "amount": 25000.00, "paymentMethod": "CREDIT_CARD", "idempotencyKey": "attempt-1"}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value("payment_rejected"))
                .andExpect(jsonPath("$.detail.reason").value("Fondos insuficientes"))
                .andExpect(jsonPath("$.detail.rejectedAttempts").value(1));
    }
}

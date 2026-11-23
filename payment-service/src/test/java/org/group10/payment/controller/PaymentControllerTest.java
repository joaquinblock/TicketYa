package org.group10.payment.controller;

import org.group10.common.exception.TicketYaException;
import org.group10.payment.dto.PaymentResponse;
import org.group10.payment.entity.PaymentMethod;
import org.group10.payment.entity.PaymentStatus;
import org.group10.payment.exception.PaymentErrorCode;
import org.group10.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

        mockMvc.perform(post("/internal/payments").header("X-Internal-Secret", "dev-internal-secret").contentType(MediaType.APPLICATION_JSON).content("""
                        {"orderId": 10, "amount": 25000.00, "paymentMethod": "CREDIT_CARD", "idempotencyKey": "attempt-1"}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value("payment_rejected"))
                .andExpect(jsonPath("$.detail.reason").value("Fondos insuficientes"))
                .andExpect(jsonPath("$.detail.rejectedAttempts").value(1));
    }

    @Test
    void charge_withoutTheServiceSecret_returns401WithoutCharging() throws Exception {
        mockMvc.perform(post("/internal/payments").contentType(MediaType.APPLICATION_JSON).content("""
                        {"orderId": 10, "amount": 25000.00, "paymentMethod": "CREDIT_CARD", "idempotencyKey": "attempt-1"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        verifyNoInteractions(paymentService);
    }

    @Test
    void cancel_confirmedPayment_returns200WithTheFinalStatus() throws Exception {
        PaymentResponse response = new PaymentResponse(7L, 10L, new BigDecimal("25000.00"),
                PaymentMethod.CREDIT_CARD, PaymentStatus.CANCELLED, "MOCK-1");
        when(paymentService.cancel("attempt-1")).thenReturn(response);

        mockMvc.perform(post("/internal/payments/cancel")
                        .header("X-Internal-Secret", "dev-internal-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"attempt-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.orderId").value(10))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.gatewayId").value("MOCK-1"));

        verify(paymentService).cancel("attempt-1");
    }

    @Test
    void cancel_unknownIdempotencyKey_returns404PaymentNotFound() throws Exception {
        when(paymentService.cancel("unknown"))
                .thenThrow(new TicketYaException(PaymentErrorCode.PAYMENT_NOT_FOUND, "Pago no encontrado"));

        mockMvc.perform(post("/internal/payments/cancel")
                        .header("X-Internal-Secret", "dev-internal-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"unknown\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("payment_not_found"));
    }

    @Test
    void cancel_withoutIdempotencyKey_returnsValidationError() throws Exception {
        mockMvc.perform(post("/internal/payments/cancel")
                        .header("X-Internal-Secret", "dev-internal-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));

        verifyNoInteractions(paymentService);
    }

    @Test
    void cancel_withoutTheServiceSecret_returns401WithoutCancelling() throws Exception {
        mockMvc.perform(post("/internal/payments/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"attempt-1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));

        verifyNoInteractions(paymentService);
    }
}

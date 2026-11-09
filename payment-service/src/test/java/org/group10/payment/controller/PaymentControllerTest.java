package org.group10.payment.controller;

import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.group10.payment.dto.PaymentResponse;
import org.group10.payment.entity.PaymentMethod;
import org.group10.payment.entity.PaymentStatus;
import org.group10.payment.exception.PaymentErrorCode;
import org.group10.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    private static final String URL = "/internal/payments";
    private static final String VALID_PAYMENT = """
            {"orderId": 10, "amount": 25000.00, "paymentMethod": "CREDIT_CARD", "idempotencyKey": "attempt-1"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void charge_validRequest_returns201WithTheApprovedPayment() throws Exception {
        when(paymentService.charge(any())).thenReturn(approvedPaymentWith(PaymentMethod.CREDIT_CARD));

        postPayment(VALID_PAYMENT)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.paymentMethod").value("CREDIT_CARD"))
                .andExpect(jsonPath("$.amount").value(25000.00))
                .andExpect(jsonPath("$.gatewayId").value("MOCK-1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREDIT_CARD", "DEBIT_CARD", "ACCOUNT_MONEY"})
    void charge_everyAcceptedMethod_returns201(String method) throws Exception {
        when(paymentService.charge(any())).thenReturn(approvedPaymentWith(PaymentMethod.valueOf(method)));

        postPayment(VALID_PAYMENT.replace("CREDIT_CARD", method)).andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"amount\": 100, \"paymentMethod\": \"CREDIT_CARD\", \"idempotencyKey\": \"k\"}",
            "{\"orderId\": 10, \"paymentMethod\": \"CREDIT_CARD\", \"idempotencyKey\": \"k\"}",
            "{\"orderId\": 10, \"amount\": 100, \"idempotencyKey\": \"k\"}",
            "{\"orderId\": 10, \"amount\": 100, \"paymentMethod\": \"CREDIT_CARD\"}",
            "{\"orderId\": 10, \"amount\": 0, \"paymentMethod\": \"CREDIT_CARD\", \"idempotencyKey\": \"k\"}",
            "{\"orderId\": 10, \"amount\": -5, \"paymentMethod\": \"CREDIT_CARD\", \"idempotencyKey\": \"k\"}",
            "{\"orderId\": 10, \"amount\": 100, \"paymentMethod\": \"CASH\", \"idempotencyKey\": \"k\"}"
    })
    void charge_invalidRequest_returns400WithoutCharging(String body) throws Exception {
        postPayment(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));

        verifyNoInteractions(paymentService);
    }

    @Test
    void charge_rejectedPayment_returns422WithTheReasonAndTheRejectedAttempts() throws Exception {
        when(paymentService.charge(any())).thenThrow(new TicketYaException(PaymentErrorCode.PAYMENT_REJECTED,
                "El pago fue rechazado: Fondos insuficientes",
                Map.of("reason", "Fondos insuficientes", "rejectedAttempts", 1L)));

        postPayment(VALID_PAYMENT)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value("payment_rejected"))
                .andExpect(jsonPath("$.detail.reason").value("Fondos insuficientes"))
                .andExpect(jsonPath("$.detail.rejectedAttempts").value(1));
    }

    @Test
    void charge_orderWithAPendingOrApprovedPayment_returns409() throws Exception {
        when(paymentService.charge(any())).thenThrow(
                new TicketYaException(CommonErrorCode.DATA_CONFLICT, "La orden 10 ya tiene un pago en curso o aprobado"));

        postPayment(VALID_PAYMENT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("data_conflict"));
    }

    private ResultActions postPayment(String body) throws Exception {
        return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private PaymentResponse approvedPaymentWith(PaymentMethod method) {
        return new PaymentResponse(1L, 10L, new BigDecimal("25000.00"), method, PaymentStatus.APPROVED, "MOCK-1");
    }
}

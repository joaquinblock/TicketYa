package org.group10.payment.controller;

import org.group10.common.exception.TicketYaException;
import org.group10.payment.dto.OrderPaymentResponse;
import org.group10.payment.entity.PaymentMethod;
import org.group10.payment.entity.PaymentStatus;
import org.group10.payment.exception.PaymentErrorCode;
import org.group10.payment.service.PaymentService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PaymentController.class, PaymentQueryController.class})
class PaymentQueryControllerTest {

    private static final Long ORDER_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @ParameterizedTest
    @ValueSource(strings = {"/api/payments", "/internal/payments"})
    void findPaymentsOfOrder_orderWithPayments_returns200WithTheDetailOfEachOne(String url) throws Exception {
        when(paymentService.findPaymentsOfOrder(ORDER_ID)).thenReturn(List.of(
                new OrderPaymentResponse(1L, ORDER_ID, new BigDecimal("25000.00"), PaymentMethod.DEBIT_CARD,
                        PaymentStatus.REJECTED, "Fondos insuficientes", "MOCK-1", LocalDateTime.of(2026, 11, 10, 10, 30)),
                new OrderPaymentResponse(2L, ORDER_ID, new BigDecimal("25000.00"), PaymentMethod.CREDIT_CARD,
                        PaymentStatus.APPROVED, null, "MOCK-2", LocalDateTime.of(2026, 11, 10, 10, 31))));

        mockMvc.perform(get(url).param("orderId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].paymentMethod").value("DEBIT_CARD"))
                .andExpect(jsonPath("$[0].amount").value(25000.00))
                .andExpect(jsonPath("$[0].status").value("REJECTED"))
                .andExpect(jsonPath("$[0].failureReason").value("Fondos insuficientes"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-11-10T10:30:00"))
                .andExpect(jsonPath("$[1].status").value("APPROVED"))
                .andExpect(jsonPath("$[1].failureReason").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/payments", "/internal/payments"})
    void findPaymentsOfOrder_orderWithoutPayments_returns404(String url) throws Exception {
        when(paymentService.findPaymentsOfOrder(ORDER_ID))
                .thenThrow(new TicketYaException(PaymentErrorCode.PAYMENT_NOT_FOUND, "La orden 10 no tiene pagos"));

        mockMvc.perform(get(url).param("orderId", "10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("payment_not_found"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/payments", "/internal/payments"})
    void findPaymentsOfOrder_withoutOrderId_returns400InvalidParameter(String url) throws Exception {
        mockMvc.perform(get(url))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_parameter"))
                .andExpect(jsonPath("$.message").value("Falta el parámetro obligatorio 'orderId'"));

        verifyNoInteractions(paymentService);
    }

    @ParameterizedTest
    @CsvSource({
            "/api/payments, abc", "/api/payments, 1.5", "/api/payments, 9223372036854775808",
            "/internal/payments, abc", "/internal/payments, 1.5", "/internal/payments, 9223372036854775808"
    })
    void findPaymentsOfOrder_orderIdThatIsNotALong_returns400InvalidParameter(String url, String orderId) throws Exception {
        mockMvc.perform(get(url).param("orderId", orderId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_parameter"));

        verifyNoInteractions(paymentService);
    }

    @ParameterizedTest
    @CsvSource({"/api/payments, 0", "/api/payments, -1", "/internal/payments, 0", "/internal/payments, -1"})
    void findPaymentsOfOrder_orderIdNotPositive_returns400WithTheParameter(String url, String orderId) throws Exception {
        mockMvc.perform(get(url).param("orderId", orderId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"))
                .andExpect(jsonPath("$.detail.orderId").exists());

        verifyNoInteractions(paymentService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/payments", "/internal/payments"})
    void findPaymentsOfOrder_emptyOrderId_returns400InvalidParameter(String url) throws Exception {
        mockMvc.perform(get(url).param("orderId", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_parameter"));

        verifyNoInteractions(paymentService);
    }
}

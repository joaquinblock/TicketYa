package org.group10.payment.controller;

import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.group10.payment.dto.RefundOutcome;
import org.group10.payment.dto.RefundResponse;
import org.group10.payment.entity.RefundReason;
import org.group10.payment.entity.RefundStatus;
import org.group10.payment.exception.PaymentErrorCode;
import org.group10.payment.service.RefundService;
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

@WebMvcTest(RefundController.class)
class RefundControllerTest {

    private static final String URL = "/internal/refunds";
    private static final String RETURN_REQUEST_REFUND = """
            {"ticketId": 7, "orderId": 10, "amount": 25000.00, "reason": "RETURN_REQUEST", "returnRequestId": 3}""";
    private static final String CANCELLATION_REFUND = """
            {"ticketId": 7, "orderId": 10, "amount": 25000.00, "reason": "EVENT_CANCELLED"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RefundService refundService;

    @Test
    void refund_newRefund_returns201WithTheCreditedRefund() throws Exception {
        when(refundService.refund(any())).thenReturn(creditedOutcome(RefundReason.RETURN_REQUEST, 3L, true));

        postRefund(RETURN_REQUEST_REFUND)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREDITED"))
                .andExpect(jsonPath("$.reason").value("RETURN_REQUEST"))
                .andExpect(jsonPath("$.returnRequestId").value(3))
                .andExpect(jsonPath("$.ticketId").value(7))
                .andExpect(jsonPath("$.amount").value(25000.00))
                .andExpect(jsonPath("$.gatewayId").value("MOCK-1"));
    }

    @Test
    void refund_alreadyRegisteredRefund_returns200() throws Exception {
        when(refundService.refund(any())).thenReturn(creditedOutcome(RefundReason.RETURN_REQUEST, 3L, false));

        postRefund(RETURN_REQUEST_REFUND).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"EVENT_CANCELLED", "PURCHASE_COMPENSATION"})
    void refund_reasonWithoutReturnRequest_isAcceptedAndInformedInTheResponse(String reason) throws Exception {
        when(refundService.refund(any())).thenReturn(creditedOutcome(RefundReason.valueOf(reason), null, true));

        postRefund(CANCELLATION_REFUND.replace("EVENT_CANCELLED", reason))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reason").value(reason));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"orderId\": 10, \"amount\": 100, \"reason\": \"EVENT_CANCELLED\"}",
            "{\"ticketId\": 7, \"amount\": 100, \"reason\": \"EVENT_CANCELLED\"}",
            "{\"ticketId\": 7, \"orderId\": 10, \"reason\": \"EVENT_CANCELLED\"}",
            "{\"ticketId\": 7, \"orderId\": 10, \"amount\": 100}",
            "{\"ticketId\": 0, \"orderId\": 10, \"amount\": 100, \"reason\": \"EVENT_CANCELLED\"}",
            "{\"ticketId\": -1, \"orderId\": 10, \"amount\": 100, \"reason\": \"EVENT_CANCELLED\"}",
            "{\"ticketId\": 1.5, \"orderId\": 10, \"amount\": 100, \"reason\": \"EVENT_CANCELLED\"}",
            "{\"ticketId\": 7, \"orderId\": 0, \"amount\": 100, \"reason\": \"EVENT_CANCELLED\"}",
            "{\"ticketId\": 7, \"orderId\": 10, \"amount\": 0, \"reason\": \"EVENT_CANCELLED\"}",
            "{\"ticketId\": 7, \"orderId\": 10, \"amount\": -5, \"reason\": \"EVENT_CANCELLED\"}",
            "{\"ticketId\": 7, \"orderId\": 10, \"amount\": 100.001, \"reason\": \"EVENT_CANCELLED\"}",
            "{\"ticketId\": 7, \"orderId\": 10, \"amount\": 12345678901, \"reason\": \"EVENT_CANCELLED\"}",
            "{\"ticketId\": 7, \"orderId\": 10, \"amount\": 100, \"reason\": \"OTHER\"}",
            "{\"ticketId\": 7, \"orderId\": 10, \"amount\": 100, \"reason\": \"event_cancelled\"}",
            "{\"ticketId\": 7, \"orderId\": 10, \"amount\": 100, \"reason\": 0}",
            "{\"ticketId\": 7, \"orderId\": 10, \"amount\": 100, \"reason\": \"RETURN_REQUEST\"}",
            "{\"ticketId\": 7, \"orderId\": 10, \"amount\": 100, \"reason\": \"RETURN_REQUEST\", \"returnRequestId\": 0}",
            "{\"ticketId\": 7, \"orderId\": 10, \"amount\": 100, \"reason\": \"EVENT_CANCELLED\", \"returnRequestId\": 3}",
            "{\"ticketId\": 7, \"orderId\": 10, \"amount\": 100, \"reason\": \"PURCHASE_COMPENSATION\", \"returnRequestId\": 3}",
            "{}",
            "[]",
            "{\"ticketId\": 7",
            "no es json"
    })
    void refund_invalidBody_returns400WithoutCallingTheService(String body) throws Exception {
        postRefund(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));

        verifyNoInteractions(refundService);
    }

    @Test
    void refund_missingReturnRequestId_informsTheFieldInDetail() throws Exception {
        postRefund("{\"ticketId\": 7, \"orderId\": 10, \"amount\": 100, \"reason\": \"RETURN_REQUEST\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail.returnRequestIdPresentOnlyForAReturnRequest").isNotEmpty());
    }

    @Test
    void refund_paymentNotFound_returns404() throws Exception {
        when(refundService.refund(any()))
                .thenThrow(new TicketYaException(PaymentErrorCode.PAYMENT_NOT_FOUND, "Sin pagos"));

        postRefund(CANCELLATION_REFUND)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("payment_not_found"));
    }

    @Test
    void refund_paymentNotRefundable_returns409() throws Exception {
        when(refundService.refund(any()))
                .thenThrow(new TicketYaException(PaymentErrorCode.PAYMENT_NOT_REFUNDABLE, "Sin pago aprobado"));

        postRefund(CANCELLATION_REFUND)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("payment_not_refundable"));
    }

    @Test
    void refund_otherDataForTheSameTicket_returns409() throws Exception {
        when(refundService.refund(any()))
                .thenThrow(new TicketYaException(CommonErrorCode.DATA_CONFLICT, "Otros datos"));

        postRefund(CANCELLATION_REFUND)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("data_conflict"));
    }

    @Test
    void refund_gatewayDoesNotCredit_returns502WithTheReason() throws Exception {
        when(refundService.refund(any())).thenThrow(new TicketYaException(PaymentErrorCode.REFUND_FAILED,
                "No acreditó", Map.of("reason", "Cuenta cerrada")));

        postRefund(CANCELLATION_REFUND)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("refund_failed"))
                .andExpect(jsonPath("$.detail.reason").value("Cuenta cerrada"));
    }

    private ResultActions postRefund(String body) throws Exception {
        return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private RefundOutcome creditedOutcome(RefundReason reason, Long returnRequestId, boolean created) {
        return new RefundOutcome(new RefundResponse(1L, 7L, 2L, new BigDecimal("25000.00"), reason, returnRequestId,
                RefundStatus.CREDITED, "MOCK-1"), created);
    }
}

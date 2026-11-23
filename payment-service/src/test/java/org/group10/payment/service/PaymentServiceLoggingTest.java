package org.group10.payment.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.group10.common.exception.TicketYaException;
import org.group10.payment.dto.PaymentRequest;
import org.group10.payment.entity.Payment;
import org.group10.payment.entity.PaymentMethod;
import org.group10.payment.entity.PaymentStatus;
import org.group10.payment.exception.PaymentErrorCode;
import org.group10.payment.gateway.ChargeResult;
import org.group10.payment.gateway.PaymentGateway;
import org.group10.payment.repository.PaymentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.KeyValuePair;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceLoggingTest {

    private static final String CORRELATION_ID = "ticket-123.abc:xyz";
    private static final Long ORDER_ID = 10L;
    private static final String IDEMPOTENCY_KEY = "attempt-1";
    private static final PaymentRequest REQUEST =
            new PaymentRequest(ORDER_ID, new BigDecimal("25000.00"), PaymentMethod.CREDIT_CARD, IDEMPOTENCY_KEY);

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks
    private PaymentService paymentService;

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void capturePaymentLogs() {
        MDC.put("correlationId", CORRELATION_ID);
        logger = (Logger) LoggerFactory.getLogger(PaymentService.class);
        appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void stopCapturingAndClearMdc() {
        logger.detachAppender(appender);
        appender.stop();
        MDC.remove("correlationId");
    }

    @Test
    void charge_approvedByTheGateway_logsItsResultWithoutSensitivePaymentData() {
        givenNewPayment(ChargeResult.approved("MOCK-1"));

        paymentService.charge(REQUEST);

        Map<String, Object> fields = fieldsOfOutcome("charge");
        assertThat(fields).containsEntry("result", "APPROVED")
                .containsEntry("paymentMethod", "CREDIT_CARD")
                .containsEntry("paymentId", 7L);
        assertThat(outcome("charge").getMDCPropertyMap()).containsEntry("correlationId", CORRELATION_ID);
        assertThat(fields.keySet()).doesNotContain("cardNumber", "cvv", "securityCode");
    }

    @Test
    void charge_rejectedByTheGateway_logsTheRejectedResult() {
        givenNewPayment(ChargeResult.rejected("MOCK-1", "Insufficient funds"));
        when(paymentRepository.countByOrderIdAndStatusAndIdLessThanEqual(
                eq(ORDER_ID), eq(PaymentStatus.REJECTED), any())).thenReturn(1L);

        assertThatThrownBy(() -> paymentService.charge(REQUEST))
                .isInstanceOfSatisfying(TicketYaException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_REJECTED));

        assertThat(fieldsOfOutcome("charge")).containsEntry("result", "REJECTED")
                .containsEntry("paymentMethod", "CREDIT_CARD");
    }

    @Test
    void charge_gatewayUnavailable_logsFailureAndRethrowsTheError() {
        givenNewPayment(null);
        when(paymentGateway.charge(any())).thenThrow(new IllegalStateException("gateway down"));

        assertThatThrownBy(() -> paymentService.charge(REQUEST))
                .isInstanceOfSatisfying(TicketYaException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE));

        assertThat(fieldsOfOutcome("charge")).containsEntry("result", "FAILED")
                .containsEntry("paymentMethod", "CREDIT_CARD");
    }

    private void givenNewPayment(ChargeResult chargeResult) {
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(paymentRepository.findByActiveOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(7L);
            return payment;
        });
        if (chargeResult != null) {
            when(paymentGateway.charge(any())).thenReturn(chargeResult);
        }
    }

    private Map<String, Object> fieldsOfOutcome(String operation) {
        return outcome(operation).getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value, (first, second) -> second));
    }

    private ILoggingEvent outcome(String operation) {
        return appender.list.stream()
                .filter(event -> event.getKeyValuePairs().stream()
                        .anyMatch(pair -> pair.key.equals("operation") && pair.value.equals(operation)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No structured " + operation + " log was emitted"));
    }
}

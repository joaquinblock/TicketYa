package org.group10.payment.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.group10.common.exception.TicketYaException;
import org.group10.payment.dto.RefundRequest;
import org.group10.payment.entity.Payment;
import org.group10.payment.entity.PaymentStatus;
import org.group10.payment.entity.Refund;
import org.group10.payment.entity.RefundReason;
import org.group10.payment.entity.RefundStatus;
import org.group10.payment.gateway.PaymentGateway;
import org.group10.payment.gateway.RefundResult;
import org.group10.payment.repository.PaymentRepository;
import org.group10.payment.repository.RefundRepository;
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
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceLoggingTest {

    private static final String CORRELATION_ID = "ticket-123.abc:xyz";
    private static final Long ORDER_ID = 10L;
    private static final Long TICKET_ID = 20L;
    private static final Long PAYMENT_ID = 30L;
    private static final Long REFUND_ID = 40L;
    private static final BigDecimal AMOUNT = new BigDecimal("25.00");
    private static final RefundRequest REQUEST =
            new RefundRequest(TICKET_ID, ORDER_ID, AMOUNT, RefundReason.EVENT_CANCELLED, null);

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private RefundRepository refundRepository;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private TransactionOperations transaction;

    @InjectMocks
    private RefundService refundService;

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;
    private Payment payment;

    @BeforeEach
    void captureRefundLogsAndRunTransactions() {
        MDC.put("correlationId", CORRELATION_ID);
        logger = (Logger) LoggerFactory.getLogger(RefundService.class);
        appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);

        when(transaction.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });

        payment = Payment.builder()
                .id(PAYMENT_ID)
                .orderId(ORDER_ID)
                .amount(new BigDecimal("100.00"))
                .status(PaymentStatus.APPROVED)
                .build();
        when(paymentRepository.findForUpdateByActiveOrderIdAndStatus(ORDER_ID, PaymentStatus.APPROVED))
                .thenReturn(Optional.of(payment));
    }

    @AfterEach
    void stopCapturingAndClearMdc() {
        logger.detachAppender(appender);
        appender.stop();
        MDC.remove("correlationId");
    }

    @Test
    void refund_creditedByTheGateway_logsItsResult() {
        givenNewRefund();
        when(paymentGateway.refund(any())).thenReturn(RefundResult.credited("MOCK-REFUND-1"));

        refundService.refund(REQUEST);

        Map<String, Object> fields = fieldsOfOutcome();
        assertThat(fields).containsEntry("result", "CREDITED")
                .containsEntry("refundId", REFUND_ID)
                .containsEntry("created", true);
        assertThat(outcome().getMDCPropertyMap()).containsEntry("correlationId", CORRELATION_ID);
        assertThat(fields.keySet()).doesNotContain("cardNumber", "cvv", "securityCode");
    }

    @Test
    void refund_repeatedCreditedRefund_logsTheStoredResultWithoutCallingTheGateway() {
        Refund existing = refund(RefundStatus.CREDITED);
        when(refundRepository.findByTicketId(TICKET_ID)).thenReturn(Optional.of(existing));

        refundService.refund(REQUEST);

        assertThat(fieldsOfOutcome()).containsEntry("result", "CREDITED")
                .containsEntry("refundId", REFUND_ID)
                .containsEntry("created", false);
        verify(paymentGateway, never()).refund(any());
    }

    @Test
    void refund_gatewayFails_logsFailureAndRethrowsTheError() {
        givenNewRefund();
        when(paymentGateway.refund(any())).thenReturn(RefundResult.failed("Account closed"));

        assertThatThrownBy(() -> refundService.refund(REQUEST)).isInstanceOf(TicketYaException.class);

        assertThat(fieldsOfOutcome()).containsEntry("result", "FAILED")
                .containsEntry("refundId", REFUND_ID);
    }

    private void givenNewRefund() {
        when(refundRepository.findByTicketId(TICKET_ID)).thenReturn(Optional.empty());
        when(refundRepository.sumAmountOfThePaymentExcludingStatus(PAYMENT_ID, RefundStatus.FAILED))
                .thenReturn(BigDecimal.ZERO);
        when(refundRepository.save(any())).thenAnswer(invocation -> {
            Refund refund = invocation.getArgument(0);
            refund.setId(REFUND_ID);
            return refund;
        });
        when(refundRepository.findForUpdateById(REFUND_ID))
                .thenAnswer(invocation -> Optional.of(refund(RefundStatus.REQUESTED)));
    }

    private Refund refund(RefundStatus status) {
        return Refund.builder()
                .id(REFUND_ID)
                .ticketId(TICKET_ID)
                .payment(payment)
                .amount(AMOUNT)
                .reason(RefundReason.EVENT_CANCELLED)
                .status(status)
                .build();
    }

    private Map<String, Object> fieldsOfOutcome() {
        return outcome().getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value, (first, second) -> second));
    }

    private ILoggingEvent outcome() {
        return appender.list.stream()
                .filter(event -> event.getKeyValuePairs().stream()
                        .anyMatch(pair -> pair.key.equals("operation") && pair.value.equals("refund")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No structured refund log was emitted"));
    }
}

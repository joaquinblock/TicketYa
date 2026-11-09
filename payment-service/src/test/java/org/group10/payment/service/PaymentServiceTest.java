package org.group10.payment.service;

import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.group10.payment.dto.PaymentRequest;
import org.group10.payment.dto.PaymentResponse;
import org.group10.payment.entity.Payment;
import org.group10.payment.entity.PaymentMethod;
import org.group10.payment.entity.PaymentStatus;
import org.group10.payment.exception.PaymentErrorCode;
import org.group10.payment.gateway.ChargeResult;
import org.group10.payment.gateway.PaymentGateway;
import org.group10.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Long ORDER_ID = 10L;
    private static final BigDecimal AMOUNT = new BigDecimal("25000.00");
    private static final ChargeResult APPROVED_CHARGE = ChargeResult.approved("MOCK-1");

    private final PaymentRequest request = new PaymentRequest(ORDER_ID, AMOUNT, PaymentMethod.CREDIT_CARD, "attempt-1");

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void charge_approvedByTheGateway_returnsTheApprovedPayment() {
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentGateway.charge(any())).thenReturn(APPROVED_CHARGE);

        PaymentResponse response = paymentService.charge(request);

        assertThat(response.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(response.orderId()).isEqualTo(ORDER_ID);
        assertThat(response.amount()).isEqualTo(AMOUNT);
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        assertThat(response.gatewayId()).isEqualTo("MOCK-1");
    }

    @Test
    void charge_storesThePaymentAsPendingBeforeCallingTheGateway() {
        List<PaymentStatus> savedStatuses = new ArrayList<>();
        when(paymentRepository.save(any())).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            savedStatuses.add(payment.getStatus());
            return payment;
        });
        when(paymentGateway.charge(any())).thenAnswer(invocation -> {
            assertThat(savedStatuses).containsExactly(PaymentStatus.PENDING);
            return APPROVED_CHARGE;
        });

        paymentService.charge(request);

        assertThat(savedStatuses).containsExactly(PaymentStatus.PENDING, PaymentStatus.APPROVED);
    }

    @Test
    void charge_rejectedByTheGateway_failsWithTheReasonAndTheRejectedAttempts() {
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentGateway.charge(any())).thenReturn(ChargeResult.rejected("MOCK-1", "Fondos insuficientes"));
        when(paymentRepository.countByOrderIdAndStatus(ORDER_ID, PaymentStatus.REJECTED)).thenReturn(2L);

        assertThatThrownBy(() -> paymentService.charge(request))
                .isInstanceOfSatisfying(TicketYaException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_REJECTED);
                    assertThat(ex.getDetail()).containsEntry("reason", "Fondos insuficientes")
                            .containsEntry("rejectedAttempts", 2L);
                });
    }

    @Test
    void charge_rejectedByTheGateway_storesThePaymentAsRejectedWithItsReason() {
        List<Payment> savedPayments = new ArrayList<>();
        when(paymentRepository.save(any())).thenAnswer(invocation -> {
            savedPayments.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        when(paymentGateway.charge(any())).thenReturn(ChargeResult.rejected("MOCK-1", "Fondos insuficientes"));

        assertThatThrownBy(() -> paymentService.charge(request)).isInstanceOf(TicketYaException.class);

        Payment lastSavedPayment = savedPayments.getLast();
        assertThat(lastSavedPayment.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(lastSavedPayment.getFailureReason()).isEqualTo("Fondos insuficientes");
        assertThat(lastSavedPayment.getGatewayId()).isEqualTo("MOCK-1");
    }

    @Test
    void charge_orderWithAPendingOrApprovedPayment_isRejectedWithoutCharging() {
        when(paymentRepository.findFirstByOrderIdAndStatusIn(any(), any()))
                .thenReturn(Optional.of(Payment.builder().build()));

        assertThatThrownBy(() -> paymentService.charge(request))
                .isInstanceOfSatisfying(TicketYaException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.DATA_CONFLICT));

        verifyNoInteractions(paymentGateway);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void charge_onlyPendingAndApprovedPaymentsBlockTheOrder() {
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentGateway.charge(any())).thenReturn(APPROVED_CHARGE);

        paymentService.charge(request);

        verify(paymentRepository).findFirstByOrderIdAndStatusIn(
                ORDER_ID, List.of(PaymentStatus.PENDING, PaymentStatus.APPROVED));
    }
}

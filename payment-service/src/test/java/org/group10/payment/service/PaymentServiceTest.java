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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Long ORDER_ID = 10L;
    private static final Long STORED_PAYMENT_ID = 7L;
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
        when(paymentRepository.countByOrderIdAndStatusAndIdLessThanEqual(eq(ORDER_ID), eq(PaymentStatus.REJECTED), any()))
                .thenReturn(2L);

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
    void charge_rejectedByTheGateway_countsTheAttemptsAfterStoringTheCurrentRejection() {
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentGateway.charge(any())).thenReturn(ChargeResult.rejected("MOCK-1", "Fondos insuficientes"));

        assertThatThrownBy(() -> paymentService.charge(request)).isInstanceOf(TicketYaException.class);

        InOrder inOrder = inOrder(paymentRepository);
        inOrder.verify(paymentRepository, times(2)).save(any());
        inOrder.verify(paymentRepository)
                .countByOrderIdAndStatusAndIdLessThanEqual(eq(ORDER_ID), eq(PaymentStatus.REJECTED), any());
    }

    @Test
    void charge_approvedByTheGateway_doesNotCountRejectedAttempts() {
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentGateway.charge(any())).thenReturn(APPROVED_CHARGE);

        paymentService.charge(request);

        verify(paymentRepository, never()).countByOrderIdAndStatusAndIdLessThanEqual(any(), any(), any());
    }

    @Test
    void charge_insertFailsAndNoPaymentHasTheKey_propagatesTheFailureWithoutCharging() {
        when(paymentRepository.save(any())).thenThrow(new DataIntegrityViolationException("other constraint"));

        assertThatThrownBy(() -> paymentService.charge(request)).isInstanceOf(DataIntegrityViolationException.class);

        verifyNoInteractions(paymentGateway);
    }

    @Test
    void charge_sameKeyAlreadyApproved_returnsTheStoredPaymentWithoutCharging() {
        when(paymentRepository.findByIdempotencyKey("attempt-1"))
                .thenReturn(Optional.of(storedPayment(PaymentStatus.APPROVED)));

        PaymentResponse response = paymentService.charge(request);

        assertThat(response.id()).isEqualTo(STORED_PAYMENT_ID);
        assertThat(response.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(response.gatewayId()).isEqualTo("MOCK-1");
        assertNothingWasChargedOrStored();
    }

    @Test
    void charge_sameKeyAlreadyApproved_doesNotConsiderItAConflictOfTheOrder() {
        when(paymentRepository.findByIdempotencyKey("attempt-1"))
                .thenReturn(Optional.of(storedPayment(PaymentStatus.APPROVED)));

        paymentService.charge(request);

        verify(paymentRepository, never()).findFirstByOrderIdAndStatusIn(any(), any());
    }

    @Test
    void charge_sameKeyWithTheSameAmountInAnotherScale_returnsTheStoredPayment() {
        when(paymentRepository.findByIdempotencyKey("attempt-1"))
                .thenReturn(Optional.of(storedPayment(PaymentStatus.APPROVED)));

        PaymentResponse response = paymentService.charge(
                new PaymentRequest(ORDER_ID, new BigDecimal("25000"), PaymentMethod.CREDIT_CARD, "attempt-1"));

        assertThat(response.id()).isEqualTo(STORED_PAYMENT_ID);
        assertNothingWasChargedOrStored();
    }

    @Test
    void charge_sameKeyAlreadyRejected_repeatsTheRejectionWithoutAddingAnother() {
        when(paymentRepository.findByIdempotencyKey("attempt-1"))
                .thenReturn(Optional.of(storedPayment(PaymentStatus.REJECTED)));
        when(paymentRepository.countByOrderIdAndStatusAndIdLessThanEqual(ORDER_ID, PaymentStatus.REJECTED, STORED_PAYMENT_ID))
                .thenReturn(1L);

        assertThatThrownBy(() -> paymentService.charge(request))
                .isInstanceOfSatisfying(TicketYaException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_REJECTED);
                    assertThat(ex.getDetail()).containsEntry("reason", "Fondos insuficientes")
                            .containsEntry("rejectedAttempts", 1L);
                });

        assertNothingWasChargedOrStored();
    }

    @Test
    void charge_sameKeyStillPending_failsAsUnavailableWithoutCharging() {
        when(paymentRepository.findByIdempotencyKey("attempt-1"))
                .thenReturn(Optional.of(storedPayment(PaymentStatus.PENDING)));

        assertThatThrownBy(() -> paymentService.charge(request))
                .isInstanceOfSatisfying(TicketYaException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE));

        assertNothingWasChargedOrStored();
    }

    @ParameterizedTest
    @MethodSource("requestsThatChangeTheDataOfTheAttempt")
    void charge_sameKeyWithOtherData_failsAsConflictWithoutCharging(PaymentRequest changedRequest) {
        when(paymentRepository.findByIdempotencyKey("attempt-1"))
                .thenReturn(Optional.of(storedPayment(PaymentStatus.APPROVED)));

        assertThatFailsAsDataConflict(changedRequest);

        assertNothingWasChargedOrStored();
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void charge_sameKeyWithOtherDataInAnyStatus_prefersTheConflictOverTheStoredResult(PaymentStatus status) {
        when(paymentRepository.findByIdempotencyKey("attempt-1")).thenReturn(Optional.of(storedPayment(status)));

        assertThatFailsAsDataConflict(new PaymentRequest(ORDER_ID, new BigDecimal("1.00"), PaymentMethod.CREDIT_CARD, "attempt-1"));

        assertNothingWasChargedOrStored();
    }

    @Test
    void charge_keyTakenByASimultaneousApprovedAttempt_returnsThatPaymentWithoutCharging() {
        when(paymentRepository.findByIdempotencyKey("attempt-1"))
                .thenReturn(Optional.empty(), Optional.of(storedPayment(PaymentStatus.APPROVED)));
        when(paymentRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));

        PaymentResponse response = paymentService.charge(request);

        assertThat(response.id()).isEqualTo(STORED_PAYMENT_ID);
        verifyNoInteractions(paymentGateway);
    }

    @Test
    void charge_keyTakenByASimultaneousAttemptStillPending_failsAsUnavailableAndNotAsConflict() {
        when(paymentRepository.findByIdempotencyKey("attempt-1"))
                .thenReturn(Optional.empty(), Optional.of(storedPayment(PaymentStatus.PENDING)));
        when(paymentRepository.findFirstByOrderIdAndStatusIn(any(), any()))
                .thenReturn(Optional.of(storedPayment(PaymentStatus.PENDING)));
        when(paymentRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));

        assertThatThrownBy(() -> paymentService.charge(request))
                .isInstanceOfSatisfying(TicketYaException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE));

        verifyNoInteractions(paymentGateway);
    }

    @Test
    void charge_keyTakenByASimultaneousAttemptWithOtherData_failsAsConflictWithoutCharging() {
        Payment paymentOfAnotherAmount = Payment.builder().id(STORED_PAYMENT_ID).orderId(ORDER_ID).idempotencyKey("attempt-1")
                .paymentMethod(PaymentMethod.CREDIT_CARD).amount(new BigDecimal("1.00")).status(PaymentStatus.APPROVED).build();
        when(paymentRepository.findByIdempotencyKey("attempt-1"))
                .thenReturn(Optional.empty(), Optional.of(paymentOfAnotherAmount));
        when(paymentRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));

        assertThatFailsAsDataConflict(request);

        verifyNoInteractions(paymentGateway);
    }

    @Test
    void charge_storingTheResultFails_doesNotTreatItAsATakenKey() {
        when(paymentRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0))
                .thenThrow(new DataIntegrityViolationException("duplicate gateway id"));
        when(paymentGateway.charge(any())).thenReturn(APPROVED_CHARGE);

        assertThatThrownBy(() -> paymentService.charge(request)).isInstanceOf(DataIntegrityViolationException.class);

        verify(paymentRepository, times(1)).findByIdempotencyKey("attempt-1");
    }

    @Test
    void charge_gatewayFails_leavesThePaymentPendingAndPropagatesTheFailure() {
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentGateway.charge(any())).thenThrow(new IllegalStateException("gateway down"));

        assertThatThrownBy(() -> paymentService.charge(request)).isInstanceOf(IllegalStateException.class);

        verify(paymentRepository, times(1)).save(any());
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

    private static Stream<PaymentRequest> requestsThatChangeTheDataOfTheAttempt() {
        return Stream.of(
                new PaymentRequest(11L, AMOUNT, PaymentMethod.CREDIT_CARD, "attempt-1"),
                new PaymentRequest(ORDER_ID, new BigDecimal("25000.01"), PaymentMethod.CREDIT_CARD, "attempt-1"),
                new PaymentRequest(ORDER_ID, AMOUNT, PaymentMethod.DEBIT_CARD, "attempt-1"));
    }

    private Payment storedPayment(PaymentStatus status) {
        return Payment.builder()
                .id(STORED_PAYMENT_ID)
                .orderId(ORDER_ID)
                .idempotencyKey("attempt-1")
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .amount(AMOUNT)
                .status(status)
                .gatewayId(status == PaymentStatus.PENDING ? null : "MOCK-1")
                .failureReason(status == PaymentStatus.REJECTED ? "Fondos insuficientes" : null)
                .build();
    }

    private void assertThatFailsAsDataConflict(PaymentRequest conflictingRequest) {
        assertThatThrownBy(() -> paymentService.charge(conflictingRequest))
                .isInstanceOfSatisfying(TicketYaException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.DATA_CONFLICT));
    }

    private void assertNothingWasChargedOrStored() {
        verifyNoInteractions(paymentGateway);
        verify(paymentRepository, never()).save(any());
    }
}

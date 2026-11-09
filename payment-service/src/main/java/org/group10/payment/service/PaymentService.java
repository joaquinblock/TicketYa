package org.group10.payment.service;

import lombok.RequiredArgsConstructor;
import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.group10.payment.dto.PaymentRequest;
import org.group10.payment.dto.PaymentResponse;
import org.group10.payment.entity.Payment;
import org.group10.payment.entity.PaymentStatus;
import org.group10.payment.exception.PaymentErrorCode;
import org.group10.payment.gateway.ChargeResult;
import org.group10.payment.gateway.PaymentGateway;
import org.group10.payment.mapper.PaymentMapper;
import org.group10.payment.repository.PaymentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final List<PaymentStatus> PENDING_OR_APPROVED = List.of(PaymentStatus.PENDING, PaymentStatus.APPROVED);

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    public PaymentResponse charge(PaymentRequest request) {
        return paymentRepository.findByIdempotencyKey(request.idempotencyKey())
                .map(storedPayment -> answerWithTheStoredResult(storedPayment, request))
                .orElseGet(() -> chargeNewAttempt(request));
    }

    private PaymentResponse chargeNewAttempt(PaymentRequest request) {
        ensureOrderHasNoPendingOrApprovedPaymentFromAnotherAttempt(request);

        Payment pendingPayment;
        try {
            pendingPayment = commitPendingPaymentBeforeCallingTheGateway(request);
        } catch (DataIntegrityViolationException duplicateKey) {
            return answerWithTheResultOfTheAttemptThatTookTheKey(request, duplicateKey);
        }

        return chargeThroughTheGateway(pendingPayment);
    }

    private PaymentResponse answerWithTheResultOfTheAttemptThatTookTheKey(PaymentRequest request,
                                                                          DataIntegrityViolationException duplicateKey) {
        return paymentRepository.findByIdempotencyKey(request.idempotencyKey())
                .map(storedPayment -> answerWithTheStoredResult(storedPayment, request))
                .orElseThrow(() -> duplicateKey);
    }

    private PaymentResponse answerWithTheStoredResult(Payment storedPayment, PaymentRequest request) {
        ensureRequestRepeatsTheDataOfTheStoredPayment(storedPayment, request);
        ensurePaymentAlreadyHasAResult(storedPayment);
        ensurePaymentWasNotRejected(storedPayment);

        return PaymentMapper.toResponse(storedPayment);
    }

    private PaymentResponse chargeThroughTheGateway(Payment pendingPayment) {
        ChargeResult chargeResult = paymentGateway.charge(pendingPayment);
        Payment chargedPayment = commitChargeResult(pendingPayment, chargeResult);
        ensurePaymentWasNotRejected(chargedPayment);

        return PaymentMapper.toResponse(chargedPayment);
    }

    private void ensureRequestRepeatsTheDataOfTheStoredPayment(Payment storedPayment, PaymentRequest request) {
        boolean repeatsTheData = storedPayment.getOrderId().equals(request.orderId())
                && storedPayment.getAmount().compareTo(request.amount()) == 0
                && storedPayment.getPaymentMethod() == request.paymentMethod();

        if (!repeatsTheData) {
            throw new TicketYaException(CommonErrorCode.DATA_CONFLICT,
                    "La clave de idempotencia ya se usó con otros datos");
        }
    }

    private void ensurePaymentAlreadyHasAResult(Payment payment) {
        if (payment.getStatus() == PaymentStatus.PENDING) {
            throw new TicketYaException(PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE,
                    "El pago todavía no tiene resultado, se puede reintentar con la misma clave");
        }
    }

    private void ensureOrderHasNoPendingOrApprovedPaymentFromAnotherAttempt(PaymentRequest request) {
        paymentRepository.findFirstByOrderIdAndStatusIn(request.orderId(), PENDING_OR_APPROVED)
                .filter(existingPayment -> !request.idempotencyKey().equals(existingPayment.getIdempotencyKey()))
                .ifPresent(existingPayment -> {
                    throw new TicketYaException(CommonErrorCode.DATA_CONFLICT,
                            "La orden %d ya tiene un pago en curso o aprobado".formatted(request.orderId()));
                });
    }

    private Payment commitPendingPaymentBeforeCallingTheGateway(PaymentRequest request) {
        return paymentRepository.save(PaymentMapper.toPendingEntity(request));
    }

    private Payment commitChargeResult(Payment payment, ChargeResult chargeResult) {
        payment.setGatewayId(chargeResult.gatewayId());
        payment.setStatus(chargeResult.status());
        payment.setFailureReason(chargeResult.failureReason());
        return paymentRepository.save(payment);
    }

    private void ensurePaymentWasNotRejected(Payment payment) {
        if (payment.getStatus() == PaymentStatus.REJECTED) {
            throw new TicketYaException(PaymentErrorCode.PAYMENT_REJECTED,
                    "El pago fue rechazado: %s".formatted(payment.getFailureReason()),
                    Map.of("reason", payment.getFailureReason(),
                            "rejectedAttempts", countRejectedPaymentsOfTheOrderUpTo(payment)));
        }
    }

    private long countRejectedPaymentsOfTheOrderUpTo(Payment payment) {
        return paymentRepository.countByOrderIdAndStatusAndIdLessThanEqual(
                payment.getOrderId(), PaymentStatus.REJECTED, payment.getId());
    }
}

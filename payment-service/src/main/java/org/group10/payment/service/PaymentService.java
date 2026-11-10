package org.group10.payment.service;

import lombok.RequiredArgsConstructor;
import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.group10.payment.dto.PaymentRequest;
import org.group10.payment.dto.PaymentResponse;
import org.group10.payment.entity.Payment;
import org.group10.payment.entity.PaymentStatus;
import org.group10.payment.gateway.ChargeResult;
import org.group10.payment.gateway.PaymentGateway;
import org.group10.payment.mapper.PaymentMapper;
import org.group10.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final List<PaymentStatus> PENDING_OR_APPROVED = List.of(PaymentStatus.PENDING, PaymentStatus.APPROVED);

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    public PaymentResponse charge(PaymentRequest request) {
        ensureOrderHasNoPendingOrApprovedPayment(request.orderId());

        Payment pendingPayment = commitPendingPaymentBeforeCallingTheGateway(request);
        ChargeResult chargeResult = paymentGateway.charge(pendingPayment);

        return PaymentMapper.toResponse(commitChargeResult(pendingPayment, chargeResult));
    }

    private void ensureOrderHasNoPendingOrApprovedPayment(Long orderId) {
        paymentRepository.findFirstByOrderIdAndStatusIn(orderId, PENDING_OR_APPROVED)
                .ifPresent(existingPayment -> {
                    throw new TicketYaException(CommonErrorCode.DATA_CONFLICT,
                            "La orden %d ya tiene un pago en curso o aprobado".formatted(orderId));
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
}

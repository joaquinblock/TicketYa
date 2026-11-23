package org.group10.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.group10.payment.dto.RefundOutcome;
import org.group10.payment.dto.RefundRequest;
import org.group10.payment.entity.Payment;
import org.group10.payment.entity.PaymentStatus;
import org.group10.payment.entity.Refund;
import org.group10.payment.entity.RefundStatus;
import org.group10.payment.exception.PaymentErrorCode;
import org.group10.payment.gateway.PaymentGateway;
import org.group10.payment.gateway.RefundResult;
import org.group10.payment.mapper.RefundMapper;
import org.group10.payment.repository.PaymentRepository;
import org.group10.payment.repository.RefundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    private static final String GATEWAY_DID_NOT_ANSWER = "La pasarela no respondió";

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final PaymentGateway paymentGateway;
    private final TransactionOperations transaction;

    private record RegisteredRefund(Refund refund, boolean created) {
    }

    public RefundOutcome refund(RefundRequest request) {
        RegisteredRefund registered;
        try {
            registered = transaction.execute(status -> registerHoldingThePaymentLock(request));
        } catch (RuntimeException failure) {
            logRefundResult(request, null, false, "FAILED");
            throw failure;
        }

        Refund refund;
        try {
            refund = registered.refund().getStatus() == RefundStatus.REQUESTED
                    ? creditThroughTheGateway(registered.refund())
                    : registered.refund();
        } catch (RuntimeException failure) {
            logRefundResult(request, registered.refund(), registered.created(), "FAILED");
            throw failure;
        }

        logRefundResult(request, refund, registered.created(), refund.getStatus().name());
        ensureRefundWasCredited(refund);
        return new RefundOutcome(RefundMapper.toResponse(refund), registered.created());
    }

    private RegisteredRefund registerHoldingThePaymentLock(RefundRequest request) {
        Payment payment = lockApprovedPaymentOfTheOrder(request.orderId());

        return refundRepository.findByTicketId(request.ticketId())
                .filter(storedRefund -> storedRefund.getStatus() != RefundStatus.FAILED)
                .map(activeRefund -> repeatActiveRefund(activeRefund, payment, request))
                .orElseGet(() -> openRefund(payment, request));
    }

    private Payment lockApprovedPaymentOfTheOrder(Long orderId) {
        return paymentRepository.findForUpdateByActiveOrderIdAndStatus(orderId, PaymentStatus.APPROVED)
                .orElseThrow(() -> orderHasNothingToRefund(orderId));
    }

    private TicketYaException orderHasNothingToRefund(Long orderId) {
        return paymentRepository.existsByOrderId(orderId)
                ? new TicketYaException(PaymentErrorCode.PAYMENT_NOT_REFUNDABLE,
                        "La orden %d no tiene un pago aprobado".formatted(orderId))
                : new TicketYaException(PaymentErrorCode.PAYMENT_NOT_FOUND,
                        "La orden %d no tiene pagos".formatted(orderId));
    }

    private RegisteredRefund repeatActiveRefund(Refund activeRefund, Payment payment, RefundRequest request) {
        ensureRequestRepeatsTheDataOfTheActiveRefund(activeRefund, payment, request);

        return new RegisteredRefund(activeRefund, false);
    }

    private void ensureRequestRepeatsTheDataOfTheActiveRefund(Refund activeRefund, Payment payment,
                                                              RefundRequest request) {
        boolean repeatsTheData = activeRefund.getPayment().getId().equals(payment.getId())
                && activeRefund.getAmount().compareTo(request.amount()) == 0
                && activeRefund.getReason() == request.reason()
                && Objects.equals(activeRefund.getReturnRequestId(), request.returnRequestId());

        if (!repeatsTheData) {
            throw new TicketYaException(CommonErrorCode.DATA_CONFLICT,
                    "La entrada %d ya tiene un reintegro con otros datos".formatted(request.ticketId()));
        }
    }

    private RegisteredRefund openRefund(Payment payment, RefundRequest request) {
        ensureRefundFitsInWhatIsLeftOfThePayment(payment, request);

        Refund refund = refundRepository.findByTicketId(request.ticketId())
                .map(failedRefund -> reopenFailedRefund(failedRefund, payment, request))
                .orElseGet(() -> refundRepository.save(RefundMapper.toRequestedEntity(request, payment)));

        return new RegisteredRefund(refund, true);
    }

    private Refund reopenFailedRefund(Refund failedRefund, Payment payment, RefundRequest request) {
        RefundMapper.reopenWithTheDataOfTheNewRequest(failedRefund, request, payment);

        return failedRefund;
    }

    private void ensureRefundFitsInWhatIsLeftOfThePayment(Payment payment, RefundRequest request) {
        BigDecimal alreadyRefunded = refundRepository.sumAmountOfThePaymentExcludingStatus(
                payment.getId(), RefundStatus.FAILED);

        if (alreadyRefunded.add(request.amount()).compareTo(payment.getAmount()) > 0) {
            throw new TicketYaException(PaymentErrorCode.PAYMENT_NOT_REFUNDABLE,
                    "El reintegro supera lo cobrado en el pago %d".formatted(payment.getId()));
        }
    }

    private Refund creditThroughTheGateway(Refund requestedRefund) {
        RefundResult result = askTheGatewayToCredit(requestedRefund);
        return transaction.execute(status -> commitGatewayAnswer(requestedRefund.getId(), result));
    }

    private RefundResult askTheGatewayToCredit(Refund requestedRefund) {
        try {
            return paymentGateway.refund(requestedRefund);
        } catch (RuntimeException gatewayFailure) {
            log.atError()
                    .addKeyValue("operation", "refund")
                    .addKeyValue("orderId", requestedRefund.getPayment().getOrderId())
                    .addKeyValue("ticketId", requestedRefund.getTicketId())
                    .addKeyValue("refundId", requestedRefund.getId())
                    .addKeyValue("result", "FAILED")
                    .addKeyValue("errorType", gatewayFailure.getClass().getSimpleName())
                    .log("Payment gateway refund failed");
            return RefundResult.failed(GATEWAY_DID_NOT_ANSWER);
        }
    }

    private Refund commitGatewayAnswer(Long refundId, RefundResult result) {
        Refund refund = refundRepository.findForUpdateById(refundId).orElseThrow();

        if (refund.getStatus() != RefundStatus.CREDITED) {
            refund.setGatewayId(result.gatewayId());
            refund.setStatus(result.status());
            refund.setFailureReason(result.failureReason());
        }
        return refund;
    }

    private void ensureRefundWasCredited(Refund refund) {
        if (refund.getStatus() == RefundStatus.FAILED) {
            throw new TicketYaException(PaymentErrorCode.REFUND_FAILED,
                    "La pasarela no acreditó el reintegro: %s".formatted(refund.getFailureReason()),
                    Map.of("reason", refund.getFailureReason()));
        }
    }

    private void logRefundResult(RefundRequest request, Refund refund, boolean created, String result) {
        var event = "CREDITED".equals(result) ? log.atInfo() : log.atWarn();
        event.addKeyValue("operation", "refund")
                .addKeyValue("orderId", request.orderId())
                .addKeyValue("ticketId", request.ticketId())
                .addKeyValue("created", created)
                .addKeyValue("result", result);
        if (refund != null) {
            event.addKeyValue("refundId", refund.getId());
        }
        event.log("Payment refund result");
    }
}

package org.group10.payment.mapper;

import org.group10.payment.dto.RefundRequest;
import org.group10.payment.dto.RefundResponse;
import org.group10.payment.entity.Payment;
import org.group10.payment.entity.Refund;
import org.group10.payment.entity.RefundStatus;

public final class RefundMapper {

    private RefundMapper() {
    }

    public static Refund toRequestedEntity(RefundRequest request, Payment payment) {
        return Refund.builder()
                .ticketId(request.ticketId())
                .payment(payment)
                .reason(request.reason())
                .returnRequestId(request.returnRequestId())
                .amount(request.amount())
                .status(RefundStatus.REQUESTED)
                .build();
    }

    public static void reopenWithTheDataOfTheNewRequest(Refund failedRefund, RefundRequest request, Payment payment) {
        failedRefund.setPayment(payment);
        failedRefund.setReason(request.reason());
        failedRefund.setReturnRequestId(request.returnRequestId());
        failedRefund.setAmount(request.amount());
        failedRefund.setStatus(RefundStatus.REQUESTED);
        failedRefund.setGatewayId(null);
        failedRefund.setFailureReason(null);
    }

    public static RefundResponse toResponse(Refund refund) {
        return new RefundResponse(refund.getId(), refund.getTicketId(), refund.getPayment().getId(),
                refund.getAmount(), refund.getReason(), refund.getReturnRequestId(), refund.getStatus(),
                refund.getGatewayId());
    }
}

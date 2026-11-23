package org.group10.ticket.job;

import lombok.RequiredArgsConstructor;
import org.group10.ticket.service.PaymentAttemptResolutionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentAttemptResolutionJob {

    private final PaymentAttemptResolutionService paymentAttemptResolutionService;

    @Scheduled(fixedDelayString = "${ticketya.payment-attempt.resolution-check}")
    public void resolveAttemptsOfClosedOrders() {
        paymentAttemptResolutionService.resolveAttemptsOfClosedOrders();
    }
}

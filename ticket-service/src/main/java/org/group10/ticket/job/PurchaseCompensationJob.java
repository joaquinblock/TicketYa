package org.group10.ticket.job;

import lombok.RequiredArgsConstructor;
import org.group10.ticket.service.PurchaseCompensationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PurchaseCompensationJob {

    private final PurchaseCompensationService purchaseCompensationService;

    @Scheduled(fixedDelayString = "${ticketya.compensation.refund-check}")
    public void refundPendingCompensations() {
        purchaseCompensationService.refundPendingCompensations();
    }
}

package org.group10.ticket.job;

import lombok.RequiredArgsConstructor;
import org.group10.ticket.service.EventCancellationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CancelledEventRefundsJob {

    private final EventCancellationService eventCancellationService;

    @Scheduled(fixedDelayString = "${ticketya.cancellation.refund-check}")
    public void refundTicketsOfCancelledEvents() {
        eventCancellationService.refundTicketsOfCancelledEvents();
    }
}

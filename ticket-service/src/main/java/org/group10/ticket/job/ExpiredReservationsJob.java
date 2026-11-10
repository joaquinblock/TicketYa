package org.group10.ticket.job;

import lombok.RequiredArgsConstructor;
import org.group10.ticket.service.OrderClosingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExpiredReservationsJob {

    private final OrderClosingService orderClosingService;

    @Scheduled(fixedDelayString = "${ticketya.reservation.expiration-check}")
    public void expireOverdueReservations() {
        orderClosingService.expireOverdueReservations();
    }
}

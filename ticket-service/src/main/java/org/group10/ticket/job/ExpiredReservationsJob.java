package org.group10.ticket.job;

import lombok.RequiredArgsConstructor;
import org.group10.ticket.service.OrderClosingService;
import org.group10.ticket.service.SoldOutService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExpiredReservationsJob {

    private final OrderClosingService orderClosingService;
    private final SoldOutService soldOutService;

    @Scheduled(fixedDelayString = "${ticketya.reservation.expiration-check}")
    public void expireOverdueReservations() {
        orderClosingService.expireOverdueReservations();
    }

    @Scheduled(fixedDelayString = "${ticketya.reservation.expiration-check}")
    public void notifyPendingSoldOutEvents() {
        soldOutService.notifyPendingSoldOutEvents();
    }
}

package org.group10.event.job;

import lombok.RequiredArgsConstructor;
import org.group10.event.service.EventService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FinishedEventsJob {

    private final EventService eventService;

    @Scheduled(initialDelayString = "${ticketya.events.finish-check}", fixedDelayString = "${ticketya.events.finish-check}")
    public void finishEventsThatAlreadyHappened() {
        eventService.finishEventsThatAlreadyHappened();
    }
}

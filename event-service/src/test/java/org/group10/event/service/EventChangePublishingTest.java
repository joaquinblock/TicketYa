package org.group10.event.service;

import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.group10.common.security.Caller;
import org.group10.event.client.TicketClient;
import org.group10.event.dto.EventResumeRequest;
import org.group10.event.entity.Event;
import org.group10.event.entity.EventStatus;
import org.group10.event.entity.EventType;
import org.group10.event.entity.Venue;
import org.group10.event.messaging.EventChange;
import org.group10.event.messaging.EventChanged;
import org.group10.event.repository.EventRepository;
import org.group10.event.repository.EventSectorRepository;
import org.group10.event.repository.ParticipantRepository;
import org.group10.event.repository.SeatRepository;
import org.group10.event.repository.SectorRepository;
import org.group10.event.repository.VenueRepository;
import org.group10.event.statemachine.EventStatusMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventChangePublishingTest {

    private static final Long EVENT_ID = 3L;
    private static final Instant NOW = Instant.parse("2026-11-22T18:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("America/Argentina/Buenos_Aires"));
    private static final LocalDateTime DATE = LocalDateTime.now(CLOCK).plusMonths(2);
    private static final Caller ADMIN = new Caller(3L, "ADMIN", Set.of("EVENT_ADMIN"), null);

    @Mock
    private EventRepository eventRepository;
    @Mock
    private VenueRepository venueRepository;
    @Mock
    private ParticipantRepository participantRepository;
    @Mock
    private EventSectorRepository eventSectorRepository;
    @Mock
    private SectorRepository sectorRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private TransactionOperations transactions;
    @Mock
    private TicketClient ticketClient;
    @Mock
    private ApplicationEventPublisher events;

    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventRepository, venueRepository, participantRepository, eventSectorRepository,
                sectorRepository, seatRepository, new EventStatusMachine(), transactions, ticketClient, CLOCK, events);
        when(transactions.execute(any())).thenAnswer(invocation ->
                invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"PUBLISHED", "SOLD_OUT", "SUSPENDED"})
    void cancel_eventOnSaleOrSuspended_publishesCancelled(EventStatus status) {
        givenEvent(status);

        eventService.cancel(EVENT_ID, ADMIN);

        assertThat(publishedChange()).isEqualTo(new EventChanged(EVENT_ID, EventChange.CANCELLED, NOW));
    }

    @Test
    void cancel_alreadyCancelled_doesNotPublish() {
        givenEvent(EventStatus.CANCELLED);

        eventService.cancel(EVENT_ID, ADMIN);

        verifyNoInteractions(events);
    }

    @Test
    void cancel_ticketServiceFails_doesNotPublish() {
        givenEvent(EventStatus.PUBLISHED);
        givenTicketServiceFails();

        assertThatThrownBy(() -> eventService.cancel(EVENT_ID, ADMIN)).isInstanceOf(TicketYaException.class);

        verifyNoInteractions(events);
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"PUBLISHED", "SOLD_OUT"})
    void suspend_eventOnSale_publishesSuspended(EventStatus status) {
        givenEvent(status);

        eventService.suspend(EVENT_ID, ADMIN);

        assertThat(publishedChange()).isEqualTo(new EventChanged(EVENT_ID, EventChange.SUSPENDED, NOW));
    }

    @Test
    void suspend_alreadySuspended_doesNotPublish() {
        givenEvent(EventStatus.SUSPENDED);

        eventService.suspend(EVENT_ID, ADMIN);

        verifyNoInteractions(events);
    }

    @Test
    void suspend_ticketServiceFails_doesNotPublish() {
        givenEvent(EventStatus.PUBLISHED);
        givenTicketServiceFails();

        assertThatThrownBy(() -> eventService.suspend(EVENT_ID, ADMIN)).isInstanceOf(TicketYaException.class);

        verifyNoInteractions(events);
    }

    @Test
    void resume_withANewDate_publishesRescheduled() {
        givenEvent(EventStatus.SUSPENDED);

        eventService.resume(EVENT_ID, ADMIN, new EventResumeRequest(DATE.plusMonths(1)));

        assertThat(publishedChange()).isEqualTo(new EventChanged(EVENT_ID, EventChange.RESCHEDULED, NOW));
    }

    @Test
    void resume_withoutRequest_doesNotPublish() {
        givenEvent(EventStatus.SUSPENDED);

        eventService.resume(EVENT_ID, ADMIN, null);

        verifyNoInteractions(events);
    }

    @Test
    void resume_withoutDate_doesNotPublish() {
        givenEvent(EventStatus.SUSPENDED);

        eventService.resume(EVENT_ID, ADMIN, new EventResumeRequest(null));

        verifyNoInteractions(events);
    }

    @Test
    void resume_withTheSameDate_doesNotPublish() {
        givenEvent(EventStatus.SUSPENDED);

        eventService.resume(EVENT_ID, ADMIN, new EventResumeRequest(DATE));

        verifyNoInteractions(events);
    }

    @Test
    void resume_ticketServiceFails_doesNotPublish() {
        givenEvent(EventStatus.SUSPENDED);
        givenTicketServiceFails();

        assertThatThrownBy(() -> eventService.resume(EVENT_ID, ADMIN, new EventResumeRequest(DATE.plusMonths(1))))
                .isInstanceOf(TicketYaException.class);

        verifyNoInteractions(events);
    }

    private EventChanged publishedChange() {
        ArgumentCaptor<EventChanged> change = ArgumentCaptor.forClass(EventChanged.class);
        verify(events).publishEvent(change.capture());
        return change.getValue();
    }

    private void givenTicketServiceFails() {
        doThrow(new TicketYaException(CommonErrorCode.SERVICE_UNAVAILABLE, "ticket-service caído"))
                .when(ticketClient).synchronizeEventSale(eq(EVENT_ID), any());
    }

    private void givenEvent(EventStatus status) {
        Venue venue = Venue.builder().id(2L).name("Teatro de la Sierra").address("Calle 1").city("Tandil")
                .latitude(-37.0).longitude(-59.0).build();
        Event event = Event.builder().id(EVENT_ID).name("Arquitectura de microservicios").type(EventType.CONFERENCE)
                .organizationId(1L).createdBy(2L).venue(venue).dateTime(DATE)
                .status(status).highDemand(false).participants(new HashSet<>()).build();
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventSectorRepository.findByEventId(EVENT_ID)).thenReturn(List.of());
        when(seatRepository.countBySectorOfVenue(venue.getId())).thenReturn(List.of());
    }
}

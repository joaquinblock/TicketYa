package org.group10.event.service;

import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.group10.common.security.Caller;
import org.group10.event.client.TicketClient;
import org.group10.event.entity.Event;
import org.group10.event.entity.EventSector;
import org.group10.event.entity.EventStatus;
import org.group10.event.entity.EventType;
import org.group10.event.entity.Sector;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
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

/**
 * Publicar un borrador avisa {@code event.published} (US-NOT-03), solo si la publicación se completa.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventPublishedPublishingTest {

    private static final Long EVENT_ID = 3L;
    private static final Long ORGANIZATION_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-11-23T18:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("America/Argentina/Buenos_Aires"));
    private static final LocalDateTime DATE = LocalDateTime.now(CLOCK).plusMonths(2);
    private static final Caller CREATOR = new Caller(2L, "ORGANIZER", Set.of("EVENT_MANAGE"), ORGANIZATION_ID);

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

    private final Venue theater = Venue.builder().id(2L).name("Teatro de la Sierra").address("Calle Pinto 650")
            .city("Tandil").latitude(-37.0).longitude(-59.0).build();
    private final Sector field = Sector.builder().id(4L).venue(theater).name("Campo").numbered(false).capacity(300)
            .build();

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventRepository, venueRepository, participantRepository, eventSectorRepository,
                sectorRepository, seatRepository, new EventStatusMachine(), transactions, ticketClient, CLOCK, events);
        when(transactions.execute(any())).thenAnswer(invocation ->
                invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    }

    @Test
    void publish_readyDraft_publishesPublished() {
        Event event = givenDraft();
        givenSectorOnSale(event);

        eventService.publish(EVENT_ID, CREATOR);

        ArgumentCaptor<EventChanged> changed = ArgumentCaptor.forClass(EventChanged.class);
        verify(events).publishEvent(changed.capture());
        assertThat(changed.getValue()).isEqualTo(new EventChanged(EVENT_ID, EventChange.PUBLISHED, NOW));
    }

    @Test
    void publish_readyDraft_locksTheEventBeforeChangingItsStatus() {
        Event event = givenDraft();
        givenSectorOnSale(event);

        eventService.publish(EVENT_ID, CREATOR);

        verify(eventRepository).lockById(EVENT_ID);
    }

    @Test
    void publish_notPublishable_doesNotPublish() {
        givenDraft();

        assertThatThrownBy(() -> eventService.publish(EVENT_ID, CREATOR)).isInstanceOf(TicketYaException.class);

        verifyNoInteractions(events);
    }

    @Test
    void publish_ticketServiceFails_doesNotPublish() {
        Event event = givenDraft();
        givenSectorOnSale(event);
        doThrow(new TicketYaException(CommonErrorCode.SERVICE_UNAVAILABLE, "ticket-service caído"))
                .when(ticketClient).synchronizeEventSale(eq(EVENT_ID), any());

        assertThatThrownBy(() -> eventService.publish(EVENT_ID, CREATOR)).isInstanceOf(TicketYaException.class);

        verifyNoInteractions(events);
    }

    @Test
    void routingKey_published_isEventPublished() {
        assertThat(EventChange.PUBLISHED.routingKey()).isEqualTo("event.published");
    }

    private Event givenDraft() {
        Event event = Event.builder().id(EVENT_ID).name("Los Serranos en vivo").type(EventType.CONCERT)
                .organizationId(ORGANIZATION_ID).createdBy(CREATOR.userId()).venue(theater).dateTime(DATE)
                .status(EventStatus.DRAFT).highDemand(false).participants(new HashSet<>()).build();
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventSectorRepository.findByEventId(EVENT_ID)).thenReturn(List.of());
        return event;
    }

    private void givenSectorOnSale(Event event) {
        when(eventSectorRepository.findByEventId(EVENT_ID)).thenReturn(List.of(
                EventSector.builder().id(31L).event(event).sector(field).price(new BigDecimal("10000")).build()));
    }
}

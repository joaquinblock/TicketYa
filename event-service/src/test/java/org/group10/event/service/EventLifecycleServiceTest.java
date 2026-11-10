package org.group10.event.service;

import org.group10.common.exception.TicketYaException;
import org.group10.event.client.AuthClient;
import org.group10.event.client.dto.OrganizationResponse;
import org.group10.event.client.dto.OrganizerResponse;
import org.group10.event.dto.EventCreateRequest;
import org.group10.event.dto.EventDetailResponse;
import org.group10.event.entity.Event;
import org.group10.event.entity.EventSector;
import org.group10.event.entity.EventStatus;
import org.group10.event.entity.EventType;
import org.group10.event.entity.Participant;
import org.group10.event.entity.ParticipantType;
import org.group10.event.entity.Sector;
import org.group10.event.entity.Venue;
import org.group10.event.exception.EventErrorCode;
import org.group10.event.repository.EventRepository;
import org.group10.event.repository.EventSectorRepository;
import org.group10.event.repository.ParticipantRepository;
import org.group10.event.repository.SeatRepository;
import org.group10.event.repository.SectorSeatCount;
import org.group10.event.repository.VenueRepository;
import org.group10.event.statemachine.EventStatusMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventLifecycleServiceTest {

    private static final Long EVENT_ID = 3L;
    private static final Long ORGANIZATION_ID = 1L;
    private static final Long ORGANIZER_ID = 2L;
    private static final LocalDateTime DATE = LocalDateTime.now().plusMonths(2);

    @Mock
    private EventRepository eventRepository;
    @Mock
    private VenueRepository venueRepository;
    @Mock
    private ParticipantRepository participantRepository;
    @Mock
    private EventSectorRepository eventSectorRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private TransactionOperations transactions;
    @Mock
    private AuthClient authClient;

    private EventService eventService;

    private final Venue theater = venue(2L, "Teatro de la Sierra");
    private final Sector stalls = Sector.builder().id(3L).venue(theater).name("Platea").numbered(true).build();
    private final Sector field = Sector.builder().id(4L).venue(theater).name("Campo").numbered(false).capacity(300).build();

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventRepository, venueRepository, participantRepository, eventSectorRepository,
                seatRepository, new EventStatusMachine(), transactions, authClient);
        when(transactions.execute(any())).thenAnswer(invocation ->
                invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        when(seatRepository.countBySectorOfVenue(theater.getId())).thenReturn(List.of(new SectorSeatCount(3L, 50)));
        when(authClient.getOrganization(ORGANIZATION_ID))
                .thenReturn(new OrganizationResponse(ORGANIZATION_ID, "Producciones del Sur S.A.", "Del Sur Producciones"));
        when(authClient.getOrganizer(ORGANIZER_ID)).thenReturn(new OrganizerResponse(ORGANIZER_ID, ORGANIZATION_ID));
    }

    // ---- findById ----

    @Test
    void findById_addsUpTheCapacityOfTheSectorsItSellsAndSortsEverything() {
        Event event = givenEvent(EventStatus.PUBLISHED);
        event.getParticipants().addAll(Set.of(participant(1L, "Los Serranos"), participant(5L, "DJ Marea")));
        givenSectors(event, stalls, field);

        EventDetailResponse detail = eventService.findById(EVENT_ID);

        assertThat(detail.capacity()).isEqualTo(350);
        assertThat(detail.sectors()).extracting(s -> s.name() + "=" + s.capacity()).containsExactly("Campo=300", "Platea=50");
        assertThat(detail.participants()).extracting(p -> p.name()).containsExactly("DJ Marea", "Los Serranos");
    }

    @Test
    void findById_missing_throwsNotFound() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertError(() -> eventService.findById(EVENT_ID), EventErrorCode.EVENT_NOT_FOUND);
    }

    // ---- update ----

    @Test
    void update_sameVenue_replacesTheDataAndKeepsTheSectors() {
        Event event = givenEvent(EventStatus.DRAFT);
        givenVenueExists(theater);

        eventService.update(EVENT_ID, ORGANIZER_ID, request(theater.getId(), false, null));

        assertThat(event.getName()).isEqualTo("Nombre nuevo");
        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
        verify(eventSectorRepository, never()).deleteAllByEventId(any());
    }

    @Test
    void update_anotherVenue_removesTheSectorsOfTheOldOne() {
        givenEvent(EventStatus.DRAFT);
        Venue stadium = venue(1L, "Estadio del Sur");
        givenVenueExists(stadium);

        eventService.update(EVENT_ID, ORGANIZER_ID, request(stadium.getId(), false, null));

        verify(eventSectorRepository).deleteAllByEventId(EVENT_ID);
    }

    @Test
    void update_publishedEvent_throwsNotEditableWithoutCallingAuth() {
        givenEvent(EventStatus.PUBLISHED);

        assertError(() -> eventService.update(EVENT_ID, ORGANIZER_ID, request(theater.getId(), false, null)),
                EventErrorCode.EVENT_NOT_EDITABLE);
        verifyNoInteractions(authClient, transactions);
    }

    @Test
    void update_highDemandWithoutSaleStart_throwsInvalidConfiguration() {
        givenEvent(EventStatus.DRAFT);

        assertError(() -> eventService.update(EVENT_ID, ORGANIZER_ID, request(theater.getId(), true, null)),
                EventErrorCode.INVALID_EVENT_CONFIGURATION);
    }

    @Test
    void update_missingVenue_throwsVenueNotFound() {
        givenEvent(EventStatus.DRAFT);
        when(venueRepository.existsById(99L)).thenReturn(false);

        assertError(() -> eventService.update(EVENT_ID, ORGANIZER_ID, request(99L, false, null)),
                EventErrorCode.VENUE_NOT_FOUND);
    }

    @Test
    void update_organizerOfAnotherOrganization_throwsForbidden() {
        givenEvent(EventStatus.DRAFT);
        givenVenueExists(theater);
        when(authClient.getOrganizer(ORGANIZER_ID)).thenReturn(new OrganizerResponse(ORGANIZER_ID, 9L));

        assertError(() -> eventService.update(EVENT_ID, ORGANIZER_ID, request(theater.getId(), false, null)),
                EventErrorCode.ORGANIZER_NOT_IN_ORGANIZATION);
        verifyNoInteractions(transactions);
    }

    // ---- delete ----

    @Test
    void delete_draft_removesItsSectorsAndTheEvent() {
        givenEvent(EventStatus.DRAFT);

        eventService.delete(EVENT_ID, ORGANIZER_ID);

        verify(eventSectorRepository).deleteAllByEventId(EVENT_ID);
        verify(eventRepository).deleteById(EVENT_ID);
    }

    @Test
    void delete_publishedEvent_throwsNotEditable() {
        givenEvent(EventStatus.PUBLISHED);

        assertError(() -> eventService.delete(EVENT_ID, ORGANIZER_ID), EventErrorCode.EVENT_NOT_EDITABLE);
        verify(eventRepository, never()).deleteById(any());
    }

    @Test
    void delete_organizerOfAnotherOrganization_throwsForbidden() {
        givenEvent(EventStatus.DRAFT);
        when(authClient.getOrganizer(ORGANIZER_ID)).thenReturn(new OrganizerResponse(ORGANIZER_ID, 9L));

        assertError(() -> eventService.delete(EVENT_ID, ORGANIZER_ID), EventErrorCode.ORGANIZER_NOT_IN_ORGANIZATION);
        verify(eventRepository, never()).deleteById(any());
    }

    // ---- publish ----

    @Test
    void publish_readyDraft_goesToPublished() {
        Event event = givenEvent(EventStatus.DRAFT);
        givenSectors(event, stalls);

        EventDetailResponse detail = eventService.publish(EVENT_ID, ORGANIZER_ID);

        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(detail.status()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void publish_withoutSectors_throwsNotPublishableWithTheProblem() {
        Event event = givenEvent(EventStatus.DRAFT);
        givenSectors(event);

        assertNotPublishable(event, "No tiene sectores a la venta");
    }

    @Test
    void publish_numberedSectorWithoutSeats_throwsNotPublishable() {
        Event event = givenEvent(EventStatus.DRAFT);
        Sector empty = Sector.builder().id(8L).venue(theater).name("Pullman").numbered(true).build();
        givenSectors(event, stalls, empty);

        assertNotPublishable(event, "El sector numerado 'Pullman' no tiene asientos");
    }

    @Test
    void publish_withSeveralProblems_listsThemAll() {
        Event event = givenEvent(EventStatus.DRAFT);
        event.setDateTime(LocalDateTime.now().minusDays(1));
        event.setHighDemand(true);
        givenSectors(event);

        assertNotPublishable(event, "No tiene sectores a la venta", "La fecha del evento ya pasó",
                "Es de alta demanda y no tiene saleStart");
    }

    @Test
    void publish_notADraft_throwsInvalidTransitionWithoutCallingAuth() {
        givenEvent(EventStatus.SUSPENDED);

        assertError(() -> eventService.publish(EVENT_ID, ORGANIZER_ID), EventErrorCode.INVALID_STATUS_TRANSITION);
        verifyNoInteractions(authClient);
    }

    @Test
    void publish_organizerOfAnotherOrganization_throwsForbiddenAndKeepsTheDraft() {
        Event event = givenEvent(EventStatus.DRAFT);
        when(authClient.getOrganizer(ORGANIZER_ID)).thenReturn(new OrganizerResponse(ORGANIZER_ID, 9L));

        assertError(() -> eventService.publish(EVENT_ID, ORGANIZER_ID), EventErrorCode.ORGANIZER_NOT_IN_ORGANIZATION);
        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
    }

    // ---- cancel ----

    @Test
    void cancel_publishedSoldOutOrSuspended_goesToCancelled() {
        for (EventStatus status : List.of(EventStatus.PUBLISHED, EventStatus.SOLD_OUT, EventStatus.SUSPENDED)) {
            Event event = givenEvent(status);

            eventService.cancel(EVENT_ID);

            assertThat(event.getStatus()).as("desde %s", status).isEqualTo(EventStatus.CANCELLED);
        }
    }

    @Test
    void cancel_draft_throwsInvalidTransitionAndPointsToDelete() {
        givenEvent(EventStatus.DRAFT);

        assertThatThrownBy(() -> eventService.cancel(EVENT_ID))
                .isInstanceOf(TicketYaException.class)
                .hasMessageContaining("se borra");
    }

    @Test
    void cancel_finished_throwsInvalidTransition() {
        givenEvent(EventStatus.FINISHED);

        assertError(() -> eventService.cancel(EVENT_ID), EventErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void cancel_alreadyCancelled_staysCancelled() {
        Event event = givenEvent(EventStatus.CANCELLED);

        eventService.cancel(EVENT_ID);

        assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);
    }

    // ---- helpers ----

    private Event givenEvent(EventStatus status) {
        Event event = Event.builder().id(EVENT_ID).name("Arquitectura de microservicios").type(EventType.CONFERENCE)
                .organizationId(ORGANIZATION_ID).createdBy(ORGANIZER_ID).venue(theater).dateTime(DATE)
                .status(status).highDemand(false).participants(new HashSet<>()).build();
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventSectorRepository.findByEventId(EVENT_ID)).thenReturn(List.of());
        return event;
    }

    private void givenSectors(Event event, Sector... sectors) {
        when(eventSectorRepository.findByEventId(EVENT_ID)).thenReturn(java.util.Arrays.stream(sectors)
                .map(s -> EventSector.builder().event(event).sector(s).price(new BigDecimal("10000")).build())
                .toList());
    }

    private void givenVenueExists(Venue venue) {
        when(venueRepository.existsById(venue.getId())).thenReturn(true);
        when(venueRepository.getReferenceById(venue.getId())).thenReturn(venue);
    }

    private void assertNotPublishable(Event event, String... problems) {
        assertThatThrownBy(() -> eventService.publish(EVENT_ID, ORGANIZER_ID))
                .isInstanceOf(TicketYaException.class)
                .satisfies(e -> {
                    TicketYaException error = (TicketYaException) e;
                    assertThat(error.getErrorCode()).isEqualTo(EventErrorCode.EVENT_NOT_PUBLISHABLE);
                    assertThat(error.getDetail()).isEqualTo(Map.of("problems", List.of(problems)));
                });
        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
    }

    private static EventCreateRequest request(Long venueId, boolean highDemand, LocalDateTime saleStart) {
        return new EventCreateRequest("  Nombre nuevo  ", "Descripción", EventType.CONFERENCE, venueId, DATE,
                highDemand, saleStart, 4);
    }

    private static Venue venue(Long id, String name) {
        return Venue.builder().id(id).name(name).address("Calle 1").city("Tandil").latitude(-37.0).longitude(-59.0).build();
    }

    private static Participant participant(Long id, String name) {
        return Participant.builder().id(id).name(name).type(ParticipantType.ARTIST).build();
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EventErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(TicketYaException.class)
                .extracting(e -> ((TicketYaException) e).getErrorCode())
                .isEqualTo(code);
    }
}

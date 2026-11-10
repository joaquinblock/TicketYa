package org.group10.event.service;

import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.group10.event.client.AuthClient;
import org.group10.event.client.TicketClient;
import org.group10.event.client.dto.EventSaleRequest;
import org.group10.event.client.dto.OrganizationResponse;
import org.group10.event.client.dto.OrganizerResponse;
import org.group10.event.dto.EventCreateRequest;
import org.group10.event.dto.EventDetailResponse;
import org.group10.event.dto.EventResumeRequest;
import org.group10.event.dto.EventSectorsRequest;
import org.group10.event.entity.Event;
import org.group10.event.entity.EventSector;
import org.group10.event.entity.EventStatus;
import org.group10.event.entity.EventType;
import org.group10.event.entity.Participant;
import org.group10.event.entity.ParticipantType;
import org.group10.event.entity.Seat;
import org.group10.event.entity.Sector;
import org.group10.event.entity.Venue;
import org.group10.event.exception.EventErrorCode;
import org.group10.event.repository.EventRepository;
import org.group10.event.repository.EventSectorRepository;
import org.group10.event.repository.ParticipantRepository;
import org.group10.event.repository.SeatRepository;
import org.group10.event.repository.SectorRepository;
import org.group10.event.repository.SectorSeatCount;
import org.group10.event.repository.VenueRepository;
import org.group10.event.security.Caller;
import org.group10.event.statemachine.EventStatusMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private static final Caller ADMIN = new Caller(3L, Set.of("EVENT_ADMIN"));
    private static final Caller CREATOR = new Caller(ORGANIZER_ID, Set.of("EVENT_MANAGE"));
    private static final Caller OTHER_ORGANIZER = new Caller(7L, Set.of("EVENT_MANAGE"));

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
    private AuthClient authClient;
    @Mock
    private TicketClient ticketClient;

    private EventService eventService;

    private final Venue theater = venue(2L, "Teatro de la Sierra");
    private final Sector stalls = Sector.builder().id(3L).venue(theater).name("Platea").numbered(true).build();
    private final Sector field = Sector.builder().id(4L).venue(theater).name("Campo").numbered(false).capacity(300).build();

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventRepository, venueRepository, participantRepository, eventSectorRepository,
                sectorRepository, seatRepository, new EventStatusMachine(), transactions, authClient, ticketClient);
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
    void publish_readyDraft_sendsTheInventoryOfEverySectorToTheTicketService() {
        Event event = givenEvent(EventStatus.DRAFT);
        givenSectorsWithIds(event);
        when(seatRepository.findBySectorIdIn(List.of(stalls.getId())))
                .thenReturn(List.of(seat(301L, stalls), seat(302L, stalls)));

        eventService.publish(EVENT_ID, ORGANIZER_ID);

        ArgumentCaptor<EventSaleRequest> sale = ArgumentCaptor.forClass(EventSaleRequest.class);
        verify(ticketClient).synchronizeEventSale(eq(EVENT_ID), sale.capture());
        assertThat(sale.getValue().status()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(sale.getValue().sectors()).containsExactly(
                new EventSaleRequest.SectorSale(30L, new BigDecimal("10000"), null, List.of(301L, 302L)),
                new EventSaleRequest.SectorSale(31L, new BigDecimal("10000"), 300, null));
    }

    @Test
    void publish_ticketServiceFails_keepsTheDraft() {
        Event event = givenEvent(EventStatus.DRAFT);
        givenSectorsWithIds(event);
        doThrow(new TicketYaException(CommonErrorCode.SERVICE_UNAVAILABLE, "ticket-service caído"))
                .when(ticketClient).synchronizeEventSale(eq(EVENT_ID), any());

        assertThatThrownBy(() -> eventService.publish(EVENT_ID, ORGANIZER_ID)).isInstanceOf(TicketYaException.class);

        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
    }

    @Test
    void publish_notPublishable_doesNotCallTheTicketService() {
        Event event = givenEvent(EventStatus.DRAFT);
        givenSectors(event);

        assertNotPublishable(event, "No tiene sectores a la venta");
        verifyNoInteractions(ticketClient);
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
    void cancel_byAnAdmin_publishedSoldOutOrSuspended_goesToCancelledWithoutCallingAuth() {
        for (EventStatus status : List.of(EventStatus.PUBLISHED, EventStatus.SOLD_OUT, EventStatus.SUSPENDED)) {
            Event event = givenEvent(status);

            eventService.cancel(EVENT_ID, ADMIN);

            assertThat(event.getStatus()).as("desde %s", status).isEqualTo(EventStatus.CANCELLED);
            assertThat(event.getStatusBeforeSuspension()).isNull();
        }
        verifyNoInteractions(authClient);
    }

    @Test
    void cancel_byTheOrganizerWhoCreatedIt_goesToCancelled() {
        Event event = givenEvent(EventStatus.PUBLISHED);

        eventService.cancel(EVENT_ID, CREATOR);

        assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);
    }

    @Test
    void cancel_byAnotherOrganizerOfTheSameOrganization_throwsNotEventCreatorWithoutCallingAuth() {
        Event event = givenEvent(EventStatus.PUBLISHED);

        assertError(() -> eventService.cancel(EVENT_ID, OTHER_ORGANIZER), EventErrorCode.NOT_EVENT_CREATOR);
        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        verifyNoInteractions(authClient, transactions);
    }

    @Test
    void cancel_byTheCreatorWhoNoLongerOrganizesForTheOrganization_throwsForbidden() {
        givenEvent(EventStatus.PUBLISHED);
        when(authClient.getOrganizer(ORGANIZER_ID)).thenReturn(new OrganizerResponse(ORGANIZER_ID, 9L));

        assertError(() -> eventService.cancel(EVENT_ID, CREATOR), EventErrorCode.ORGANIZER_NOT_IN_ORGANIZATION);
    }

    @Test
    void cancel_published_tellsTheTicketServiceThatTheSaleIsCancelled() {
        Event event = givenEvent(EventStatus.PUBLISHED);
        givenSectorsWithIds(event);

        eventService.cancel(EVENT_ID, ADMIN);

        ArgumentCaptor<EventSaleRequest> sale = ArgumentCaptor.forClass(EventSaleRequest.class);
        verify(ticketClient).synchronizeEventSale(eq(EVENT_ID), sale.capture());
        assertThat(sale.getValue().status()).isEqualTo(EventStatus.CANCELLED);
    }

    @Test
    void cancel_ticketServiceFails_keepsTheStatus() {
        Event event = givenEvent(EventStatus.PUBLISHED);
        doThrow(new TicketYaException(CommonErrorCode.SERVICE_UNAVAILABLE, "ticket-service caído"))
                .when(ticketClient).synchronizeEventSale(eq(EVENT_ID), any());

        assertThatThrownBy(() -> eventService.cancel(EVENT_ID, ADMIN)).isInstanceOf(TicketYaException.class);

        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void cancel_draft_throwsInvalidTransitionAndPointsToDelete() {
        givenEvent(EventStatus.DRAFT);

        assertThatThrownBy(() -> eventService.cancel(EVENT_ID, ADMIN))
                .isInstanceOf(TicketYaException.class)
                .hasMessageContaining("se borra");
        verifyNoInteractions(ticketClient);
    }

    @Test
    void cancel_finished_throwsInvalidTransitionWithoutCallingTheTicketService() {
        givenEvent(EventStatus.FINISHED);

        assertError(() -> eventService.cancel(EVENT_ID, ADMIN), EventErrorCode.INVALID_STATUS_TRANSITION);
        verifyNoInteractions(ticketClient);
    }

    @Test
    void cancel_alreadyCancelled_staysCancelled() {
        Event event = givenEvent(EventStatus.CANCELLED);

        eventService.cancel(EVENT_ID, ADMIN);

        assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);
    }

    @Test
    void markSoldOut_published_goesToSoldOut() {
        Event event = givenEvent(EventStatus.PUBLISHED);

        eventService.markSoldOut(EVENT_ID);

        assertThat(event.getStatus()).isEqualTo(EventStatus.SOLD_OUT);
    }

    @Test
    void markSoldOut_alreadySoldOut_changesNothing() {
        Event event = givenEvent(EventStatus.SOLD_OUT);

        eventService.markSoldOut(EVENT_ID);

        assertThat(event.getStatus()).isEqualTo(EventStatus.SOLD_OUT);
    }

    @Test
    void markSoldOut_cancelled_throwsInvalidTransition() {
        givenEvent(EventStatus.CANCELLED);

        assertError(() -> eventService.markSoldOut(EVENT_ID), EventErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void markBackOnSale_soldOut_goesToPublished() {
        Event event = givenEvent(EventStatus.SOLD_OUT);

        eventService.markBackOnSale(EVENT_ID);

        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void markBackOnSale_alreadyPublished_changesNothing() {
        Event event = givenEvent(EventStatus.PUBLISHED);

        eventService.markBackOnSale(EVENT_ID);

        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void markSoldOut_suspended_staysSuspendedAndResumesAsSoldOut() {
        Event event = givenEvent(EventStatus.PUBLISHED);
        eventService.suspend(EVENT_ID, ADMIN);

        eventService.markSoldOut(EVENT_ID);

        assertThat(event.getStatus()).isEqualTo(EventStatus.SUSPENDED);
        eventService.resume(EVENT_ID, ADMIN, null);
        assertThat(event.getStatus()).isEqualTo(EventStatus.SOLD_OUT);
    }

    @Test
    void markBackOnSale_suspendedWhileSoldOut_staysSuspendedAndResumesAsPublished() {
        Event event = givenEvent(EventStatus.SOLD_OUT);
        eventService.suspend(EVENT_ID, ADMIN);

        eventService.markBackOnSale(EVENT_ID);

        assertThat(event.getStatus()).isEqualTo(EventStatus.SUSPENDED);
        eventService.resume(EVENT_ID, ADMIN, null);
        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void markBackOnSale_cancelled_throwsInvalidTransition() {
        givenEvent(EventStatus.CANCELLED);

        assertError(() -> eventService.markBackOnSale(EVENT_ID), EventErrorCode.INVALID_STATUS_TRANSITION);
    }

    // ---- suspend and resume ----

    @Test
    void suspend_published_remembersItAndResumeGoesBackToPublished() {
        Event event = givenEvent(EventStatus.PUBLISHED);

        eventService.suspend(EVENT_ID, ADMIN);
        assertThat(event.getStatus()).isEqualTo(EventStatus.SUSPENDED);
        assertThat(event.getStatusBeforeSuspension()).isEqualTo(EventStatus.PUBLISHED);

        eventService.resume(EVENT_ID, ADMIN, null);
        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(event.getStatusBeforeSuspension()).isNull();
    }

    @Test
    void suspend_soldOut_resumeGoesBackToSoldOutNotToPublished() {
        Event event = givenEvent(EventStatus.SOLD_OUT);

        eventService.suspend(EVENT_ID, CREATOR);
        eventService.resume(EVENT_ID, CREATOR, null);

        assertThat(event.getStatus()).isEqualTo(EventStatus.SOLD_OUT);
    }

    @Test
    void suspend_alreadySuspended_keepsTheStatusItHadBefore() {
        Event event = givenEvent(EventStatus.SOLD_OUT);
        eventService.suspend(EVENT_ID, ADMIN);

        eventService.suspend(EVENT_ID, ADMIN);

        assertThat(event.getStatusBeforeSuspension()).isEqualTo(EventStatus.SOLD_OUT);
    }

    @Test
    void suspend_published_tellsTheTicketServiceThatTheSaleIsSuspended() {
        Event event = givenEvent(EventStatus.PUBLISHED);
        givenSectorsWithIds(event);

        eventService.suspend(EVENT_ID, ADMIN);

        ArgumentCaptor<EventSaleRequest> sale = ArgumentCaptor.forClass(EventSaleRequest.class);
        verify(ticketClient).synchronizeEventSale(eq(EVENT_ID), sale.capture());
        assertThat(sale.getValue().status()).isEqualTo(EventStatus.SUSPENDED);
    }

    @Test
    void suspend_ticketServiceFails_keepsTheStatus() {
        Event event = givenEvent(EventStatus.PUBLISHED);
        doThrow(new TicketYaException(CommonErrorCode.SERVICE_UNAVAILABLE, "ticket-service caído"))
                .when(ticketClient).synchronizeEventSale(eq(EVENT_ID), any());

        assertThatThrownBy(() -> eventService.suspend(EVENT_ID, ADMIN)).isInstanceOf(TicketYaException.class);

        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(event.getStatusBeforeSuspension()).isNull();
    }

    @Test
    void resume_soldOutWithANewDate_tellsTheTicketServiceTheStatusAndDateItGoesBackTo() {
        Event event = givenEvent(EventStatus.SOLD_OUT);
        givenSectorsWithIds(event);
        eventService.suspend(EVENT_ID, ADMIN);
        LocalDateTime newDate = DATE.plusMonths(1);

        eventService.resume(EVENT_ID, ADMIN, new EventResumeRequest(newDate));

        ArgumentCaptor<EventSaleRequest> sale = ArgumentCaptor.forClass(EventSaleRequest.class);
        verify(ticketClient, times(2)).synchronizeEventSale(eq(EVENT_ID), sale.capture());
        assertThat(sale.getValue().status()).isEqualTo(EventStatus.SOLD_OUT);
        assertThat(sale.getValue().dateTime()).isEqualTo(newDate);
    }

    @Test
    void resume_ticketServiceFails_staysSuspendedWithTheOldDate() {
        Event event = givenEvent(EventStatus.SUSPENDED);
        doThrow(new TicketYaException(CommonErrorCode.SERVICE_UNAVAILABLE, "ticket-service caído"))
                .when(ticketClient).synchronizeEventSale(eq(EVENT_ID), any());

        assertThatThrownBy(() -> eventService.resume(EVENT_ID, ADMIN, new EventResumeRequest(DATE.plusMonths(1))))
                .isInstanceOf(TicketYaException.class);

        assertThat(event.getStatus()).isEqualTo(EventStatus.SUSPENDED);
        assertThat(event.getDateTime()).isEqualTo(DATE);
    }

    @Test
    void resume_withANewDateBeforeTheSaleStart_doesNotCallTheTicketService() {
        Event event = givenEvent(EventStatus.SUSPENDED);
        event.setSaleStart(DATE.minusDays(5));

        assertError(() -> eventService.resume(EVENT_ID, ADMIN, new EventResumeRequest(DATE.minusDays(10))),
                EventErrorCode.INVALID_EVENT_CONFIGURATION);
        verifyNoInteractions(ticketClient);
    }

    @Test
    void suspend_draft_throwsInvalidTransition() {
        givenEvent(EventStatus.DRAFT);

        assertThatThrownBy(() -> eventService.suspend(EVENT_ID, ADMIN))
                .isInstanceOf(TicketYaException.class)
                .hasMessageContaining("borrador");
    }

    @Test
    void suspend_cancelled_throwsInvalidTransition() {
        givenEvent(EventStatus.CANCELLED);

        assertError(() -> eventService.suspend(EVENT_ID, ADMIN), EventErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void suspend_byAnotherOrganizer_throwsNotEventCreator() {
        givenEvent(EventStatus.PUBLISHED);

        assertError(() -> eventService.suspend(EVENT_ID, OTHER_ORGANIZER), EventErrorCode.NOT_EVENT_CREATOR);
    }

    @Test
    void resume_withANewDate_reschedulesIt() {
        Event event = givenEvent(EventStatus.SUSPENDED);
        LocalDateTime newDate = DATE.plusMonths(1);

        eventService.resume(EVENT_ID, ADMIN, new EventResumeRequest(newDate));

        assertThat(event.getDateTime()).isEqualTo(newDate);
        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void resume_withANewDateBeforeTheSaleStart_throwsInvalidConfigurationAndStaysSuspended() {
        Event event = givenEvent(EventStatus.SUSPENDED);
        event.setSaleStart(DATE.minusDays(5));

        assertError(() -> eventService.resume(EVENT_ID, ADMIN, new EventResumeRequest(DATE.minusDays(10))),
                EventErrorCode.INVALID_EVENT_CONFIGURATION);
        assertThat(event.getStatus()).isEqualTo(EventStatus.SUSPENDED);
    }

    @Test
    void resume_suspendedWithoutPreviousStatus_goesToPublished() {
        Event event = givenEvent(EventStatus.SUSPENDED);

        eventService.resume(EVENT_ID, ADMIN, new EventResumeRequest(null));

        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void resume_notSuspended_throwsInvalidTransition() {
        givenEvent(EventStatus.PUBLISHED);

        assertError(() -> eventService.resume(EVENT_ID, ADMIN, null), EventErrorCode.INVALID_STATUS_TRANSITION);
    }

    // ---- replaceSectors ----

    @Test
    void replaceSectors_newSectors_addsThemWithTheirPricesWithTwoDecimals() {
        Event event = givenEvent(EventStatus.DRAFT);
        when(sectorRepository.findAllById(List.of(3L, 4L))).thenReturn(List.of(stalls, field));

        eventService.replaceSectors(EVENT_ID, ORGANIZER_ID, sectorsRequest(3L, "20000", 4L, "9500.5"));

        org.mockito.ArgumentCaptor<List<EventSector>> saved = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(eventSectorRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(es -> es.getSector().getName() + "=" + es.getPrice())
                .containsExactly("Platea=20000.00", "Campo=9500.50");
        assertThat(saved.getValue()).allSatisfy(es -> assertThat(es.getEvent()).isSameAs(event));
        verify(eventSectorRepository).deleteAll(List.of());
    }

    @Test
    void replaceSectors_sectorThatStays_keepsItsRowAndOnlyChangesThePrice() {
        Event event = givenEvent(EventStatus.DRAFT);
        EventSector currentStalls = EventSector.builder().id(30L).event(event).sector(stalls)
                .price(new BigDecimal("10000.00")).build();
        EventSector currentField = EventSector.builder().id(31L).event(event).sector(field)
                .price(new BigDecimal("5000.00")).build();
        when(eventSectorRepository.findByEventId(EVENT_ID)).thenReturn(List.of(currentStalls, currentField));
        when(sectorRepository.findAllById(List.of(3L))).thenReturn(List.of(stalls));

        eventService.replaceSectors(EVENT_ID, ORGANIZER_ID, new EventSectorsRequest(List.of(price(3L, "12000"))));

        assertThat(currentStalls.getId()).isEqualTo(30L);
        assertThat(currentStalls.getPrice()).isEqualByComparingTo("12000.00");
        verify(eventSectorRepository).deleteAll(List.of(currentField));
        verify(eventSectorRepository).saveAll(List.of());
    }

    @Test
    void replaceSectors_withAnEmptyList_removesThemAll() {
        Event event = givenEvent(EventStatus.DRAFT);
        EventSector currentStalls = EventSector.builder().id(30L).event(event).sector(stalls)
                .price(new BigDecimal("10000.00")).build();
        when(eventSectorRepository.findByEventId(EVENT_ID)).thenReturn(List.of(currentStalls));
        when(sectorRepository.findAllById(List.of())).thenReturn(List.of());

        eventService.replaceSectors(EVENT_ID, ORGANIZER_ID, new EventSectorsRequest(List.of()));

        verify(eventSectorRepository).deleteAll(List.of(currentStalls));
        verify(eventSectorRepository).saveAll(List.of());
    }

    @Test
    void replaceSectors_repeatedSector_throwsInvalidConfigurationWithItsIdAndWithoutCallingAuth() {
        givenEvent(EventStatus.DRAFT);

        assertThatThrownBy(() -> eventService.replaceSectors(EVENT_ID, ORGANIZER_ID,
                sectorsRequest(3L, "20000", 3L, "15000")))
                .isInstanceOf(TicketYaException.class)
                .satisfies(e -> {
                    TicketYaException error = (TicketYaException) e;
                    assertThat(error.getErrorCode()).isEqualTo(EventErrorCode.INVALID_EVENT_CONFIGURATION);
                    assertThat(error.getDetail()).isEqualTo(Map.of("repeatedSectorIds", List.of(3L)));
                });
        verifyNoInteractions(authClient, transactions);
    }

    @Test
    void replaceSectors_sectorOfAnotherVenue_throwsNotFoundWithItsIdAndKeepsTheOldOnes() {
        givenEvent(EventStatus.DRAFT);
        Sector stadiumField = Sector.builder().id(1L).venue(venue(1L, "Estadio del Sur")).name("Campo")
                .numbered(false).capacity(5000).build();
        when(sectorRepository.findAllById(List.of(3L, 1L, 99L))).thenReturn(List.of(stalls, stadiumField));

        assertThatThrownBy(() -> eventService.replaceSectors(EVENT_ID, ORGANIZER_ID,
                new EventSectorsRequest(List.of(price(3L, "1"), price(1L, "1"), price(99L, "1")))))
                .isInstanceOf(TicketYaException.class)
                .satisfies(e -> {
                    TicketYaException error = (TicketYaException) e;
                    assertThat(error.getErrorCode()).isEqualTo(EventErrorCode.SECTOR_NOT_FOUND);
                    assertThat(error.getDetail()).isEqualTo(Map.of("missingIds", List.of(1L, 99L)));
                });
        verify(eventSectorRepository, never()).deleteAll(any());
    }

    @Test
    void replaceSectors_publishedEvent_throwsNotEditable() {
        givenEvent(EventStatus.PUBLISHED);

        assertError(() -> eventService.replaceSectors(EVENT_ID, ORGANIZER_ID, sectorsRequest(3L, "1", 4L, "1")),
                EventErrorCode.EVENT_NOT_EDITABLE);
        verifyNoInteractions(authClient, transactions);
    }

    @Test
    void replaceSectors_organizerOfAnotherOrganization_throwsForbidden() {
        givenEvent(EventStatus.DRAFT);
        when(authClient.getOrganizer(ORGANIZER_ID)).thenReturn(new OrganizerResponse(ORGANIZER_ID, 9L));

        assertError(() -> eventService.replaceSectors(EVENT_ID, ORGANIZER_ID, sectorsRequest(3L, "1", 4L, "1")),
                EventErrorCode.ORGANIZER_NOT_IN_ORGANIZATION);
        verifyNoInteractions(transactions);
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

    private void givenSectorsWithIds(Event event) {
        when(eventSectorRepository.findByEventId(EVENT_ID)).thenReturn(List.of(
                EventSector.builder().id(30L).event(event).sector(stalls).price(new BigDecimal("10000")).build(),
                EventSector.builder().id(31L).event(event).sector(field).price(new BigDecimal("10000")).build()));
    }

    private static Seat seat(Long id, Sector sector) {
        return Seat.builder().id(id).sector(sector).build();
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

    private static EventSectorsRequest sectorsRequest(Long first, String firstPrice, Long second, String secondPrice) {
        return new EventSectorsRequest(List.of(price(first, firstPrice), price(second, secondPrice)));
    }

    private static EventSectorsRequest.SectorPrice price(Long sectorId, String price) {
        return new EventSectorsRequest.SectorPrice(sectorId, new BigDecimal(price));
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

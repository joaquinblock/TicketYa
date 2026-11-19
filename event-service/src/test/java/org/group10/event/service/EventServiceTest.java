package org.group10.event.service;

import org.group10.common.exception.TicketYaException;
import org.group10.common.security.Caller;
import org.group10.event.dto.EventCreateRequest;
import org.group10.event.dto.EventResponse;
import org.group10.event.entity.Event;
import org.group10.event.entity.EventStatus;
import org.group10.event.entity.EventType;
import org.group10.event.entity.Venue;
import org.group10.event.exception.EventErrorCode;
import org.group10.event.repository.EventRepository;
import org.group10.event.repository.VenueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    private static final Long ORGANIZATION_ID = 1L;
    private static final Long ORGANIZER_ID = 2L;
    private static final Long VENUE_ID = 10L;
    private static final LocalDateTime DATE = LocalDateTime.now().plusMonths(2);
    private static final Caller ORGANIZER = new Caller(ORGANIZER_ID, "ORGANIZER", Set.of("EVENT_MANAGE"), ORGANIZATION_ID);

    @Mock
    private EventRepository eventRepository;
    @Mock
    private VenueRepository venueRepository;

    @InjectMocks
    private EventService eventService;

    private final Venue venue = Venue.builder().id(VENUE_ID).name("Estadio").address("Av. 1").city("Tandil")
            .latitude(-37.0).longitude(-59.0).build();

    @Test
    void create_validRequest_savesItInDraftForTheOrganizationOfTheToken() {
        when(venueRepository.findById(VENUE_ID)).thenReturn(Optional.of(venue));
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EventResponse response = eventService.create(ORGANIZER, request(false, null));

        ArgumentCaptor<Event> saved = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(EventStatus.DRAFT);
        assertThat(saved.getValue().getOrganizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(saved.getValue().getCreatedBy()).isEqualTo(ORGANIZER_ID);
        assertThat(saved.getValue().getVenue()).isSameAs(venue);
        assertThat(response.status()).isEqualTo(EventStatus.DRAFT);
        assertThat(response.venue().id()).isEqualTo(VENUE_ID);
    }

    @Test
    void create_trimsNameAndDescription() {
        when(venueRepository.findById(VENUE_ID)).thenReturn(Optional.of(venue));
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        EventCreateRequest request = new EventCreateRequest("  Recital  ", "  Gira  ", EventType.CONCERT, VENUE_ID,
                DATE, false, null, null);

        EventResponse response = eventService.create(ORGANIZER, request);

        assertThat(response.name()).isEqualTo("Recital");
        assertThat(response.description()).isEqualTo("Gira");
    }

    @Test
    void create_highDemandWithoutSaleStart_isRejectedBeforeLookingAnythingUp() {
        assertError(() -> eventService.create(ORGANIZER, request(true, null)),
                EventErrorCode.INVALID_EVENT_CONFIGURATION);
        verifyNoInteractions(venueRepository, eventRepository);
    }

    @Test
    void create_highDemandWithSaleStart_isAccepted() {
        when(venueRepository.findById(VENUE_ID)).thenReturn(Optional.of(venue));
        when(eventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EventResponse response = eventService.create(ORGANIZER, request(true, DATE.minusDays(20)));

        assertThat(response.highDemand()).isTrue();
    }

    @Test
    void create_saleStartAfterTheEvent_isRejected() {
        assertError(() -> eventService.create(ORGANIZER, request(false, DATE.plusDays(1))),
                EventErrorCode.INVALID_EVENT_CONFIGURATION);
    }

    @Test
    void create_saleStartAtTheSameTimeAsTheEvent_isRejected() {
        assertError(() -> eventService.create(ORGANIZER, request(false, DATE)),
                EventErrorCode.INVALID_EVENT_CONFIGURATION);
    }

    @Test
    void create_missingVenue_isRejectedWithoutSaving() {
        when(venueRepository.findById(VENUE_ID)).thenReturn(Optional.empty());

        assertError(() -> eventService.create(ORGANIZER, request(false, null)), EventErrorCode.VENUE_NOT_FOUND);
        verifyNoInteractions(eventRepository);
    }

    @Test
    void create_callerWithoutOrganization_isForbiddenWithoutSaving() {
        Caller withoutOrganization = new Caller(ORGANIZER_ID, "ORGANIZER", Set.of("EVENT_MANAGE"), null);

        assertError(() -> eventService.create(withoutOrganization, request(false, null)),
                EventErrorCode.ORGANIZER_NOT_IN_ORGANIZATION);
        verifyNoInteractions(eventRepository);
    }

    private static EventCreateRequest request(boolean highDemand, LocalDateTime saleStart) {
        return new EventCreateRequest("Los Serranos en vivo", "Presentación", EventType.CONCERT, VENUE_ID, DATE,
                highDemand, saleStart, 4);
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EventErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(TicketYaException.class)
                .extracting(e -> ((TicketYaException) e).getErrorCode())
                .isEqualTo(code);
    }
}

package org.group10.event.service;

import org.group10.common.exception.TicketYaException;
import org.group10.event.client.AuthClient;
import org.group10.event.client.dto.OrganizationResponse;
import org.group10.event.client.dto.OrganizerResponse;
import org.group10.event.dto.EventParticipantsRequest;
import org.group10.event.dto.ParticipantResponse;
import org.group10.event.entity.Event;
import org.group10.event.entity.EventStatus;
import org.group10.event.entity.EventType;
import org.group10.event.entity.Participant;
import org.group10.event.entity.ParticipantType;
import org.group10.event.exception.EventErrorCode;
import org.group10.event.repository.EventRepository;
import org.group10.event.repository.ParticipantRepository;
import org.group10.event.repository.VenueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
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
class EventServiceParticipantsTest {

    private static final Long EVENT_ID = 10L;
    private static final Long ORGANIZATION_ID = 1L;
    private static final Long ORGANIZER_ID = 2L;

    @Mock
    private EventRepository eventRepository;
    @Mock
    private VenueRepository venueRepository;
    @Mock
    private ParticipantRepository participantRepository;
    @Mock
    private AuthClient authClient;

    @InjectMocks
    private EventService eventService;

    private final Participant serranos = participant(1L, "Los Serranos");
    private final Participant djMarea = participant(2L, "DJ Marea");

    @Test
    void replaceParticipants_draftOfItsOrganization_replacesThemAndReturnsThemByName() {
        Event event = givenEvent(EventStatus.DRAFT, Set.of(participant(9L, "Anterior")));
        givenOrganizerOf(ORGANIZATION_ID);
        when(participantRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(serranos, djMarea));

        List<ParticipantResponse> response = eventService.replaceParticipants(EVENT_ID, ORGANIZER_ID,
                new EventParticipantsRequest(List.of(1L, 2L)));

        assertThat(event.getParticipants()).containsExactlyInAnyOrder(serranos, djMarea);
        verify(eventRepository).save(event);
        assertThat(response).extracting(ParticipantResponse::name).containsExactly("DJ Marea", "Los Serranos");
    }

    @Test
    void replaceParticipants_withRepeatedIds_countsEachOnce() {
        Event event = givenEvent(EventStatus.DRAFT, Set.of());
        givenOrganizerOf(ORGANIZATION_ID);
        when(participantRepository.findAllById(Set.of(1L))).thenReturn(List.of(serranos));

        eventService.replaceParticipants(EVENT_ID, ORGANIZER_ID, new EventParticipantsRequest(List.of(1L, 1L)));

        assertThat(event.getParticipants()).containsExactly(serranos);
    }

    @Test
    void replaceParticipants_withAnEmptyList_removesThemAll() {
        Event event = givenEvent(EventStatus.DRAFT, Set.of(serranos));
        givenOrganizerOf(ORGANIZATION_ID);
        when(participantRepository.findAllById(Set.of())).thenReturn(List.of());

        List<ParticipantResponse> response = eventService.replaceParticipants(EVENT_ID, ORGANIZER_ID,
                new EventParticipantsRequest(List.of()));

        assertThat(event.getParticipants()).isEmpty();
        assertThat(response).isEmpty();
    }

    @Test
    void replaceParticipants_missingEvent_throwsEventNotFound() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertError(() -> eventService.replaceParticipants(EVENT_ID, ORGANIZER_ID,
                new EventParticipantsRequest(List.of(1L))), EventErrorCode.EVENT_NOT_FOUND);
    }

    @Test
    void replaceParticipants_publishedEvent_throwsNotEditableWithoutCallingAuth() {
        givenEvent(EventStatus.PUBLISHED, Set.of(serranos));

        assertError(() -> eventService.replaceParticipants(EVENT_ID, ORGANIZER_ID,
                new EventParticipantsRequest(List.of(1L))), EventErrorCode.EVENT_NOT_EDITABLE);
        verifyNoInteractions(authClient);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void replaceParticipants_organizerOfAnotherOrganization_throwsForbiddenAndDoesNotSave() {
        givenEvent(EventStatus.DRAFT, Set.of());
        givenOrganizerOf(99L);

        assertError(() -> eventService.replaceParticipants(EVENT_ID, ORGANIZER_ID,
                new EventParticipantsRequest(List.of(1L))), EventErrorCode.ORGANIZER_NOT_IN_ORGANIZATION);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void replaceParticipants_withMissingParticipants_throwsNotFoundWithTheirIds() {
        Event event = givenEvent(EventStatus.DRAFT, Set.of(serranos));
        givenOrganizerOf(ORGANIZATION_ID);
        when(participantRepository.findAllById(Set.of(1L, 7L, 8L))).thenReturn(List.of(serranos));

        assertThatThrownBy(() -> eventService.replaceParticipants(EVENT_ID, ORGANIZER_ID,
                new EventParticipantsRequest(List.of(1L, 7L, 8L))))
                .isInstanceOf(TicketYaException.class)
                .satisfies(e -> {
                    TicketYaException error = (TicketYaException) e;
                    assertThat(error.getErrorCode()).isEqualTo(EventErrorCode.PARTICIPANT_NOT_FOUND);
                    assertThat(error.getDetail()).isEqualTo(Map.of("missingIds", List.of(7L, 8L)));
                });
        assertThat(event.getParticipants()).containsExactly(serranos);
        verify(eventRepository, never()).save(any());
    }

    private Event givenEvent(EventStatus status, Set<Participant> participants) {
        Event event = Event.builder().id(EVENT_ID).name("Muestra").type(EventType.THEATER)
                .organizationId(ORGANIZATION_ID).createdBy(ORGANIZER_ID).dateTime(LocalDateTime.now().plusMonths(1))
                .status(status).participants(new java.util.HashSet<>(participants)).build();
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        return event;
    }

    private void givenOrganizerOf(Long organizationId) {
        when(authClient.getOrganization(ORGANIZATION_ID))
                .thenReturn(new OrganizationResponse(ORGANIZATION_ID, "Producciones del Sur S.A.", "Del Sur Producciones"));
        when(authClient.getOrganizer(ORGANIZER_ID)).thenReturn(new OrganizerResponse(ORGANIZER_ID, organizationId));
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

package org.group10.event.service;

import org.group10.common.exception.TicketYaException;
import org.group10.event.dto.ParticipantRequest;
import org.group10.event.dto.ParticipantResponse;
import org.group10.event.entity.Participant;
import org.group10.event.entity.ParticipantType;
import org.group10.event.exception.EventErrorCode;
import org.group10.event.repository.EventRepository;
import org.group10.event.repository.ParticipantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParticipantServiceTest {

    @Mock
    private ParticipantRepository participantRepository;
    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private ParticipantService participantService;

    private final Participant serranos = Participant.builder().id(1L).name("Los Serranos").type(ParticipantType.ARTIST).build();

    @Test
    void findAll_withBlankName_doesNotFilterByName() {
        when(participantRepository.search(ParticipantType.ARTIST, null)).thenReturn(List.of(serranos));

        assertThat(participantService.findAll(ParticipantType.ARTIST, "  "))
                .extracting(ParticipantResponse::name).containsExactly("Los Serranos");
    }

    @Test
    void findAll_trimsTheName() {
        when(participantRepository.search(null, "serr")).thenReturn(List.of(serranos));

        assertThat(participantService.findAll(null, " serr ")).hasSize(1);
    }

    @Test
    void findById_missing_throwsNotFound() {
        when(participantRepository.findById(7L)).thenReturn(Optional.empty());

        assertError(() -> participantService.findById(7L), EventErrorCode.PARTICIPANT_NOT_FOUND);
    }

    @Test
    void create_newNameAndType_savesItTrimmed() {
        when(participantRepository.existsByNameIgnoreCaseAndType("Trío Costero", ParticipantType.ARTIST)).thenReturn(false);
        when(participantRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ParticipantResponse response = participantService.create(
                new ParticipantRequest("  Trío Costero  ", ParticipantType.ARTIST));

        assertThat(response.name()).isEqualTo("Trío Costero");
        assertThat(response.type()).isEqualTo(ParticipantType.ARTIST);
    }

    @Test
    void create_sameNameAndTypeIgnoringCase_throwsDuplicated() {
        when(participantRepository.existsByNameIgnoreCaseAndType("los serranos", ParticipantType.ARTIST)).thenReturn(true);

        assertError(() -> participantService.create(new ParticipantRequest("los serranos", ParticipantType.ARTIST)),
                EventErrorCode.PARTICIPANT_DUPLICATED);
        verify(participantRepository, never()).save(any());
    }

    @Test
    void create_sameNameWithAnotherType_isAllowed() {
        when(participantRepository.existsByNameIgnoreCaseAndType("Los Serranos", ParticipantType.TEAM)).thenReturn(false);
        when(participantRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(participantService.create(new ParticipantRequest("Los Serranos", ParticipantType.TEAM)).type())
                .isEqualTo(ParticipantType.TEAM);
    }

    @Test
    void update_replacesNameAndType() {
        when(participantRepository.findById(1L)).thenReturn(Optional.of(serranos));
        when(participantRepository.existsByNameIgnoreCaseAndTypeAndIdNot("Los Serranos Trío", ParticipantType.ARTIST, 1L))
                .thenReturn(false);

        participantService.update(1L, new ParticipantRequest("Los Serranos Trío", ParticipantType.ARTIST));

        assertThat(serranos.getName()).isEqualTo("Los Serranos Trío");
    }

    @Test
    void update_toTheNameOfAnotherParticipant_throwsDuplicated() {
        when(participantRepository.findById(1L)).thenReturn(Optional.of(serranos));
        when(participantRepository.existsByNameIgnoreCaseAndTypeAndIdNot("DJ Marea", ParticipantType.ARTIST, 1L))
                .thenReturn(true);

        assertError(() -> participantService.update(1L, new ParticipantRequest("DJ Marea", ParticipantType.ARTIST)),
                EventErrorCode.PARTICIPANT_DUPLICATED);
        assertThat(serranos.getName()).isEqualTo("Los Serranos");
    }

    @Test
    void update_missing_throwsNotFound() {
        when(participantRepository.findById(7L)).thenReturn(Optional.empty());

        assertError(() -> participantService.update(7L, new ParticipantRequest("X", ParticipantType.ARTIST)),
                EventErrorCode.PARTICIPANT_NOT_FOUND);
    }

    @Test
    void delete_participantInNoEvent_deletesIt() {
        when(participantRepository.findById(1L)).thenReturn(Optional.of(serranos));
        when(eventRepository.existsByParticipantsId(1L)).thenReturn(false);

        participantService.delete(1L);

        verify(participantRepository).delete(serranos);
    }

    @Test
    void delete_participantOfAnEvent_throwsInUseAndKeepsIt() {
        when(participantRepository.findById(1L)).thenReturn(Optional.of(serranos));
        when(eventRepository.existsByParticipantsId(1L)).thenReturn(true);

        assertError(() -> participantService.delete(1L), EventErrorCode.PARTICIPANT_IN_USE);
        verify(participantRepository, never()).delete(any());
    }

    @Test
    void delete_missing_throwsNotFound() {
        when(participantRepository.findById(7L)).thenReturn(Optional.empty());

        assertError(() -> participantService.delete(7L), EventErrorCode.PARTICIPANT_NOT_FOUND);
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EventErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(TicketYaException.class)
                .extracting(e -> ((TicketYaException) e).getErrorCode())
                .isEqualTo(code);
    }
}

package org.group10.recommendation.service;

import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.group10.common.security.Caller;
import org.group10.recommendation.client.EventClient;
import org.group10.recommendation.dto.PreferencesRequest;
import org.group10.recommendation.dto.PreferencesResponse;
import org.group10.recommendation.entity.CustomerPreferences;
import org.group10.recommendation.entity.EventType;
import org.group10.recommendation.exception.RecommendationErrorCode;
import org.group10.recommendation.repository.PreferencesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreferenceServiceTest {

    private static final Caller CLIENT = new Caller(7L, "CLIENT", Set.of(), null);
    private static final Caller ORGANIZER = new Caller(8L, "ORGANIZER", Set.of(), 1L);

    @Mock
    private PreferencesRepository preferencesRepository;

    @Mock
    private EventClient eventClient;

    @InjectMocks
    private PreferenceService preferenceService;

    @Test
    void get_withoutSavedPreferences_returnsEmptyPreferences() {
        when(preferencesRepository.findById(7L)).thenReturn(Optional.empty());

        PreferencesResponse response = preferenceService.get(CLIENT);

        assertThat(response).isEqualTo(new PreferencesResponse(List.of(), List.of(), null));
    }

    @Test
    void get_withSavedPreferences_returnsThem() {
        when(preferencesRepository.findById(7L)).thenReturn(Optional.of(CustomerPreferences.builder()
                .userId(7L).eventTypes(List.of(EventType.CONCERT)).participantIds(List.of(12L)).city("Tandil")
                .build()));

        PreferencesResponse response = preferenceService.get(CLIENT);

        assertThat(response).isEqualTo(new PreferencesResponse(List.of("CONCERT"), List.of(12L), "Tandil"));
    }

    @Test
    void replace_validPreferences_savesThemUnderTheCallerAndReturnsThem() {
        when(preferencesRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PreferencesResponse response = preferenceService.replace(CLIENT,
                new PreferencesRequest(List.of("CONCERT", "THEATER"), List.of(12L, 40L), "Tandil"));

        ArgumentCaptor<CustomerPreferences> saved = ArgumentCaptor.forClass(CustomerPreferences.class);
        verify(preferencesRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(7L);
        assertThat(saved.getValue().getEventTypes()).containsExactly(EventType.CONCERT, EventType.THEATER);
        assertThat(saved.getValue().getParticipantIds()).containsExactly(12L, 40L);
        assertThat(saved.getValue().getCity()).isEqualTo("Tandil");
        assertThat(response).isEqualTo(new PreferencesResponse(List.of("CONCERT", "THEATER"), List.of(12L, 40L), "Tandil"));
        verify(eventClient).getParticipant(12L);
        verify(eventClient).getParticipant(40L);
    }

    @Test
    void replace_nullListsAndBlankCity_savesEmptyPreferences() {
        when(preferencesRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PreferencesResponse response = preferenceService.replace(CLIENT, new PreferencesRequest(null, null, "   "));

        assertThat(response).isEqualTo(new PreferencesResponse(List.of(), List.of(), null));
        verifyNoInteractions(eventClient);
    }

    @Test
    void replace_repeatedValues_savesEachOnceAndChecksEachParticipantOnce() {
        when(preferencesRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PreferencesResponse response = preferenceService.replace(CLIENT,
                new PreferencesRequest(List.of("CONCERT", "CONCERT"), List.of(12L, 12L), " Tandil "));

        assertThat(response).isEqualTo(new PreferencesResponse(List.of("CONCERT"), List.of(12L), "Tandil"));
        verify(eventClient).getParticipant(12L);
    }

    @Test
    void replace_unknownEventType_throwsInvalidEventTypeWithoutSaving() {
        assertThatThrownBy(() -> preferenceService.replace(CLIENT,
                new PreferencesRequest(List.of("CONCERT", "OPERA"), List.of(), null)))
                .isInstanceOfSatisfying(TicketYaException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(RecommendationErrorCode.INVALID_EVENT_TYPE);
                    assertThat(e.getErrorCode().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });

        verify(preferencesRepository, never()).save(any());
    }

    @Test
    void replace_unknownParticipant_propagatesTheNotFoundOfEventServiceWithoutSaving() {
        doNothing().when(eventClient).getParticipant(12L);
        doThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null))
                .when(eventClient).getParticipant(99L);

        assertThatThrownBy(() -> preferenceService.replace(CLIENT,
                new PreferencesRequest(List.of(), List.of(12L, 99L), null)))
                .isInstanceOf(HttpClientErrorException.NotFound.class);

        verify(preferencesRepository, never()).save(any());
    }

    @Test
    void get_notClient_throwsForbidden() {
        assertThatThrownBy(() -> preferenceService.get(ORGANIZER))
                .isInstanceOfSatisfying(TicketYaException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));

        verifyNoInteractions(preferencesRepository);
    }

    @Test
    void replace_notClient_throwsForbiddenBeforeCallingEventService() {
        assertThatThrownBy(() -> preferenceService.replace(ORGANIZER,
                new PreferencesRequest(List.of("CONCERT"), List.of(12L), "Tandil")))
                .isInstanceOfSatisfying(TicketYaException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));

        verifyNoInteractions(eventClient, preferencesRepository);
    }
}

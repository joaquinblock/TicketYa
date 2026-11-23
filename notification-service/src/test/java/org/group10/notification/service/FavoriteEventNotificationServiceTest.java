package org.group10.notification.service;

import org.group10.notification.client.EventClient;
import org.group10.notification.client.RecommendationClient;
import org.group10.notification.dto.EventDetail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteEventNotificationServiceTest {

    private static final UUID MESSAGE_ID = UUID.fromString("6b7c8d9e-2f3a-4b4c-8d5e-6f7a8b9c0d1e");
    private static final long EVENT_ID = 3L;
    private static final EventDetail.Venue VENUE = new EventDetail.Venue("Teatro de la Sierra", "Calle Pinto 650",
            "Tandil");
    private static final List<EventDetail.Participant> PARTICIPANTS = List.of(
            new EventDetail.Participant(1L, "Los Serranos"), new EventDetail.Participant(4L, "La Banda del Sur"));

    @Mock
    private EventClient eventClient;

    @Mock
    private RecommendationClient recommendationClient;

    @Mock
    private FanNotifier fanNotifier;

    @InjectMocks
    private FavoriteEventNotificationService notificationService;

    @Test
    void notifyFans_publishedEvent_asksForTheFansOfItsParticipantsInItsCityAndNotifiesEach() {
        EventDetail event = event("PUBLISHED", VENUE, PARTICIPANTS);
        when(eventClient.findEvent(EVENT_ID)).thenReturn(event);
        when(recommendationClient.findFans(List.of(1L, 4L), "Tandil")).thenReturn(List.of(7L, 9L));

        notificationService.notifyFans(MESSAGE_ID, EVENT_ID);

        verify(fanNotifier).notifyFan(MESSAGE_ID, event, 7L);
        verify(fanNotifier).notifyFan(MESSAGE_ID, event, 9L);
    }

    @Test
    void notifyFans_eventNotFound_doesNothing() {
        when(eventClient.findEvent(EVENT_ID)).thenThrow(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, null, null));

        notificationService.notifyFans(MESSAGE_ID, EVENT_ID);

        verifyNoInteractions(recommendationClient, fanNotifier);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELLED", "SUSPENDED", "FINISHED"})
    void notifyFans_eventNoLongerOnSale_doesNotNotify(String status) {
        when(eventClient.findEvent(EVENT_ID)).thenReturn(event(status, VENUE, PARTICIPANTS));

        notificationService.notifyFans(MESSAGE_ID, EVENT_ID);

        verifyNoInteractions(recommendationClient, fanNotifier);
    }

    @Test
    void notifyFans_soldOutEvent_stillNotifies() {
        EventDetail event = event("SOLD_OUT", VENUE, PARTICIPANTS);
        when(eventClient.findEvent(EVENT_ID)).thenReturn(event);
        when(recommendationClient.findFans(List.of(1L, 4L), "Tandil")).thenReturn(List.of(7L));

        notificationService.notifyFans(MESSAGE_ID, EVENT_ID);

        verify(fanNotifier).notifyFan(MESSAGE_ID, event, 7L);
    }

    @Test
    void notifyFans_eventWithoutParticipants_doesNotAskForFans() {
        when(eventClient.findEvent(EVENT_ID)).thenReturn(event("PUBLISHED", VENUE, List.of()));

        notificationService.notifyFans(MESSAGE_ID, EVENT_ID);

        verifyNoInteractions(recommendationClient, fanNotifier);
    }

    @Test
    void notifyFans_eventWithoutCity_doesNotAskForFans() {
        when(eventClient.findEvent(EVENT_ID)).thenReturn(event("PUBLISHED",
                new EventDetail.Venue("Teatro de la Sierra", "Calle Pinto 650", " "), PARTICIPANTS));

        notificationService.notifyFans(MESSAGE_ID, EVENT_ID);

        verifyNoInteractions(recommendationClient, fanNotifier);
    }

    @Test
    void notifyFans_recommendationServiceDown_throwsSoTheMessageIsRetried() {
        when(eventClient.findEvent(EVENT_ID)).thenReturn(event("PUBLISHED", VENUE, PARTICIPANTS));
        when(recommendationClient.findFans(any(), any())).thenThrow(new ResourceAccessException("Read timed out"));

        assertThatThrownBy(() -> notificationService.notifyFans(MESSAGE_ID, EVENT_ID))
                .isInstanceOf(ResourceAccessException.class);
        verifyNoInteractions(fanNotifier);
    }

    @Test
    void notifyFans_firstFanFails_stillNotifiesTheOthersAndThrows() {
        EventDetail event = event("PUBLISHED", VENUE, PARTICIPANTS);
        when(eventClient.findEvent(EVENT_ID)).thenReturn(event);
        when(recommendationClient.findFans(List.of(1L, 4L), "Tandil")).thenReturn(List.of(7L, 9L));
        doThrow(new MailSendException("SMTP caído")).when(fanNotifier).notifyFan(MESSAGE_ID, event, 7L);

        assertThatThrownBy(() -> notificationService.notifyFans(MESSAGE_ID, EVENT_ID))
                .isInstanceOf(IllegalStateException.class);
        verify(fanNotifier).notifyFan(MESSAGE_ID, event, 9L);
    }

    @Test
    void notifyFans_fanAlreadyNotified_isSkippedWithoutFailing() {
        EventDetail event = event("PUBLISHED", VENUE, PARTICIPANTS);
        when(eventClient.findEvent(EVENT_ID)).thenReturn(event);
        when(recommendationClient.findFans(List.of(1L, 4L), "Tandil")).thenReturn(List.of(7L, 9L));
        doThrow(new DataIntegrityViolationException("duplicada")).when(fanNotifier).notifyFan(MESSAGE_ID, event, 7L);

        notificationService.notifyFans(MESSAGE_ID, EVENT_ID);

        verify(fanNotifier).notifyFan(MESSAGE_ID, event, 9L);
    }

    private static EventDetail event(String status, EventDetail.Venue venue, List<EventDetail.Participant> participants) {
        return new EventDetail(EVENT_ID, "Los Serranos en vivo", LocalDateTime.of(2026, 12, 13, 21, 0), venue, status,
                participants);
    }
}

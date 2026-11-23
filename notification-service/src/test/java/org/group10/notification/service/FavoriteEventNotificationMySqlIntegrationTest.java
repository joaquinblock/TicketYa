package org.group10.notification.service;

import org.group10.notification.client.AuthClient;
import org.group10.notification.client.EventClient;
import org.group10.notification.client.RecommendationClient;
import org.group10.notification.dto.EventDetail;
import org.group10.notification.dto.UserContact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Un aviso por fan y por publicación, contra MySQL real: la misma clave de {@code processed_message} que los cambios
 * de un evento impide repetir mails cuando el mensaje se reintenta (US-NOT-03).
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@ActiveProfiles("test")
class FavoriteEventNotificationMySqlIntegrationTest {

    private static final long EVENT_ID = 3L;
    private static final UserContact FIRST = new UserContact(1L, "uno@ticketya.com", "Juan", "Pérez");
    private static final UserContact SECOND = new UserContact(2L, "dos@ticketya.com", "Ana", "Gómez");

    @Autowired
    private FavoriteEventNotificationService notificationService;

    @MockitoBean
    private EventClient eventClient;

    @MockitoBean
    private RecommendationClient recommendationClient;

    @MockitoBean
    private AuthClient authClient;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        when(eventClient.findEvent(EVENT_ID)).thenReturn(new EventDetail(EVENT_ID, "Los Serranos en vivo",
                LocalDateTime.of(2026, 12, 13, 21, 0),
                new EventDetail.Venue("Teatro de la Sierra", "Calle Pinto 650", "Tandil"), "PUBLISHED",
                List.of(new EventDetail.Participant(1L, "Los Serranos"))));
        when(recommendationClient.findFans(List.of(1L), "Tandil")).thenReturn(List.of(FIRST.userId(), SECOND.userId()));
        when(authClient.findContact(FIRST.userId())).thenReturn(FIRST);
        when(authClient.findContact(SECOND.userId())).thenReturn(SECOND);
    }

    @Test
    void notifyFans_sameMessageTwice_sendsOneMailToEachFan() {
        UUID messageId = UUID.randomUUID();

        notificationService.notifyFans(messageId, EVENT_ID);
        notificationService.notifyFans(messageId, EVENT_ID);

        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void notifyFans_mailOfTheSecondFanFails_theRetrySendsOnlyTheMissingOne() {
        UUID messageId = UUID.randomUUID();
        doThrow(new MailSendException("SMTP caído"))
                .doNothing()
                .when(mailSender).send(argThat((SimpleMailMessage mail) -> mail.getTo()[0].equals(SECOND.email())));

        assertThatThrownBy(() -> notificationService.notifyFans(messageId, EVENT_ID))
                .isInstanceOf(RuntimeException.class);
        notificationService.notifyFans(messageId, EVENT_ID);

        verify(mailSender, times(1)).send(argThat((SimpleMailMessage mail) -> mail.getTo()[0].equals(FIRST.email())));
        verify(mailSender, times(2)).send(argThat((SimpleMailMessage mail) -> mail.getTo()[0].equals(SECOND.email())));
    }
}

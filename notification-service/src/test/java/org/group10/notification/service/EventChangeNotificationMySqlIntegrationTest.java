package org.group10.notification.service;

import org.group10.notification.client.AuthClient;
import org.group10.notification.client.EventClient;
import org.group10.notification.client.TicketClient;
import org.group10.notification.dto.EventDetail;
import org.group10.notification.dto.UserContact;
import org.group10.notification.messaging.EventChangedMessage.Change;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Un aviso por comprador y por cambio, contra MySQL real: la clave derivada del {@code messageId} y del comprador es la
 * que impide repetir mails cuando el mensaje se reintenta (US-NOT-02).
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@ActiveProfiles("test")
class EventChangeNotificationMySqlIntegrationTest {

    private static final long EVENT_ID = 3L;
    private static final UserContact FIRST = new UserContact(1L, "uno@ticketya.com", "Juan", "Pérez");
    private static final UserContact SECOND = new UserContact(2L, "dos@ticketya.com", "Ana", "Gómez");
    private static final LocalDateTime DATE = LocalDateTime.of(2026, 12, 13, 21, 0);

    @Autowired
    private EventChangeNotificationService notificationService;

    @MockitoBean
    private EventClient eventClient;

    @MockitoBean
    private TicketClient ticketClient;

    @MockitoBean
    private AuthClient authClient;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        when(eventClient.findEvent(EVENT_ID)).thenReturn(new EventDetail(EVENT_ID, "Los Serranos en vivo", DATE,
                new EventDetail.Venue("Estadio del Sur", "Av. del Estadio 1000", "Buenos Aires")));
        when(ticketClient.findBuyers(EVENT_ID)).thenReturn(List.of(FIRST.userId(), SECOND.userId()));
        when(authClient.findContact(FIRST.userId())).thenReturn(FIRST);
        when(authClient.findContact(SECOND.userId())).thenReturn(SECOND);
    }

    @Test
    void notifyChange_twoBuyers_sendsOneMailToEach() {
        notificationService.notifyChange(UUID.randomUUID(), EVENT_ID, Change.CANCELLED);

        assertThat(sentMails()).extracting(mail -> mail.getTo()[0])
                .containsExactlyInAnyOrder(FIRST.email(), SECOND.email());
    }

    @Test
    void notifyChange_sameMessageTwice_doesNotSendAgain() {
        UUID messageId = UUID.randomUUID();
        notificationService.notifyChange(messageId, EVENT_ID, Change.CANCELLED);

        notificationService.notifyChange(messageId, EVENT_ID, Change.CANCELLED);

        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void notifyChange_mailOfTheSecondBuyerFails_throwsAndTheRetrySendsOnlyTheMissingOne() {
        UUID messageId = UUID.randomUUID();
        doThrow(new MailSendException("SMTP caído"))
                .doNothing()
                .when(mailSender).send(argThat((SimpleMailMessage mail) -> mail.getTo()[0].equals(SECOND.email())));

        assertThatThrownBy(() -> notificationService.notifyChange(messageId, EVENT_ID, Change.SUSPENDED))
                .isInstanceOf(RuntimeException.class);
        verify(mailSender, times(1)).send(argThat((SimpleMailMessage mail) -> mail.getTo()[0].equals(FIRST.email())));

        notificationService.notifyChange(messageId, EVENT_ID, Change.SUSPENDED);

        verify(mailSender, times(1)).send(argThat((SimpleMailMessage mail) -> mail.getTo()[0].equals(FIRST.email())));
        verify(mailSender, times(2)).send(argThat((SimpleMailMessage mail) -> mail.getTo()[0].equals(SECOND.email())));
    }

    @Test
    void notifyChange_mailOfTheFirstBuyerFails_stillSendsTheOthers() {
        doThrow(new MailSendException("SMTP caído"))
                .when(mailSender).send(argThat((SimpleMailMessage mail) -> mail.getTo()[0].equals(FIRST.email())));

        assertThatThrownBy(() -> notificationService.notifyChange(UUID.randomUUID(), EVENT_ID, Change.CANCELLED))
                .isInstanceOf(RuntimeException.class);

        verify(mailSender).send(argThat((SimpleMailMessage mail) -> mail.getTo()[0].equals(SECOND.email())));
    }

    @Test
    void notifyChange_cancelled_saysTheTicketsAreRefunded() {
        givenASingleBuyer();

        notificationService.notifyChange(UUID.randomUUID(), EVENT_ID, Change.CANCELLED);

        SimpleMailMessage mail = sentMails().getFirst();
        assertThat(mail.getSubject()).contains("cancel", "Los Serranos en vivo");
        assertThat(mail.getText()).contains("Juan", "Los Serranos en vivo", "reintegro");
    }

    @Test
    void notifyChange_suspended_saysItIsSuspended() {
        givenASingleBuyer();

        notificationService.notifyChange(UUID.randomUUID(), EVENT_ID, Change.SUSPENDED);

        SimpleMailMessage mail = sentMails().getFirst();
        assertThat(mail.getSubject()).contains("suspend", "Los Serranos en vivo");
        assertThat(mail.getText()).contains("suspendido").doesNotContain("reintegro");
    }

    @Test
    void notifyChange_rescheduled_saysTheNewDate() {
        givenASingleBuyer();

        notificationService.notifyChange(UUID.randomUUID(), EVENT_ID, Change.RESCHEDULED);

        SimpleMailMessage mail = sentMails().getFirst();
        assertThat(mail.getSubject()).contains("fecha", "Los Serranos en vivo");
        assertThat(mail.getText()).contains("13/12/2026 a las 21:00", "Estadio del Sur");
    }

    @Test
    void notifyChange_deletedBuyer_isSkippedWithoutFailing() {
        when(authClient.findContact(FIRST.userId())).thenThrow(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, null, null));

        notificationService.notifyChange(UUID.randomUUID(), EVENT_ID, Change.CANCELLED);

        assertThat(sentMails()).extracting(mail -> mail.getTo()[0]).containsExactly(SECOND.email());
    }

    @Test
    void notifyChange_missingEvent_sendsNothingWithoutFailing() {
        when(eventClient.findEvent(EVENT_ID)).thenThrow(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, null, null));

        notificationService.notifyChange(UUID.randomUUID(), EVENT_ID, Change.CANCELLED);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void notifyChange_eventWithoutBuyers_sendsNothing() {
        when(ticketClient.findBuyers(EVENT_ID)).thenReturn(List.of());

        notificationService.notifyChange(UUID.randomUUID(), EVENT_ID, Change.CANCELLED);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    private void givenASingleBuyer() {
        when(ticketClient.findBuyers(EVENT_ID)).thenReturn(List.of(FIRST.userId()));
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));
    }

    private List<SimpleMailMessage> sentMails() {
        ArgumentCaptor<SimpleMailMessage> mails = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeast(0)).send(mails.capture());
        return mails.getAllValues();
    }
}

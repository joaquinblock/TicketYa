package org.group10.notification.service;

import org.group10.notification.client.AuthClient;
import org.group10.notification.client.EventClient;
import org.group10.notification.dto.EventDetail;
import org.group10.notification.dto.UserContact;
import org.group10.notification.messaging.ReturnResolvedMessage;
import org.group10.notification.repository.ProcessedMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El cliente se entera, por mail y en su bandeja, de si el administrador aprobó o rechazó su devolución. */
@ExtendWith(MockitoExtension.class)
class ReturnResolutionNotifierTest {

    private static final long CLIENT_ID = 1L;
    private static final long EVENT_ID = 2L;

    @Mock
    private ProcessedMessageRepository processedMessages;

    @Mock
    private AuthClient authClient;

    @Mock
    private EventClient eventClient;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private NotificationInbox inbox;

    private ReturnResolutionNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new ReturnResolutionNotifier(processedMessages, authClient, eventClient, mailSender, inbox,
                "no-reply@ticketya.test");
        when(authClient.findContact(CLIENT_ID)).thenReturn(new UserContact(CLIENT_ID, "juan@ticketya.test", "Juan",
                "Pérez"));
    }

    @Test
    void approved_mailsTheClientAndRecordsItInTheInbox() {
        when(eventClient.findEvent(EVENT_ID)).thenReturn(new EventDetail(EVENT_ID, "Noche de tango",
                LocalDateTime.of(2026, 12, 12, 21, 0), null));

        notifier.notifyClient(message("APPROVED"));

        SimpleMailMessage mail = sentMail();
        assertThat(mail.getTo()).containsExactly("juan@ticketya.test");
        assertThat(mail.getSubject()).isEqualTo("Aprobamos tu devolución de Noche de tango");
        assertThat(mail.getText()).contains("Te reintegramos el dinero");
        verify(inbox).record(eq(CLIENT_ID), eq("Aprobamos tu devolución de Noche de tango"), contains("reintegramos"));
    }

    @Test
    void rejected_tellsTheClientTheTicketIsStillValid() {
        when(eventClient.findEvent(EVENT_ID)).thenReturn(new EventDetail(EVENT_ID, "Noche de tango",
                LocalDateTime.of(2026, 12, 12, 21, 0), null));

        notifier.notifyClient(message("REJECTED"));

        assertThat(sentMail().getSubject()).isEqualTo("Rechazamos tu devolución de Noche de tango");
        verify(inbox).record(eq(CLIENT_ID), eq("Rechazamos tu devolución de Noche de tango"), contains("sigue vigente"));
    }

    @Test
    void eventServiceDown_stillNotifiesWithoutTheEventName() {
        when(eventClient.findEvent(EVENT_ID)).thenThrow(new ResourceAccessException("timeout"));

        notifier.notifyClient(message("REJECTED"));

        assertThat(sentMail().getSubject()).isEqualTo("Rechazamos tu devolución de tu evento");
    }

    private SimpleMailMessage sentMail() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private static ReturnResolvedMessage message(String resolution) {
        return new ReturnResolvedMessage(UUID.randomUUID(), Instant.now(), 1L, 4L, CLIENT_ID, EVENT_ID, resolution);
    }
}

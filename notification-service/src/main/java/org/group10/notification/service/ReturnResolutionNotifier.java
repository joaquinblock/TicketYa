package org.group10.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.group10.notification.client.AuthClient;
import org.group10.notification.client.EventClient;
import org.group10.notification.dto.UserContact;
import org.group10.notification.entity.ProcessedMessage;
import org.group10.notification.messaging.ReturnResolvedMessage;
import org.group10.notification.repository.ProcessedMessageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.time.Instant;

/**
 * Le avisa al cliente, por mail y en su bandeja, si el administrador aprobó o rechazó su devolución. Una sola vez por
 * {@code messageId}, con la misma técnica que la compra (ver {@link PurchaseNotificationService}).
 */
@Slf4j
@Service
public class ReturnResolutionNotifier {

    private static final String UNKNOWN_EVENT = "tu evento";

    private final ProcessedMessageRepository processedMessages;
    private final AuthClient authClient;
    private final EventClient eventClient;
    private final JavaMailSender mailSender;
    private final NotificationInbox inbox;
    private final String from;

    public ReturnResolutionNotifier(ProcessedMessageRepository processedMessages, AuthClient authClient,
                                    EventClient eventClient, JavaMailSender mailSender, NotificationInbox inbox,
                                    @Value("${ticketya.mail.from}") String from) {
        this.processedMessages = processedMessages;
        this.authClient = authClient;
        this.eventClient = eventClient;
        this.mailSender = mailSender;
        this.inbox = inbox;
        this.from = from;
    }

    @Transactional
    public void notifyClient(ReturnResolvedMessage message) {
        processedMessages.saveAndFlush(new ProcessedMessage(message.messageId(), Instant.now()));
        UserContact user;
        try {
            user = authClient.findContact(message.userId());
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("No se avisa la solicitud {}: el usuario {} no existe o está dado de baja",
                    message.requestId(), message.userId());
            return;
        }
        String eventName = eventNameOf(message.eventId());
        String title = (message.isApproved() ? "Aprobamos" : "Rechazamos") + " tu devolución de " + eventName;
        String body = message.isApproved()
                ? "Te reintegramos el dinero en el mismo medio de pago y la entrada #%d quedó anulada."
                        .formatted(message.ticketId())
                : "El administrador rechazó la solicitud. Tu entrada #%d sigue vigente para el evento."
                        .formatted(message.ticketId());

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(user.email());
        mail.setSubject(title);
        mail.setText("Hola %s:%n%n%s%n%nTicketYa".formatted(user.firstName(), body));
        mailSender.send(mail);
        inbox.record(message.userId(), title, body);
        log.info("Se avisó la resolución {} de la solicitud {} al usuario {}", message.resolution(),
                message.requestId(), message.userId());
    }

    /** El nombre es un detalle del texto: si el event-service no responde, el aviso sale igual. */
    private String eventNameOf(Long eventId) {
        try {
            return eventClient.findEvent(eventId).name();
        } catch (RestClientException e) {
            log.warn("No se pudo leer el evento {} para el aviso de devolución: {}", eventId,
                    e.getClass().getSimpleName());
            return UNKNOWN_EVENT;
        }
    }
}

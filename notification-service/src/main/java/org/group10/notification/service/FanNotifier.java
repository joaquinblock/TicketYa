package org.group10.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.group10.notification.client.AuthClient;
import org.group10.notification.dto.EventDetail;
import org.group10.notification.dto.UserContact;
import org.group10.notification.entity.ProcessedMessage;
import org.group10.notification.repository.ProcessedMessageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;
import java.util.UUID;

/**
 * Avisa a un fan que se publicó un evento de su artista favorito, una sola vez por mensaje y fan (US-NOT-03). Igual
 * que {@link BuyerNotifier}: bean aparte para que cada fan tenga su transacción, y la clave
 * ({@link BuyerNotifier#keyOf}) se inserta antes de enviar.
 */
@Slf4j
@Service
public class FanNotifier {

    private final ProcessedMessageRepository processedMessages;
    private final AuthClient authClient;
    private final FavoriteEventMailComposer composer;
    private final JavaMailSender mailSender;
    private final String from;
    private final NotificationInbox inbox;

    public FanNotifier(ProcessedMessageRepository processedMessages, AuthClient authClient,
                       FavoriteEventMailComposer composer, JavaMailSender mailSender,
                       NotificationInbox inbox,
                       @Value("${ticketya.mail.from}") String from) {
        this.processedMessages = processedMessages;
        this.authClient = authClient;
        this.composer = composer;
        this.mailSender = mailSender;
        this.from = from;
        this.inbox = inbox;
    }

    @Transactional
    public void notifyFan(UUID messageId, EventDetail event, Long userId) {
        processedMessages.saveAndFlush(new ProcessedMessage(BuyerNotifier.keyOf(messageId, userId), Instant.now()));
        UserContact user;
        try {
            user = authClient.findContact(userId);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("No se avisa la publicación del evento {}: el usuario {} no existe o está dado de baja",
                    event.id(), userId);
            return;
        }
        SimpleMailMessage mail = composer.compose(event, user);
        mail.setFrom(from);
        mailSender.send(mail);
        inbox.record(userId, mail.getSubject(), mail.getText());
        log.info("Se avisó la publicación del evento {} al usuario {}", event.id(), userId);
    }
}

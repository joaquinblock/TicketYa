package org.group10.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.group10.notification.client.AuthClient;
import org.group10.notification.dto.EventDetail;
import org.group10.notification.dto.UserContact;
import org.group10.notification.entity.ProcessedMessage;
import org.group10.notification.messaging.EventChangedMessage.Change;
import org.group10.notification.repository.ProcessedMessageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Avisa un cambio de evento a un comprador, una sola vez por mensaje y comprador (US-NOT-02).
 * <p>
 * Un bean aparte de {@link EventChangeNotificationService} para que cada comprador tenga su propia transacción (el
 * proxy de {@code @Transactional} no se aplica a una llamada dentro de la misma clase). Igual que en la compra, la
 * clave se inserta antes de enviar: si ya estaba, falla con {@code DataIntegrityViolationException}; si el envío falla,
 * el rollback la borra y el reintento lo vuelve a intentar.
 */
@Slf4j
@Service
public class BuyerNotifier {

    private final ProcessedMessageRepository processedMessages;
    private final AuthClient authClient;
    private final EventChangeMailComposer composer;
    private final JavaMailSender mailSender;
    private final String from;

    public BuyerNotifier(ProcessedMessageRepository processedMessages, AuthClient authClient,
                         EventChangeMailComposer composer, JavaMailSender mailSender,
                         @Value("${ticketya.mail.from}") String from) {
        this.processedMessages = processedMessages;
        this.authClient = authClient;
        this.composer = composer;
        this.mailSender = mailSender;
        this.from = from;
    }

    /**
     * La clave del aviso: el mismo mensaje y el mismo comprador dan siempre el mismo UUID.
     */
    static UUID keyOf(UUID messageId, Long userId) {
        return UUID.nameUUIDFromBytes((messageId + ":" + userId).getBytes(StandardCharsets.UTF_8));
    }

    @Transactional
    public void notifyBuyer(UUID messageId, EventDetail event, Change change, Long userId) {
        processedMessages.saveAndFlush(new ProcessedMessage(keyOf(messageId, userId), Instant.now()));
        UserContact user;
        try {
            user = authClient.findContact(userId);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("No se avisa el cambio {} del evento {}: el usuario {} no existe o está dado de baja",
                    change, event.id(), userId);
            return;
        }
        SimpleMailMessage mail = composer.compose(event, change, user);
        mail.setFrom(from);
        mailSender.send(mail);
        log.info("Se avisó el cambio {} del evento {} al usuario {}", change, event.id(), userId);
    }
}

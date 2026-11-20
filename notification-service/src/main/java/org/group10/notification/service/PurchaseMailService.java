package org.group10.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.group10.notification.client.AuthClient;
import org.group10.notification.client.TicketClient;
import org.group10.notification.dto.PaidOrder;
import org.group10.notification.dto.UserContact;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;

/**
 * Envía el mail de una compra confirmada (US-NOT-01): pide la orden al ticket-service y el contacto al auth-service,
 * arma el mail y lo manda.
 * <ul>
 *   <li>Si la orden no existe o no está pagada, o el usuario se dio de baja, no se envía: reintentar no cambiaría
 *       nada. Queda en el log.</li>
 *   <li>Si un servicio no responde o falla el envío, se propaga la excepción, para que quien lo llame pueda
 *       reintentar.</li>
 * </ul>
 * En el log van el id de la orden y el del usuario, nunca el email ni el QR.
 */
@Slf4j
@Service
public class PurchaseMailService {

    private final TicketClient ticketClient;
    private final AuthClient authClient;
    private final PurchaseMailComposer composer;
    private final JavaMailSender mailSender;
    private final String from;

    public PurchaseMailService(TicketClient ticketClient, AuthClient authClient, PurchaseMailComposer composer,
                               JavaMailSender mailSender, @Value("${ticketya.mail.from}") String from) {
        this.ticketClient = ticketClient;
        this.authClient = authClient;
        this.composer = composer;
        this.mailSender = mailSender;
        this.from = from;
    }

    public void send(Long orderId, Long userId) {
        PaidOrder order;
        try {
            order = ticketClient.findPaidOrder(orderId);
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Conflict e) {
            log.warn("No se envía el mail de la orden {}: el ticket-service respondió {}", orderId, e.getStatusCode());
            return;
        }
        UserContact user;
        try {
            user = authClient.findContact(userId);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("No se envía el mail de la orden {}: el usuario {} no existe o está dado de baja", orderId, userId);
            return;
        }

        PurchaseMailComposer.Mail mail = composer.compose(order, user);
        mailSender.send(message -> {
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(user.email());
            helper.setSubject(mail.subject());
            helper.setText(mail.html(), true);
        });
        log.info("Se envió el mail de la compra de la orden {} al usuario {}", orderId, userId);
    }
}

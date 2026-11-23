package org.group10.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.group10.notification.client.EventClient;
import org.group10.notification.client.TicketClient;
import org.group10.notification.dto.EventDetail;
import org.group10.notification.messaging.EventChangedMessage.Change;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.UUID;

/**
 * Avisa a cada comprador que un evento se canceló, se suspendió o se reprogramó, un mail por comprador aunque tenga
 * varias entradas (US-NOT-02).
 * <ul>
 *   <li>Si el evento ya no existe, no hay a quién avisar ni qué decir: queda en el log y el mensaje se da por
 *       procesado.</li>
 *   <li>Cada comprador va en su propia transacción ({@link BuyerNotifier}). Si uno falla, se sigue con los demás y al
 *       final se tira una excepción para que se reintente el mensaje; en el reintento, los ya avisados se saltean.</li>
 *   <li>Si event-service o ticket-service no responden, la excepción sube y se reintenta todo.</li>
 * </ul>
 */
@Slf4j
@Service
public class EventChangeNotificationService {

    private final EventClient eventClient;
    private final TicketClient ticketClient;
    private final BuyerNotifier buyerNotifier;

    public EventChangeNotificationService(EventClient eventClient, TicketClient ticketClient,
                                          BuyerNotifier buyerNotifier) {
        this.eventClient = eventClient;
        this.ticketClient = ticketClient;
        this.buyerNotifier = buyerNotifier;
    }

    public void notifyChange(UUID messageId, Long eventId, Change change) {
        EventDetail event;
        try {
            event = eventClient.findEvent(eventId);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("No se avisa el cambio {} del evento {}: el event-service respondió 404", change, eventId);
            return;
        }
        List<Long> buyers = ticketClient.findBuyers(eventId);

        RuntimeException firstFailure = null;
        int failures = 0;
        for (Long userId : buyers) {
            try {
                buyerNotifier.notifyBuyer(messageId, event, change, userId);
            } catch (DataIntegrityViolationException e) {
                log.info("El usuario {} ya tenía el aviso del cambio {} del evento {}", userId, change, eventId);
            } catch (RuntimeException e) {
                // Solo la clase: el mensaje puede traer la respuesta del SMTP, con el email del comprador
                log.warn("Falló el aviso del cambio {} del evento {} al usuario {}: {}", change, eventId, userId,
                        e.getClass().getSimpleName());
                failures++;
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        if (firstFailure != null) {
            throw new IllegalStateException("Faltan %d de %d avisos del cambio %s del evento %d"
                    .formatted(failures, buyers.size(), change, eventId), firstFailure);
        }
    }
}

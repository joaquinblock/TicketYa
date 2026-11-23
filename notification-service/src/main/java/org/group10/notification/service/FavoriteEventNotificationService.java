package org.group10.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.group10.notification.client.EventClient;
import org.group10.notification.client.RecommendationClient;
import org.group10.notification.dto.EventDetail;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Avisa a los clientes que tienen como favorito a un participante de un evento recién publicado y viven en la ciudad
 * del lugar, un mail por cliente (US-NOT-03). Los fans los decide recommendation-service.
 * <ul>
 *   <li>Si el evento ya no existe o dejó de estar a la venta (se canceló o suspendió antes de procesar el mensaje), no
 *       se avisa: queda en el log y el mensaje se da por procesado. Lo mismo sin participantes o sin ciudad.</li>
 *   <li>Cada fan va en su propia transacción ({@link FanNotifier}). Si uno falla, se sigue con los demás y al final
 *       se tira una excepción para que se reintente el mensaje; en el reintento, los ya avisados se saltean.</li>
 *   <li>Si event-service o recommendation-service no responden, la excepción sube y se reintenta todo.</li>
 * </ul>
 */
@Slf4j
@Service
public class FavoriteEventNotificationService {

    private static final Set<String> ON_SALE = Set.of("PUBLISHED", "SOLD_OUT");

    private final EventClient eventClient;
    private final RecommendationClient recommendationClient;
    private final FanNotifier fanNotifier;

    public FavoriteEventNotificationService(EventClient eventClient, RecommendationClient recommendationClient,
                                            FanNotifier fanNotifier) {
        this.eventClient = eventClient;
        this.recommendationClient = recommendationClient;
        this.fanNotifier = fanNotifier;
    }

    public void notifyFans(UUID messageId, Long eventId) {
        EventDetail event;
        try {
            event = eventClient.findEvent(eventId);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("No se avisa la publicación del evento {}: el event-service respondió 404", eventId);
            return;
        }
        if (!ON_SALE.contains(event.status())) {
            log.info("No se avisa la publicación del evento {}: ya está {}", eventId, event.status());
            return;
        }
        String city = event.venue() == null ? null : event.venue().city();
        if (event.participantIds().isEmpty() || city == null || city.isBlank()) {
            log.info("No se avisa la publicación del evento {}: no tiene participantes o ciudad", eventId);
            return;
        }
        List<Long> fans = recommendationClient.findFans(event.participantIds(), city);

        RuntimeException firstFailure = null;
        int failures = 0;
        for (Long userId : fans) {
            try {
                fanNotifier.notifyFan(messageId, event, userId);
            } catch (DataIntegrityViolationException e) {
                log.info("El usuario {} ya tenía el aviso de la publicación del evento {}", userId, eventId);
            } catch (RuntimeException e) {
                // Solo la clase: el mensaje puede traer la respuesta del SMTP, con el email del fan
                log.warn("Falló el aviso de la publicación del evento {} al usuario {}: {}", eventId, userId,
                        e.getClass().getSimpleName());
                failures++;
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        if (firstFailure != null) {
            throw new IllegalStateException("Faltan %d de %d avisos de la publicación del evento %d"
                    .formatted(failures, fans.size(), eventId), firstFailure);
        }
    }
}

package org.group10.recommendation.seeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.group10.recommendation.entity.PurchaseHistory;
import org.group10.recommendation.repository.PurchaseHistoryRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Historial coherente con el TicketSeeder: el cliente 1 del AuthSeeder tiene órdenes {@code PAID} del concierto
 * (evento 1) y de la obra (evento 2). Esas órdenes se cargan sin publicar {@code purchase.confirmed}, así que el
 * historial se siembra acá.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecommendationSeeder implements ApplicationRunner {

    private static final Long CLIENT_USER_ID = 1L;
    private static final Long CONCERT_EVENT_ID = 1L;
    private static final Long PLAY_EVENT_ID = 2L;

    private final PurchaseHistoryRepository purchaseHistoryRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (purchaseHistoryRepository.count() > 0) {
            log.info("RecommendationSeeder: ya hay historial, se omite la carga");
            return;
        }
        // Con los datos del EventSeeder: el concierto es de Los Serranos (1) en el Estadio del Sur (1) y la obra, de la
        // Compañía Teatro del Centro (2) en el Teatro de la Sierra (2). Los dos son de la organización 1, que queda
        // como favorita inferida.
        purchaseHistoryRepository.save(PurchaseHistory.builder()
                .userId(CLIENT_USER_ID)
                .eventIds(List.of(CONCERT_EVENT_ID, PLAY_EVENT_ID))
                .countedEventIds(List.of(CONCERT_EVENT_ID, PLAY_EVENT_ID))
                .counts(new PurchaseHistory.FavoriteCounts(Map.of("1", 1, "2", 1), Map.of("1", 1, "2", 1),
                        Map.of("1", 2)))
                .build());
        log.info("RecommendationSeeder: historial de compras del cliente {} cargado", CLIENT_USER_ID);
    }
}

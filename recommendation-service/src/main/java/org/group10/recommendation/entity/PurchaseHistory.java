package org.group10.recommendation.entity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

/**
 * Eventos para los que el cliente compró entradas, armado con los {@code purchase.confirmed} de ticket-service.
 * Un documento por cliente. Cada evento está una sola vez, aunque haya comprado varias veces o el mensaje se repita.
 * {@code userId} es el id del auth-service y {@code eventIds} son ids del event-service (RFC-004).
 * <p>
 * Lo escriben dos listeners independientes: el del historial ({@code eventIds}) y el de los favoritos
 * ({@code countedEventIds} y {@code counts}), que necesita a event-service. Cada uno toca solo sus campos.
 */
@Document(collection = "purchase_history")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PurchaseHistory {

    @Id
    private Long userId;

    private List<Long> eventIds;

    /** Eventos ya sumados a {@code counts}: un mensaje repetido no cuenta dos veces. */
    private List<Long> countedEventIds;

    private FavoriteCounts counts;

    /**
     * En cuántos eventos comprados aparece cada participante, lugar y organización, por id. Las claves son texto
     * porque así las guarda MongoDB.
     */
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    public static class FavoriteCounts {

        private static final int MIN_EVENTS_TO_INFER = 2;
        private static final int MAX_INFERRED = 3;

        private Map<String, Integer> participants;

        private Map<String, Integer> venues;

        private Map<String, Integer> organizations;

        public List<Long> inferredParticipants() {
            return inferred(participants);
        }

        public List<Long> inferredVenues() {
            return inferred(venues);
        }

        public List<Long> inferredOrganizations() {
            return inferred(organizations);
        }

        /**
         * Favoritos inferidos de las compras: los que aparecen en al menos {@value #MIN_EVENTS_TO_INFER} eventos, los
         * {@value #MAX_INFERRED} más repetidos (a igual cantidad, el de menor id).
         */
        private static List<Long> inferred(Map<String, Integer> counts) {
            if (counts == null) {
                return List.of();
            }
            return counts.entrySet().stream()
                    .filter(entry -> entry.getValue() != null && entry.getValue() >= MIN_EVENTS_TO_INFER)
                    .map(entry -> Map.entry(Long.valueOf(entry.getKey()), entry.getValue()))
                    .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed()
                            .thenComparing(Map.Entry.comparingByKey()))
                    .limit(MAX_INFERRED)
                    .map(Map.Entry::getKey)
                    .toList();
        }
    }
}

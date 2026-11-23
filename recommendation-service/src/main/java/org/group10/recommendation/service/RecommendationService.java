package org.group10.recommendation.service;

import lombok.RequiredArgsConstructor;
import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.group10.common.security.Caller;
import org.group10.recommendation.client.EventClient;
import org.group10.recommendation.dto.EventDetail;
import org.group10.recommendation.dto.EventSummary;
import org.group10.recommendation.entity.CustomerPreferences;
import org.group10.recommendation.entity.EventType;
import org.group10.recommendation.entity.PurchaseHistory;
import org.group10.recommendation.repository.PreferencesRepository;
import org.group10.recommendation.repository.PurchaseHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Sugerencias de eventos para un cliente (US-REC-02).
 * <ol>
 *   <li>Candidatos: la búsqueda de event-service por sus participantes favoritos, por sus tipos y por su ciudad (una
 *       llamada por cada preferencia que tenga), más los eventos que compraron otros clientes que compraron lo mismo
 *       que él.</li>
 *   <li>Puntaje: participante favorito +3, tipo favorito +2, su ciudad +2 y co-compra +1 por cliente (hasta 3). A
 *       igual puntaje, el más próximo.</li>
 *   <li>Nunca se sugiere un evento que ya compró. Sin candidatos (cliente nuevo o nada coincide), los próximos
 *       eventos.</li>
 * </ol>
 * Con event-service caído, la excepción del client sube y el {@code GlobalExceptionHandler} responde 503.
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {

    /** Eventos de la co-compra que se piden a event-service, uno por uno. */
    public static final int CO_PURCHASE_LIMIT = 5;

    private static final String CLIENT_ROLE = "CLIENT";
    private static final int MAX_SUGGESTIONS = 10;
    /** El máximo que acepta la búsqueda de event-service. */
    private static final int SEARCH_SIZE = 50;
    private static final int FAVORITE_PARTICIPANT_POINTS = 3;
    private static final int EVENT_TYPE_POINTS = 2;
    private static final int CITY_POINTS = 2;
    private static final int MAX_CO_PURCHASE_POINTS = 3;
    /** Lo mismo que muestra la búsqueda: lo que se puede comprar o puede volver a tener lugar. */
    private static final Set<String> BUYABLE = Set.of("PUBLISHED", "SOLD_OUT");

    private final PreferencesRepository preferencesRepository;
    private final PurchaseHistoryRepository purchaseHistoryRepository;
    private final PurchaseHistoryService purchaseHistoryService;
    private final EventClient eventClient;
    private final Clock clock;

    public List<EventSummary> suggest(Caller caller) {
        requireClient(caller);
        Optional<CustomerPreferences> preferences = preferencesRepository.findById(caller.userId());
        List<Long> favorites = preferences.map(CustomerPreferences::getParticipantIds).map(List::copyOf).orElse(List.of());
        List<EventType> types = preferences.map(CustomerPreferences::getEventTypes).map(List::copyOf).orElse(List.of());
        String city = preferences.map(CustomerPreferences::getCity).orElse(null);
        List<Long> bought = purchaseHistoryRepository.findById(caller.userId())
                .map(PurchaseHistory::getEventIds)
                .orElse(List.of());

        Map<Long, Candidate> candidates = new LinkedHashMap<>();
        if (!favorites.isEmpty()) {
            search(favorites, null, null).forEach(event -> candidate(candidates, event).favoriteParticipant = true);
        }
        if (!types.isEmpty()) {
            search(null, types, null).forEach(event -> candidate(candidates, event));
        }
        if (city != null) {
            search(null, null, city).forEach(event -> candidate(candidates, event));
        }
        purchaseHistoryService.coPurchased(caller.userId(), bought, CO_PURCHASE_LIMIT).forEach((eventId, buyers) ->
                findBuyable(eventId).ifPresent(detail -> {
                    Candidate candidate = candidate(candidates, detail.toSummary());
                    candidate.coBuyers = buyers;
                    candidate.favoriteParticipant |= detail.participantIds().stream().anyMatch(favorites::contains);
                }));
        bought.forEach(candidates::remove);

        if (candidates.isEmpty()) {
            return search(null, null, null).stream()
                    .filter(event -> !bought.contains(event.id()))
                    .limit(MAX_SUGGESTIONS)
                    .toList();
        }
        Set<String> typeNames = Set.copyOf(types.stream().map(Enum::name).toList());
        String normalizedCity = normalize(city);
        return candidates.values().stream()
                .sorted(Comparator.comparingInt((Candidate c) -> c.score(typeNames, normalizedCity)).reversed()
                        .thenComparing(c -> c.event.dateTime())
                        .thenComparing(c -> c.event.id()))
                .limit(MAX_SUGGESTIONS)
                .map(c -> c.event)
                .toList();
    }

    private List<EventSummary> search(List<Long> participantIds, List<EventType> types, String city) {
        return eventClient.search(participantIds, types, city, SEARCH_SIZE).content();
    }

    /** Un evento de la co-compra que ya no se puede comprar, o que dejó de ser público, se descarta. */
    private Optional<EventDetail> findBuyable(Long eventId) {
        try {
            EventDetail detail = eventClient.findEvent(eventId);
            boolean buyable = BUYABLE.contains(detail.status()) && detail.dateTime().isAfter(LocalDateTime.now(clock));
            return buyable ? Optional.of(detail) : Optional.empty();
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    private static Candidate candidate(Map<Long, Candidate> candidates, EventSummary event) {
        return candidates.computeIfAbsent(event.id(), id -> new Candidate(event));
    }

    private static void requireClient(Caller caller) {
        if (!CLIENT_ROLE.equals(caller.role())) {
            throw new TicketYaException(CommonErrorCode.FORBIDDEN,
                    "Las sugerencias son solo para clientes, y el usuario %d no lo es".formatted(caller.userId()));
        }
    }

    /** Como la collation de event-service: sin distinguir mayúsculas ni acentos. */
    private static String normalize(String city) {
        if (city == null) {
            return null;
        }
        return Normalizer.normalize(city.strip(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private static final class Candidate {

        private final EventSummary event;
        private boolean favoriteParticipant;
        private int coBuyers;

        private Candidate(EventSummary event) {
            this.event = event;
        }

        /**
         * El participante favorito sale de la consulta que lo trajo (el resumen solo tiene nombres) o del detalle;
         * el tipo y la ciudad, del evento.
         */
        private int score(Collection<String> typeNames, String normalizedCity) {
            int score = favoriteParticipant ? FAVORITE_PARTICIPANT_POINTS : 0;
            if (typeNames.contains(event.type())) {
                score += EVENT_TYPE_POINTS;
            }
            if (normalizedCity != null && event.venue() != null
                    && normalizedCity.equals(normalize(event.venue().city()))) {
                score += CITY_POINTS;
            }
            return score + Math.min(coBuyers, MAX_CO_PURCHASE_POINTS);
        }
    }
}

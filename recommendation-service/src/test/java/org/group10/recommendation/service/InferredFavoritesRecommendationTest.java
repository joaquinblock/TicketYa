package org.group10.recommendation.service;

import org.group10.common.security.Caller;
import org.group10.recommendation.client.EventClient;
import org.group10.recommendation.dto.EventPage;
import org.group10.recommendation.dto.EventSummary;
import org.group10.recommendation.entity.CustomerPreferences;
import org.group10.recommendation.entity.EventType;
import org.group10.recommendation.entity.PurchaseHistory;
import org.group10.recommendation.repository.PreferencesRepository;
import org.group10.recommendation.repository.PurchaseHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Favoritos inferidos de las compras: un participante, lugar u organización que aparece en al menos 2 eventos
 * comprados, los 3 más repetidos de cada uno.
 */
@ExtendWith(MockitoExtension.class)
class InferredFavoritesRecommendationTest {

    private static final Caller CLIENT = new Caller(7L, "CLIENT", Set.of(), null);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-22T15:00:00Z"),
            ZoneId.of("America/Argentina/Buenos_Aires"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock
    private PreferencesRepository preferencesRepository;

    @Mock
    private PurchaseHistoryRepository purchaseHistoryRepository;

    @Mock
    private PurchaseHistoryService purchaseHistoryService;

    @Mock
    private EventClient eventClient;

    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService(preferencesRepository, purchaseHistoryRepository,
                purchaseHistoryService, eventClient, CLOCK);
    }

    @Test
    void participantBoughtTwiceIsSearchedAndScoredLikeAnExplicitFavorite() {
        history(counts(Map.of("10", 2, "11", 1), Map.of(), Map.of()));
        byParticipants(List.of(10L), event(30L, 1L, 1L, 20));

        assertThat(ids(recommendationService.suggest(CLIENT))).containsExactly(30L);
    }

    @Test
    void participantBoughtOnceIsNotInferred() {
        history(counts(Map.of("10", 1), Map.of(), Map.of()));
        upcoming(event(30L, 1L, 1L, 20));

        recommendationService.suggest(CLIENT);

        verify(eventClient, never()).search(anyList(), isNull(), isNull(), anyInt());
    }

    @Test
    void onlyTheThreeMostBoughtParticipantsAreInferred() {
        history(counts(Map.of("10", 5, "11", 4, "12", 3, "13", 2), Map.of(), Map.of()));
        byParticipants(List.of(10L, 11L, 12L), event(30L, 1L, 1L, 20));

        recommendationService.suggest(CLIENT);

        verify(eventClient).search(eq(List.of(10L, 11L, 12L)), isNull(), isNull(), anyInt());
    }

    @Test
    void explicitAndInferredParticipantsAreSearchedTogetherWithoutRepeats() {
        preferences(List.of(), List.of(12L, 10L));
        history(counts(Map.of("10", 2, "11", 2), Map.of(), Map.of()));
        byParticipants(List.of(12L, 10L, 11L), event(30L, 1L, 1L, 20));

        recommendationService.suggest(CLIENT);

        verify(eventClient).search(eq(List.of(12L, 10L, 11L)), isNull(), isNull(), anyInt());
    }

    @Test
    void inferredVenueAndOrganizationAddOnePointEach() {
        preferences(List.of(EventType.CONCERT), List.of());
        history(counts(Map.of(), Map.of("4", 2), Map.of("1", 3)));
        EventSummary elsewhereSooner = event(30L, 9L, 9L, 5);
        EventSummary inVenueAndOrganization = event(31L, 4L, 1L, 30);
        EventSummary inOrganization = event(32L, 9L, 1L, 20);
        byTypes(List.of(EventType.CONCERT), elsewhereSooner, inVenueAndOrganization, inOrganization);

        // Los tres son conciertos (+2). 31 suma lugar y organización (+2), 32 solo la organización (+1).
        assertThat(ids(recommendationService.suggest(CLIENT))).containsExactly(31L, 32L, 30L);
    }

    @Test
    void historyWithoutCountsInfersNothing() {
        when(purchaseHistoryRepository.findById(CLIENT.userId())).thenReturn(Optional.of(PurchaseHistory.builder()
                .userId(CLIENT.userId())
                .eventIds(List.of(1L))
                .build()));
        upcoming(event(30L, 1L, 1L, 20));

        assertThat(ids(recommendationService.suggest(CLIENT))).containsExactly(30L);
    }

    private void preferences(List<EventType> types, List<Long> participantIds) {
        when(preferencesRepository.findById(CLIENT.userId())).thenReturn(Optional.of(CustomerPreferences.builder()
                .userId(CLIENT.userId())
                .eventTypes(types)
                .participantIds(participantIds)
                .build()));
    }

    private void history(PurchaseHistory.FavoriteCounts counts) {
        when(purchaseHistoryRepository.findById(CLIENT.userId())).thenReturn(Optional.of(PurchaseHistory.builder()
                .userId(CLIENT.userId())
                .eventIds(List.of(1L, 2L))
                .counts(counts)
                .build()));
    }

    private static PurchaseHistory.FavoriteCounts counts(Map<String, Integer> participants, Map<String, Integer> venues,
                                                         Map<String, Integer> organizations) {
        return new PurchaseHistory.FavoriteCounts(participants, venues, organizations);
    }

    private void byParticipants(List<Long> ids, EventSummary... events) {
        when(eventClient.search(eq(ids), isNull(), isNull(), anyInt())).thenReturn(new EventPage(List.of(events)));
    }

    private void byTypes(List<EventType> types, EventSummary... events) {
        when(eventClient.search(isNull(), eq(types), isNull(), anyInt())).thenReturn(new EventPage(List.of(events)));
    }

    private void upcoming(EventSummary... events) {
        when(eventClient.search(isNull(), isNull(), isNull(), anyInt())).thenReturn(new EventPage(List.of(events)));
    }

    private static EventSummary event(long id, long venueId, long organizationId, int daysFromNow) {
        return new EventSummary(id, "Evento " + id, "CONCERT", "PUBLISHED", NOW.plusDays(daysFromNow), false,
                organizationId, new EventSummary.Venue(venueId, "Lugar", "Calle 1", "Rosario", null, null),
                new BigDecimal("10000"), List.of());
    }

    private static List<Long> ids(List<EventSummary> events) {
        return events.stream().map(EventSummary::id).toList();
    }
}

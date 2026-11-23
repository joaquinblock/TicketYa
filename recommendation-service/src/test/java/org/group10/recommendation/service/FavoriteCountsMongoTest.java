package org.group10.recommendation.service;

import org.group10.recommendation.entity.PurchaseHistory;
import org.group10.recommendation.repository.PurchaseHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contadores de participantes, lugares y organizaciones de lo que compró el cliente, contra el MongoDB de
 * {@code compose.yaml}. Se cuenta por evento: un mensaje repetido no suma dos veces.
 */
@DataMongoTest
@ActiveProfiles("test")
@Import(PurchaseHistoryService.class)
class FavoriteCountsMongoTest {

    private static final long CLIENT_ID = 7L;

    @Autowired
    private PurchaseHistoryService purchaseHistoryService;

    @Autowired
    private PurchaseHistoryRepository purchaseHistoryRepository;

    @BeforeEach
    void cleanUp() {
        purchaseHistoryRepository.deleteAll();
    }

    @Test
    void firstEventCountsItsParticipantsVenueAndOrganization() {
        purchaseHistoryService.countFavorites(CLIENT_ID, 3L, List.of(10L, 11L), 4L, 1L);

        PurchaseHistory.FavoriteCounts counts = countsOf(CLIENT_ID);
        assertThat(counts.getParticipants()).containsExactlyInAnyOrderEntriesOf(Map.of("10", 1, "11", 1));
        assertThat(counts.getVenues()).containsExactlyEntriesOf(Map.of("4", 1));
        assertThat(counts.getOrganizations()).containsExactlyEntriesOf(Map.of("1", 1));
    }

    @Test
    void sameEventTwiceCountsOnce() {
        purchaseHistoryService.countFavorites(CLIENT_ID, 3L, List.of(10L), 4L, 1L);
        purchaseHistoryService.countFavorites(CLIENT_ID, 3L, List.of(10L), 4L, 1L);

        assertThat(countsOf(CLIENT_ID).getParticipants()).containsExactlyEntriesOf(Map.of("10", 1));
    }

    @Test
    void differentEventsAddUp() {
        purchaseHistoryService.countFavorites(CLIENT_ID, 3L, List.of(10L), 4L, 1L);
        purchaseHistoryService.countFavorites(CLIENT_ID, 5L, List.of(10L, 12L), 6L, 1L);

        PurchaseHistory.FavoriteCounts counts = countsOf(CLIENT_ID);
        assertThat(counts.getParticipants()).containsExactlyInAnyOrderEntriesOf(Map.of("10", 2, "12", 1));
        assertThat(counts.getVenues()).containsExactlyInAnyOrderEntriesOf(Map.of("4", 1, "6", 1));
        assertThat(counts.getOrganizations()).containsExactlyEntriesOf(Map.of("1", 2));
    }

    @Test
    void countingKeepsTheHistoryAndTheHistoryKeepsTheCounts() {
        purchaseHistoryService.record(CLIENT_ID, 3L);
        purchaseHistoryService.countFavorites(CLIENT_ID, 3L, List.of(10L), 4L, 1L);
        purchaseHistoryService.record(CLIENT_ID, 5L);

        PurchaseHistory history = purchaseHistoryRepository.findById(CLIENT_ID).orElseThrow();
        assertThat(history.getEventIds()).containsExactly(3L, 5L);
        assertThat(history.getCounts().getParticipants()).containsExactlyEntriesOf(Map.of("10", 1));
    }

    @Test
    void eventWithoutParticipantsStillCountsItsVenueAndOrganization() {
        purchaseHistoryService.countFavorites(CLIENT_ID, 3L, List.of(), 4L, 1L);

        PurchaseHistory.FavoriteCounts counts = countsOf(CLIENT_ID);
        assertThat(counts.getParticipants()).isNullOrEmpty();
        assertThat(counts.getVenues()).containsExactlyEntriesOf(Map.of("4", 1));
    }

    private PurchaseHistory.FavoriteCounts countsOf(long userId) {
        return purchaseHistoryRepository.findById(userId).map(PurchaseHistory::getCounts).orElseThrow();
    }
}

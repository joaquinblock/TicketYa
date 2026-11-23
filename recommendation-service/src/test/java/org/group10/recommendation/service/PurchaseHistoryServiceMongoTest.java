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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Historial de compras contra el MongoDB de {@code compose.yaml}: el {@code $addToSet} con upsert tiene que dejar
 * cada evento una sola vez aunque el mensaje llegue repetido.
 */
@DataMongoTest
@ActiveProfiles("test")
@Import(PurchaseHistoryService.class)
class PurchaseHistoryServiceMongoTest {

    private static final long CLIENT_ID = 7L;
    private static final long OTHER_CLIENT_ID = 8L;

    @Autowired
    private PurchaseHistoryService purchaseHistoryService;

    @Autowired
    private PurchaseHistoryRepository purchaseHistoryRepository;

    @BeforeEach
    void cleanUp() {
        purchaseHistoryRepository.deleteAll();
    }

    @Test
    void firstPurchaseCreatesTheClientsHistory() {
        purchaseHistoryService.record(CLIENT_ID, 3L);

        assertThat(eventIdsOf(CLIENT_ID)).containsExactly(3L);
    }

    @Test
    void sameEventTwiceIsKeptOnce() {
        purchaseHistoryService.record(CLIENT_ID, 3L);
        purchaseHistoryService.record(CLIENT_ID, 3L);

        assertThat(eventIdsOf(CLIENT_ID)).containsExactly(3L);
    }

    @Test
    void anotherEventIsAddedToTheSameHistory() {
        purchaseHistoryService.record(CLIENT_ID, 3L);
        purchaseHistoryService.record(CLIENT_ID, 5L);

        assertThat(eventIdsOf(CLIENT_ID)).containsExactly(3L, 5L);
        assertThat(purchaseHistoryRepository.count()).isOne();
    }

    @Test
    void eachClientHasItsOwnHistory() {
        purchaseHistoryService.record(CLIENT_ID, 3L);
        purchaseHistoryService.record(OTHER_CLIENT_ID, 5L);

        assertThat(eventIdsOf(CLIENT_ID)).containsExactly(3L);
        assertThat(eventIdsOf(OTHER_CLIENT_ID)).containsExactly(5L);
    }

    private List<Long> eventIdsOf(long userId) {
        return purchaseHistoryRepository.findById(userId).map(PurchaseHistory::getEventIds).orElseThrow();
    }
}

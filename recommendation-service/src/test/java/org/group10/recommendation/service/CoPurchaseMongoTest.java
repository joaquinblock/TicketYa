package org.group10.recommendation.service;

import org.group10.recommendation.config.MongoIndexes;
import org.group10.recommendation.entity.PurchaseHistory;
import org.group10.recommendation.repository.PurchaseHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Quienes compraron entradas para este evento también compraron…" (US-REC-02), con la aggregation contra el MongoDB
 * de {@code compose.yaml}.
 */
@DataMongoTest
@ActiveProfiles("test")
@Import({PurchaseHistoryService.class, MongoIndexes.class})
class CoPurchaseMongoTest {

    private static final long CLIENT_ID = 7L;

    @Autowired
    private PurchaseHistoryService purchaseHistoryService;

    @Autowired
    private PurchaseHistoryRepository purchaseHistoryRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void cleanUp() {
        purchaseHistoryRepository.deleteAll();
    }

    @Test
    void countsTheOtherClientsWhoBoughtTheSameEventsAndWhatElseTheyBought() {
        history(CLIENT_ID, 1L, 2L);
        history(20L, 1L, 3L);
        history(21L, 2L, 3L, 4L);
        history(22L, 5L);

        assertThat(purchaseHistoryService.coPurchased(CLIENT_ID, List.of(1L, 2L), 5))
                .containsExactly(Map.entry(3L, 2), Map.entry(4L, 1));
    }

    @Test
    void eventsTheClientAlreadyBoughtAreLeftOut() {
        history(CLIENT_ID, 1L, 2L);
        history(20L, 1L, 2L);

        assertThat(purchaseHistoryService.coPurchased(CLIENT_ID, List.of(1L, 2L), 5)).isEmpty();
    }

    @Test
    void keepsTheMostBoughtUpToTheLimit() {
        history(CLIENT_ID, 1L);
        history(20L, 1L, 3L, 4L);
        history(21L, 1L, 4L, 5L);
        history(22L, 1L, 4L);

        assertThat(purchaseHistoryService.coPurchased(CLIENT_ID, List.of(1L), 2).keySet()).containsExactly(4L, 3L);
    }

    @Test
    void withoutPurchasesThereIsNothingToCompare() {
        history(20L, 1L);

        assertThat(purchaseHistoryService.coPurchased(CLIENT_ID, List.of(), 5)).isEmpty();
    }

    @Test
    void eventIdsHasAnIndexForTheCoPurchaseMatch() {
        List<IndexInfo> indexes = mongoTemplate.indexOps(PurchaseHistory.class).getIndexInfo();

        assertThat(indexes).anySatisfy(index -> assertThat(index.isIndexForFields(List.of("eventIds"))).isTrue());
    }

    private void history(long userId, Long... eventIds) {
        purchaseHistoryRepository.save(PurchaseHistory.builder().userId(userId).eventIds(List.of(eventIds)).build());
    }
}

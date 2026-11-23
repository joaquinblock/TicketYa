package org.group10.recommendation.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.group10.recommendation.entity.PurchaseHistory;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

/**
 * Índices de MongoDB, creados al arrancar de forma explícita (sin {@code auto-index-creation}). Crear uno que ya
 * existe no hace nada.
 * <ul>
 *   <li>{@code purchase_history.eventIds}: multikey, para encontrar a los clientes que compraron alguno de los eventos
 *       de otro en la co-compra.</li>
 * </ul>
 */
@Configuration
@RequiredArgsConstructor
public class MongoIndexes {

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    void createIndexes() {
        mongoTemplate.indexOps(PurchaseHistory.class).createIndex(new Index().on("eventIds", Sort.Direction.ASC));
    }
}

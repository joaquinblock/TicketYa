package org.group10.recommendation.service;

import lombok.RequiredArgsConstructor;
import org.group10.recommendation.entity.PurchaseHistory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Historial de compras de cada cliente, la base de las sugerencias por co-compra (US-REC-02).
 */
@Service
@RequiredArgsConstructor
public class PurchaseHistoryService {

    private final MongoTemplate mongoTemplate;

    /**
     * Suma el evento al historial del cliente y lo crea si no existía. Es una sola operación atómica
     * ({@code upsert} con {@code $addToSet}): si el mensaje llega repetido, el evento no se duplica.
     */
    public void record(long userId, long eventId) {
        mongoTemplate.upsert(Query.query(Criteria.where("_id").is(userId)),
                new Update().addToSet("eventIds", eventId), PurchaseHistory.class);
    }
}

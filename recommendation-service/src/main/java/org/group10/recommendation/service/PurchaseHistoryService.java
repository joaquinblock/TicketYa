package org.group10.recommendation.service;

import lombok.RequiredArgsConstructor;
import org.group10.recommendation.entity.PurchaseHistory;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * "Quienes compraron entradas para estos eventos también compraron…": entre los otros clientes que compraron
     * alguno de {@code eventIds}, cuenta cuántos compraron cada evento que el cliente todavía no tiene. El primer
     * {@code $match} usa el índice de {@code eventIds} ({@link org.group10.recommendation.config.MongoIndexes}).
     *
     * @return cantidad de clientes por evento, de mayor a menor (a igual cantidad, el de menor id), hasta {@code limit}
     */
    public Map<Long, Integer> coPurchased(long userId, List<Long> eventIds, int limit) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("eventIds").in(eventIds).and("_id").ne(userId)),
                Aggregation.unwind("eventIds"),
                Aggregation.match(Criteria.where("eventIds").nin(eventIds)),
                Aggregation.group("eventIds").count().as("buyers"),
                Aggregation.sort(Sort.by(Sort.Order.desc("buyers"), Sort.Order.asc("_id"))),
                Aggregation.limit(limit));
        Map<Long, Integer> buyersByEvent = new LinkedHashMap<>();
        mongoTemplate.aggregate(aggregation, PurchaseHistory.class, CoPurchase.class)
                .forEach(row -> buyersByEvent.put(row.id(), row.buyers()));
        return buyersByEvent;
    }

    private record CoPurchase(@Id Long id, int buyers) {
    }
}

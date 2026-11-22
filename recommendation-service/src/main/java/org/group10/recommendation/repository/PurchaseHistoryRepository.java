package org.group10.recommendation.repository;

import org.group10.recommendation.entity.PurchaseHistory;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PurchaseHistoryRepository extends MongoRepository<PurchaseHistory, Long> {
}

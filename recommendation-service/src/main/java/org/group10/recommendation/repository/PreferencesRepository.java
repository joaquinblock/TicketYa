package org.group10.recommendation.repository;

import org.group10.recommendation.entity.CustomerPreferences;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PreferencesRepository extends MongoRepository<CustomerPreferences, Long> {
}

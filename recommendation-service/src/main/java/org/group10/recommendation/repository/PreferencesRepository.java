package org.group10.recommendation.repository;

import org.group10.recommendation.entity.CustomerPreferences;
import org.springframework.data.mongodb.core.annotation.Collation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PreferencesRepository extends MongoRepository<CustomerPreferences, Long> {

    /** Sin distinguir mayúsculas ni acentos ({@code strength: 1}), como la collation de event-service. */
    @Collation("{ 'locale': 'es', 'strength': 1 }")
    List<CustomerPreferences> findByCity(String city);
}

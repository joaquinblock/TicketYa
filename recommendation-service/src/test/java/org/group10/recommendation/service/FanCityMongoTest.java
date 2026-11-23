package org.group10.recommendation.service;

import org.group10.recommendation.entity.CustomerPreferences;
import org.group10.recommendation.repository.PreferencesRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La ciudad del lugar contra la que guardó el cliente, en el MongoDB de {@code compose.yaml}: sin distinguir
 * mayúsculas ni acentos, como la búsqueda de event-service. Cada test usa sus propios ids y solo afirma sobre ellos.
 */
@DataMongoTest
@ActiveProfiles("test")
@Import(FanService.class)
class FanCityMongoTest {

    private static final long FAN_ID = 90_001L;
    private static final long OTHER_CITY_ID = 90_002L;

    @Autowired
    private FanService fanService;

    @Autowired
    private PreferencesRepository preferencesRepository;

    @AfterEach
    void cleanUp() {
        preferencesRepository.deleteAllById(List.of(FAN_ID, OTHER_CITY_ID));
    }

    @ParameterizedTest
    @ValueSource(strings = {"San Nicolás", "san nicolas", "SAN NICOLÁS"})
    void findFans_cityWrittenDifferently_findsTheFan(String venueCity) {
        save(FAN_ID, "San Nicolás");

        assertThat(fanService.findFans(List.of(10L), venueCity)).contains(FAN_ID);
    }

    @Test
    void findFans_otherCity_isNotAFan() {
        save(FAN_ID, "San Nicolás");
        save(OTHER_CITY_ID, "Rosario");

        assertThat(fanService.findFans(List.of(10L), "San Nicolás")).contains(FAN_ID).doesNotContain(OTHER_CITY_ID);
    }

    private void save(long userId, String city) {
        preferencesRepository.save(CustomerPreferences.builder()
                .userId(userId).participantIds(List.of(10L)).city(city).build());
    }
}

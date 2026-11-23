package org.group10.recommendation.service;

import org.group10.recommendation.entity.CustomerPreferences;
import org.group10.recommendation.entity.PurchaseHistory;
import org.group10.recommendation.repository.PreferencesRepository;
import org.group10.recommendation.repository.PurchaseHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FanServiceTest {

    private static final String CITY = "Tandil";

    @Mock
    private PreferencesRepository preferencesRepository;

    @Mock
    private PurchaseHistoryRepository purchaseHistoryRepository;

    @InjectMocks
    private FanService fanService;

    @Test
    void findFans_chosenFavoriteInTheCity_isAFan() {
        when(preferencesRepository.findByCity(CITY)).thenReturn(List.of(preferences(7L, 10L), preferences(8L, 99L)));
        when(purchaseHistoryRepository.findAllById(any())).thenReturn(List.of());

        assertThat(fanService.findFans(List.of(10L, 11L), CITY)).containsExactly(7L);
    }

    @Test
    void findFans_favoriteInferredFromTwoPurchases_isAFan() {
        when(preferencesRepository.findByCity(CITY)).thenReturn(List.of(preferences(7L)));
        when(purchaseHistoryRepository.findAllById(any())).thenReturn(List.of(history(7L, Map.of("10", 2))));

        assertThat(fanService.findFans(List.of(10L), CITY)).containsExactly(7L);
    }

    @Test
    void findFans_participantBoughtOnlyOnce_isNotAFan() {
        when(preferencesRepository.findByCity(CITY)).thenReturn(List.of(preferences(7L)));
        when(purchaseHistoryRepository.findAllById(any())).thenReturn(List.of(history(7L, Map.of("10", 1))));

        assertThat(fanService.findFans(List.of(10L), CITY)).isEmpty();
    }

    @Test
    void findFans_participantOutsideTheTopThreeInferred_isNotAFan() {
        when(preferencesRepository.findByCity(CITY)).thenReturn(List.of(preferences(7L)));
        when(purchaseHistoryRepository.findAllById(any())).thenReturn(List.of(
                history(7L, Map.of("1", 5, "2", 4, "3", 3, "10", 2))));

        assertThat(fanService.findFans(List.of(10L), CITY)).isEmpty();
    }

    @Test
    void findFans_severalFansWithChosenAndInferredFavorites_returnsEachOnceSorted() {
        when(preferencesRepository.findByCity(CITY)).thenReturn(List.of(preferences(9L, 10L), preferences(7L, 11L)));
        when(purchaseHistoryRepository.findAllById(any())).thenReturn(List.of(history(9L, Map.of("11", 3))));

        assertThat(fanService.findFans(List.of(10L, 11L), CITY)).containsExactly(7L, 9L);
    }

    @Test
    void findFans_nobodyInTheCity_returnsEmptyWithoutReadingHistories() {
        when(preferencesRepository.findByCity(CITY)).thenReturn(List.of());

        assertThat(fanService.findFans(List.of(10L), CITY)).isEmpty();
        verifyNoInteractions(purchaseHistoryRepository);
    }

    @Test
    void findFans_cityWithSurroundingSpaces_looksItUpStripped() {
        when(preferencesRepository.findByCity(CITY)).thenReturn(List.of(preferences(7L, 10L)));
        when(purchaseHistoryRepository.findAllById(any())).thenReturn(List.of());

        assertThat(fanService.findFans(List.of(10L), "  Tandil ")).containsExactly(7L);
    }

    private static CustomerPreferences preferences(Long userId, Long... participantIds) {
        return CustomerPreferences.builder().userId(userId).participantIds(List.of(participantIds)).city(CITY).build();
    }

    private static PurchaseHistory history(Long userId, Map<String, Integer> participants) {
        return PurchaseHistory.builder()
                .userId(userId)
                .counts(new PurchaseHistory.FavoriteCounts(participants, Map.of(), Map.of()))
                .build();
    }
}

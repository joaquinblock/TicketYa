package org.group10.recommendation.service;

import lombok.RequiredArgsConstructor;
import org.group10.recommendation.entity.CustomerPreferences;
import org.group10.recommendation.entity.PurchaseHistory;
import org.group10.recommendation.repository.PreferencesRepository;
import org.group10.recommendation.repository.PurchaseHistoryRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Los clientes a los que hay que avisar que se publicó un evento de un artista favorito en su ciudad (US-NOT-03).
 * <ul>
 *   <li>La ciudad es la de sus preferencias, sin distinguir mayúsculas ni acentos: sin ciudad guardada no hay aviso.</li>
 *   <li>Sus favoritos son los que eligió más los inferidos de sus compras, los mismos de las sugerencias
 *       ({@link PurchaseHistory.FavoriteCounts#inferredParticipants}).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class FanService {

    private final PreferencesRepository preferencesRepository;
    private final PurchaseHistoryRepository purchaseHistoryRepository;

    /**
     * @return ids de los clientes, de menor a mayor
     */
    public List<Long> findFans(Collection<Long> participantIds, String city) {
        List<CustomerPreferences> inCity = preferencesRepository.findByCity(city.strip());
        if (inCity.isEmpty()) {
            return List.of();
        }
        Map<Long, PurchaseHistory> histories = purchaseHistoryRepository
                .findAllById(inCity.stream().map(CustomerPreferences::getUserId).toList()).stream()
                .collect(Collectors.toMap(PurchaseHistory::getUserId, Function.identity()));
        return inCity.stream()
                .filter(preferences -> favoritesOf(preferences, histories.get(preferences.getUserId()))
                        .anyMatch(participantIds::contains))
                .map(CustomerPreferences::getUserId)
                .sorted()
                .toList();
    }

    private static Stream<Long> favoritesOf(CustomerPreferences preferences, PurchaseHistory history) {
        Stream<Long> chosen = preferences.getParticipantIds() == null
                ? Stream.empty() : preferences.getParticipantIds().stream();
        Stream<Long> inferred = history == null || history.getCounts() == null
                ? Stream.empty() : history.getCounts().inferredParticipants().stream();
        return Stream.concat(chosen, inferred);
    }
}

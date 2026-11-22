package org.group10.recommendation.service;

import lombok.RequiredArgsConstructor;
import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.group10.common.security.Caller;
import org.group10.recommendation.client.EventClient;
import org.group10.recommendation.dto.PreferencesRequest;
import org.group10.recommendation.dto.PreferencesResponse;
import org.group10.recommendation.entity.CustomerPreferences;
import org.group10.recommendation.entity.EventType;
import org.group10.recommendation.exception.RecommendationErrorCode;
import org.group10.recommendation.repository.PreferencesRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Preferencias del cliente (US-REC-01). Solo las tiene el rol CLIENT.
 */
@Service
@RequiredArgsConstructor
public class PreferenceService {

    private static final String CLIENT_ROLE = "CLIENT";

    private final PreferencesRepository preferencesRepository;
    private final EventClient eventClient;

    public PreferencesResponse get(Caller caller) {
        requireClient(caller);
        return preferencesRepository.findById(caller.userId())
                .map(PreferencesResponse::from)
                .orElse(PreferencesResponse.EMPTY);
    }

    /**
     * Valida todo antes de guardar: un tipo o un participante inexistente no deja nada a medias.
     *
     * @throws TicketYaException {@code INVALID_EVENT_TYPE} si un tipo no existe
     */
    public PreferencesResponse replace(Caller caller, PreferencesRequest request) {
        requireClient(caller);
        List<EventType> eventTypes = distinct(request.eventTypes()).stream().map(PreferenceService::eventType).toList();
        List<Long> participantIds = distinct(request.participantIds());
        participantIds.forEach(eventClient::getParticipant);

        CustomerPreferences preferences = CustomerPreferences.builder()
                .userId(caller.userId())
                .eventTypes(eventTypes)
                .participantIds(participantIds)
                .city(blankToNull(request.city()))
                .build();
        return PreferencesResponse.from(preferencesRepository.save(preferences));
    }

    private static void requireClient(Caller caller) {
        if (!CLIENT_ROLE.equals(caller.role())) {
            throw new TicketYaException(CommonErrorCode.FORBIDDEN,
                    "Las preferencias son solo de los clientes, y el usuario %d no lo es".formatted(caller.userId()));
        }
    }

    private static EventType eventType(String name) {
        return Arrays.stream(EventType.values())
                .filter(type -> type.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new TicketYaException(RecommendationErrorCode.INVALID_EVENT_TYPE,
                        "No existe el tipo de evento '%s'".formatted(name)));
    }

    private static <T> List<T> distinct(List<T> values) {
        return values == null ? List.of() : values.stream().distinct().toList();
    }

    private static String blankToNull(String city) {
        return city == null || city.isBlank() ? null : city.strip();
    }
}

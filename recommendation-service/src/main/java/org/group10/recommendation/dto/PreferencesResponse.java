package org.group10.recommendation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.group10.recommendation.entity.CustomerPreferences;
import org.group10.recommendation.entity.EventType;

import java.util.List;

@Schema(description = "Preferencias del cliente")
public record PreferencesResponse(
        @Schema(example = "[\"CONCERT\"]") List<String> eventTypes,
        @Schema(example = "[1, 2]") List<Long> participantIds,
        @Schema(example = "Tandil") String city
) {

    public static final PreferencesResponse EMPTY = new PreferencesResponse(List.of(), List.of(), null);

    public static PreferencesResponse from(CustomerPreferences preferences) {
        return new PreferencesResponse(
                preferences.getEventTypes().stream().map(EventType::name).toList(),
                List.copyOf(preferences.getParticipantIds()),
                preferences.getCity());
    }
}

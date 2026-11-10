package org.group10.event.mapper;

import org.group10.event.dto.ParticipantRequest;
import org.group10.event.dto.ParticipantResponse;
import org.group10.event.entity.Participant;

public final class ParticipantMapper {

    private ParticipantMapper() {
    }

    public static Participant toEntity(ParticipantRequest request) {
        return Participant.builder().name(request.name().trim()).type(request.type()).build();
    }

    public static void update(Participant participant, ParticipantRequest request) {
        participant.setName(request.name().trim());
        participant.setType(request.type());
    }

    public static ParticipantResponse toResponse(Participant participant) {
        return new ParticipantResponse(participant.getId(), participant.getName(), participant.getType());
    }
}

package org.group10.event.service;

import lombok.RequiredArgsConstructor;
import org.group10.common.exception.TicketYaException;
import org.group10.event.dto.ParticipantRequest;
import org.group10.event.dto.ParticipantResponse;
import org.group10.event.entity.Participant;
import org.group10.event.entity.ParticipantType;
import org.group10.event.exception.EventErrorCode;
import org.group10.event.mapper.ParticipantMapper;
import org.group10.event.repository.EventRepository;
import org.group10.event.repository.ParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParticipantService {

    private final ParticipantRepository participantRepository;
    private final EventRepository eventRepository;

    /**
     * Lists participants ordered by name.
     *
     * @param type optional
     * @param name optional, part of the name, case-insensitive
     */
    public List<ParticipantResponse> findAll(ParticipantType type, String name) {
        String nameFilter = name == null || name.isBlank() ? null : name.trim();
        return participantRepository.search(type, nameFilter).stream().map(ParticipantMapper::toResponse).toList();
    }

    public ParticipantResponse findById(Long id) {
        return ParticipantMapper.toResponse(getParticipant(id));
    }

    /**
     * @throws TicketYaException {@code PARTICIPANT_DUPLICATED} if one with the same name and type exists
     */
    @Transactional
    public ParticipantResponse create(ParticipantRequest request) {
        ensureNameIsFree(request, null);
        return ParticipantMapper.toResponse(participantRepository.save(ParticipantMapper.toEntity(request)));
    }

    /**
     * Replaces name and type. The events that have the participant keep it, with the new data.
     *
     * @throws TicketYaException {@code PARTICIPANT_NOT_FOUND} or {@code PARTICIPANT_DUPLICATED}
     */
    @Transactional
    public ParticipantResponse update(Long id, ParticipantRequest request) {
        Participant participant = getParticipant(id);
        ensureNameIsFree(request, id);
        ParticipantMapper.update(participant, request);
        return ParticipantMapper.toResponse(participant);
    }

    /**
     * Deletes the participant for good.
     *
     * @throws TicketYaException {@code PARTICIPANT_IN_USE} if any event, in any status, has it
     */
    @Transactional
    public void delete(Long id) {
        Participant participant = getParticipant(id);
        if (eventRepository.existsByParticipantsId(id)) {
            throw new TicketYaException(EventErrorCode.PARTICIPANT_IN_USE,
                    "El participante %d está en eventos y no se puede eliminar".formatted(id));
        }
        participantRepository.delete(participant);
    }

    /**
     * Names are compared without case, like the unique {@code (name, type)} in MySQL: "los serranos" and
     * "Los Serranos" would split the favorites of one artist in two.
     */
    private void ensureNameIsFree(ParticipantRequest request, Long excludedId) {
        String name = request.name().trim();
        boolean taken = excludedId == null
                ? participantRepository.existsByNameIgnoreCaseAndType(name, request.type())
                : participantRepository.existsByNameIgnoreCaseAndTypeAndIdNot(name, request.type(), excludedId);
        if (taken) {
            throw new TicketYaException(EventErrorCode.PARTICIPANT_DUPLICATED,
                    "Ya existe el participante '%s' de tipo %s".formatted(name, request.type()));
        }
    }

    private Participant getParticipant(Long id) {
        return participantRepository.findById(id)
                .orElseThrow(() -> new TicketYaException(EventErrorCode.PARTICIPANT_NOT_FOUND,
                        "No se encontró el participante con id %d".formatted(id)));
    }
}

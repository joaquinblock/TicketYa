package org.group10.auth.service;

import lombok.RequiredArgsConstructor;
import org.group10.auth.dto.OrganizerResponse;
import org.group10.auth.entity.Organizer;
import org.group10.auth.entity.User;
import org.group10.auth.exception.AuthErrorCode;
import org.group10.auth.repository.OrganizerRepository;
import org.group10.common.exception.TicketYaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizerService {

    private final OrganizerRepository organizerRepository;

    /**
     * Returns the organizer only while it can act as one: an organizer that was removed, or whose user or
     * organization was deleted, is treated as not existing.
     *
     * @throws TicketYaException {@code ORGANIZER_NOT_FOUND}
     */
    public OrganizerResponse findActiveByUserId(Long userId) {
        Organizer organizer = organizerRepository.findWithUserAndOrganizationByUserId(userId)
                .filter(o -> o.isActive() && o.getUser().isActive() && o.getOrganization().isActive())
                .orElseThrow(() -> new TicketYaException(AuthErrorCode.ORGANIZER_NOT_FOUND,
                        "El usuario %d no es un organizador activo".formatted(userId)));
        User user = organizer.getUser();
        return new OrganizerResponse(user.getId(), user.getFirstName(), user.getLastName(),
                organizer.getOrganization().getId());
    }
}

package org.group10.auth.service;

import lombok.RequiredArgsConstructor;
import org.group10.auth.client.EventClient;
import org.group10.auth.dto.AccessAssignmentOutcome;
import org.group10.auth.dto.AccessAssignmentResponse;
import org.group10.auth.entity.AccessStaff;
import org.group10.auth.exception.AuthErrorCode;
import org.group10.auth.repository.AccessAssignmentRepository;
import org.group10.auth.repository.AccessStaffRepository;
import org.group10.common.exception.TicketYaException;
import org.group10.common.security.Caller;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccessAssignmentService {

    private final AccessStaffRepository accessStaffRepository;
    private final AccessAssignmentRepository accessAssignmentRepository;
    private final EventClient eventClient;

    @Transactional
    public AccessAssignmentOutcome assign(Caller caller, Long staffId, Long eventId) {
        return new AccessAssignmentOutcome(new AccessAssignmentResponse(staffId, eventId), false);
    }

    /**
     * @throws TicketYaException {@code STAFF_NOT_ASSIGNED} si el usuario no es personal de acceso activo asignado al
     *                           evento
     */
    public void ensureAssigned(Long staffId, Long eventId) {
        accessStaffRepository.findById(staffId)
                .filter(AccessStaff::isActive)
                .filter(staff -> accessAssignmentRepository.existsByAccessStaffUserIdAndEventId(staffId, eventId))
                .orElseThrow(() -> new TicketYaException(AuthErrorCode.STAFF_NOT_ASSIGNED,
                        "El usuario %d no está asignado al evento %d".formatted(staffId, eventId)));
    }
}

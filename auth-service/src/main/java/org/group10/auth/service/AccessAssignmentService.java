package org.group10.auth.service;

import lombok.RequiredArgsConstructor;
import org.group10.auth.client.EventClient;
import org.group10.auth.dto.AccessAssignmentOutcome;
import org.group10.auth.dto.AccessAssignmentResponse;
import org.group10.auth.entity.AccessAssignment;
import org.group10.auth.entity.AccessStaff;
import org.group10.auth.exception.AuthErrorCode;
import org.group10.auth.repository.AccessAssignmentRepository;
import org.group10.auth.repository.AccessStaffRepository;
import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.group10.common.security.Caller;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccessAssignmentService {

    private final AccessStaffRepository accessStaffRepository;
    private final AccessAssignmentRepository accessAssignmentRepository;
    private final EventClient eventClient;

    @Transactional
    public AccessAssignmentOutcome assign(Caller caller, Long staffId, Long eventId) {
        AccessStaff staff = accessStaffRepository.findById(staffId)
                .filter(AccessStaff::isActive)
                .orElseThrow(() -> new TicketYaException(AuthErrorCode.ACCESS_STAFF_NOT_FOUND,
                        "El usuario %d no es personal de acceso".formatted(staffId)));
        ensureSameOrganization(caller, staff.getOrganization().getId(),
                "El personal %d no pertenece a la organización del usuario %d".formatted(staffId, caller.userId()));
        ensureSameOrganization(caller, eventClient.findEvent(eventId).organizationId(),
                "El usuario %d no pertenece a la organización del evento %d".formatted(caller.userId(), eventId));
        AccessAssignmentResponse assignment = new AccessAssignmentResponse(staffId, eventId);
        if (accessAssignmentRepository.existsByAccessStaffUserIdAndEventId(staffId, eventId)) {
            return new AccessAssignmentOutcome(assignment, false);
        }
        accessAssignmentRepository.save(AccessAssignment.builder().accessStaff(staff).eventId(eventId).build());
        return new AccessAssignmentOutcome(assignment, true);
    }

    private static void ensureSameOrganization(Caller caller, Long organizationId, String message) {
        if (!Objects.equals(caller.organizationId(), organizationId)) {
            throw new TicketYaException(CommonErrorCode.FORBIDDEN, message);
        }
    }

    /**
     * Los eventos donde el personal puede validar entradas, para su pantalla de inicio. Si no es personal de acceso o
     * está dado de baja, no tiene ninguno: la puerta igual lo rechaza con {@code STAFF_NOT_ASSIGNED}.
     */
    @Transactional(readOnly = true)
    public List<AccessAssignmentResponse> findOwn(Long staffId) {
        boolean active = accessStaffRepository.findById(staffId).filter(AccessStaff::isActive).isPresent();
        if (!active) {
            return List.of();
        }
        return accessAssignmentRepository.findByAccessStaffUserIdOrderByEventId(staffId).stream()
                .map(assignment -> new AccessAssignmentResponse(staffId, assignment.getEventId()))
                .toList();
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

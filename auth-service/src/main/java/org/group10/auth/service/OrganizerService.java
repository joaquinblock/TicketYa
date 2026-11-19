package org.group10.auth.service;

import lombok.RequiredArgsConstructor;
import org.group10.auth.dto.OrganizerResponse;
import org.group10.auth.entity.Organization;
import org.group10.auth.entity.Organizer;
import org.group10.auth.entity.Role;
import org.group10.auth.entity.User;
import org.group10.auth.exception.AuthErrorCode;
import org.group10.auth.repository.OrganizationRepository;
import org.group10.auth.repository.OrganizerRepository;
import org.group10.auth.repository.RoleRepository;
import org.group10.auth.repository.UserRepository;
import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizerService {

    private static final String CLIENT_ROLE = "CLIENT";
    private static final String ORGANIZER_ROLE = "ORGANIZER";

    private final OrganizerRepository organizerRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    /**
     * Makes a client an organizer of the organization: creates (or reactivates) its {@code organizer} row and changes
     * its role to {@code ORGANIZER}. The change reaches the user's token on its next login.
     * <p>
     * Only a client can be promoted: an admin or access staff would lose its role.
     *
     * @throws TicketYaException {@code ORGANIZATION_NOT_FOUND}, {@code USER_NOT_FOUND}, or {@code DATA_CONFLICT} if
     *                           the user already organizes or isn't a client
     */
    @Transactional
    public OrganizerResponse promote(Long organizationId, Long userId) {
        Organization organization = organizationRepository.findById(organizationId)
                .filter(Organization::isActive)
                .orElseThrow(() -> new TicketYaException(AuthErrorCode.ORGANIZATION_NOT_FOUND,
                        "No se encontró la organización con id %d".formatted(organizationId)));
        User user = userRepository.findWithRoleAndPermissionsById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new TicketYaException(AuthErrorCode.USER_NOT_FOUND,
                        "No se encontró el usuario con id %d".formatted(userId)));
        Organizer organizer = organizerRepository.findById(userId).orElse(null);
        if (organizer != null && organizer.isActive()) {
            throw new TicketYaException(CommonErrorCode.DATA_CONFLICT,
                    "El usuario %d ya es organizador de la organización %d"
                            .formatted(userId, organizer.getOrganization().getId()));
        }
        if (!CLIENT_ROLE.equals(user.getRole().getName())) {
            throw new TicketYaException(CommonErrorCode.DATA_CONFLICT,
                    "Solo un cliente puede pasar a organizador: el usuario %d es %s"
                            .formatted(userId, user.getRole().getName()));
        }
        if (organizer == null) {
            organizer = Organizer.builder().user(user).organization(organization).build();
        } else {
            organizer.setOrganization(organization);
            organizer.setDeletedAt(null);
        }
        organizerRepository.save(organizer);
        user.setRole(role(ORGANIZER_ROLE));
        return new OrganizerResponse(user.getId(), user.getFirstName(), user.getLastName(), organization.getId());
    }

    /**
     * Stops a user from organizing for the organization: its {@code organizer} row is marked as deleted and it goes
     * back to {@code CLIENT}. The events it created stay with the organization.
     *
     * @throws TicketYaException {@code ORGANIZER_NOT_FOUND} if it isn't an active organizer of that organization
     */
    @Transactional
    public void remove(Long organizationId, Long userId) {
        Organizer organizer = organizerRepository.findWithUserAndOrganizationByUserId(userId)
                .filter(o -> o.isActive() && organizationId.equals(o.getOrganization().getId()))
                .orElseThrow(() -> new TicketYaException(AuthErrorCode.ORGANIZER_NOT_FOUND,
                        "El usuario %d no es organizador de la organización %d".formatted(userId, organizationId)));
        organizer.setDeletedAt(LocalDateTime.now());
        organizer.getUser().setRole(role(CLIENT_ROLE));
    }

    private Role role(String name) {
        return roleRepository.findByName(name)
                .orElseThrow(() -> new IllegalStateException("No existe el rol " + name));
    }

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

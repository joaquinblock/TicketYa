package org.group10.auth.service;

import lombok.RequiredArgsConstructor;
import org.group10.auth.dto.OrganizationResponse;
import org.group10.auth.entity.Organization;
import org.group10.auth.exception.AuthErrorCode;
import org.group10.auth.repository.OrganizationRepository;
import org.group10.common.exception.TicketYaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    /**
     * Only active organizations: one that was removed is treated as not existing.
     *
     * @throws TicketYaException {@code ORGANIZATION_NOT_FOUND}
     */
    public OrganizationResponse findById(Long id) {
        Organization organization = organizationRepository.findById(id)
                .filter(Organization::isActive)
                .orElseThrow(() -> new TicketYaException(AuthErrorCode.ORGANIZATION_NOT_FOUND,
                        "No se encontró la organización con id %d".formatted(id)));
        return new OrganizationResponse(organization.getId(), organization.getLegalName(),
                organization.getPublicName(), organization.getType());
    }
}

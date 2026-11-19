package org.group10.auth.service;

import lombok.RequiredArgsConstructor;
import org.group10.auth.dto.TokenResponse;
import org.group10.auth.dto.UserPermissionsResponse;
import org.group10.auth.entity.Organization;
import org.group10.auth.entity.Organizer;
import org.group10.auth.repository.OrganizerRepository;
import org.group10.auth.security.JwtProperties;
import org.group10.auth.security.JwtTokenProvider;
import org.group10.auth.security.TokenSubject;
import org.group10.common.exception.TicketYaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final String ORGANIZER_ROLE = "ORGANIZER";

    private final UserService userService;
    private final OrganizerRepository organizerRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    /**
     * Issues the TicketYa JWT of an active user, with its role, permissions and, for an organizer, its
     * organization, only while both the organizer and the organization are active: the services trust this claim
     * instead of asking the auth-service. Access staff gets no organization until {@code AccessStaff} exists.
     *
     * @throws TicketYaException {@code USER_NOT_FOUND}
     */
    public TokenResponse issueTokenFor(Long userId) {
        UserPermissionsResponse user = userService.findPermissions(userId);
        TokenSubject subject = new TokenSubject(user.userId(), user.role(), List.copyOf(user.permissions()),
                organizationOf(user));
        return new TokenResponse(jwtTokenProvider.issue(subject), "Bearer", jwtProperties.ttl().toSeconds());
    }

    private Long organizationOf(UserPermissionsResponse user) {
        if (!ORGANIZER_ROLE.equals(user.role())) {
            return null;
        }
        return organizerRepository.findWithUserAndOrganizationByUserId(user.userId())
                .filter(Organizer::isActive)
                .map(Organizer::getOrganization)
                .filter(Organization::isActive)
                .map(Organization::getId)
                .orElse(null);
    }
}

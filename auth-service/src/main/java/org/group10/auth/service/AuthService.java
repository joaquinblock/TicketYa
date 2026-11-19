package org.group10.auth.service;

import lombok.RequiredArgsConstructor;
import org.group10.auth.dto.LoginResponse;
import org.group10.auth.dto.TokenResponse;
import org.group10.auth.dto.UserPermissionsResponse;
import org.group10.auth.entity.Organization;
import org.group10.auth.entity.Organizer;
import org.group10.auth.entity.User;
import org.group10.auth.exception.AuthErrorCode;
import org.group10.auth.repository.OrganizerRepository;
import org.group10.auth.repository.RoleRepository;
import org.group10.auth.repository.UserRepository;
import org.group10.auth.security.GoogleIdTokenVerifier;
import org.group10.auth.security.GoogleIdentity;
import org.group10.auth.security.JwtProperties;
import org.group10.auth.security.JwtTokenProvider;
import org.group10.auth.security.TokenSubject;
import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final String ORGANIZER_ROLE = "ORGANIZER";
    private static final String CLIENT_ROLE = "CLIENT";

    private final UserService userService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OrganizerRepository organizerRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final JwtProperties jwtProperties;

    /**
     * Exchanges a Google ID token for the TicketYa JWT (RFC-001). Login and signup are the same operation:
     * <ol>
     *   <li>a user already linked to this Google account ({@code idp_subject}) gets its token;</li>
     *   <li>otherwise a user with the same verified email (preloaded by an admin) is linked and keeps its role;</li>
     *   <li>otherwise a new {@code CLIENT} is created. Only an admin can promote it.</li>
     * </ol>
     *
     * @throws TicketYaException {@code INVALID_CREDENTIALS} or {@code SERVICE_UNAVAILABLE} from the Google token;
     *                           {@code USER_DISABLED} if the user was deleted; {@code DATA_CONFLICT} if the email
     *                           belongs to a user linked to another Google account
     */
    @Transactional
    public LoginResponse loginWithGoogle(String idToken) {
        GoogleIdentity google = googleIdTokenVerifier.verify(idToken);
        User linked = userRepository.findByIdpSubject(google.subject()).orElse(null);
        if (linked != null) {
            return login(requireActive(linked), false);
        }
        User preloaded = userRepository.findByEmailIgnoreCase(google.email()).orElse(null);
        if (preloaded != null) {
            return login(link(requireActive(preloaded), google), false);
        }
        return login(createClient(google), true);
    }

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

    private LoginResponse login(User user, boolean newUser) {
        TokenResponse token = issueTokenFor(user.getId());
        return new LoginResponse(token.accessToken(), token.tokenType(), token.expiresIn(), newUser);
    }

    private static User requireActive(User user) {
        if (!user.isActive()) {
            throw new TicketYaException(AuthErrorCode.USER_DISABLED, "Tu cuenta está dada de baja");
        }
        return user;
    }

    private User link(User preloaded, GoogleIdentity google) {
        if (preloaded.getIdpSubject() != null) {
            throw new TicketYaException(CommonErrorCode.DATA_CONFLICT,
                    "Ese email ya está vinculado a otra cuenta de Google");
        }
        preloaded.setIdpSubject(google.subject());
        userRepository.save(preloaded);
        return preloaded;
    }

    private User createClient(GoogleIdentity google) {
        User client = User.builder()
                .email(google.email())
                .idpSubject(google.subject())
                .firstName(google.firstName())
                .lastName(google.lastName())
                .role(roleRepository.findByName(CLIENT_ROLE)
                        .orElseThrow(() -> new IllegalStateException("No existe el rol " + CLIENT_ROLE)))
                .build();
        return userRepository.save(client);
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

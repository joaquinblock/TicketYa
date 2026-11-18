package org.group10.auth.service;

import org.group10.auth.dto.TokenResponse;
import org.group10.auth.entity.Organization;
import org.group10.auth.entity.Organizer;
import org.group10.auth.entity.Permission;
import org.group10.auth.entity.Role;
import org.group10.auth.entity.User;
import org.group10.auth.exception.AuthErrorCode;
import org.group10.auth.repository.OrganizerRepository;
import org.group10.auth.repository.UserRepository;
import org.group10.auth.security.JwtProperties;
import org.group10.auth.security.JwtTokenProvider;
import org.group10.auth.security.TokenSubject;
import org.group10.common.exception.TicketYaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final JwtProperties PROPERTIES =
            new JwtProperties("ticketya-auth", "ticketya-api", Duration.ofMinutes(30), null);

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizerRepository organizerRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(new UserService(userRepository), organizerRepository, jwtTokenProvider,
                PROPERTIES);
    }

    @Test
    void issueTokenFor_client_returnsBearerTokenWithItsRoleAndSortedPermissionsAndNoOrganization() {
        when(userRepository.findWithRoleAndPermissionsById(7L))
                .thenReturn(Optional.of(user(7L, "CLIENT", null, "TICKET_PURCHASE", "EVENT_SEARCH")));
        when(jwtTokenProvider.issue(any())).thenReturn("signed-token");

        TokenResponse response = authService.issueTokenFor(7L);

        assertThat(response).isEqualTo(new TokenResponse("signed-token", "Bearer", 1800));
        assertThat(issuedSubject())
                .isEqualTo(new TokenSubject(7L, "CLIENT", List.of("EVENT_SEARCH", "TICKET_PURCHASE"), null));
        verifyNoInteractions(organizerRepository);
    }

    @Test
    void issueTokenFor_activeOrganizer_tokenCarriesItsOrganization() {
        when(userRepository.findWithRoleAndPermissionsById(2L))
                .thenReturn(Optional.of(user(2L, "ORGANIZER", null, "EVENT_MANAGE")));
        when(organizerRepository.findWithUserAndOrganizationByUserId(2L))
                .thenReturn(Optional.of(organizer(2L, 1L, null)));

        authService.issueTokenFor(2L);

        assertThat(issuedSubject().organizationId()).isEqualTo(1L);
    }

    @Test
    void issueTokenFor_organizerRemovedFromItsOrganization_tokenHasNoOrganization() {
        when(userRepository.findWithRoleAndPermissionsById(2L))
                .thenReturn(Optional.of(user(2L, "ORGANIZER", null, "EVENT_MANAGE")));
        when(organizerRepository.findWithUserAndOrganizationByUserId(2L))
                .thenReturn(Optional.of(organizer(2L, 1L, LocalDateTime.of(2026, 1, 1, 0, 0))));

        authService.issueTokenFor(2L);

        assertThat(issuedSubject().organizationId()).isNull();
    }

    @Test
    void issueTokenFor_missingUser_throwsUserNotFoundWithoutIssuing() {
        when(userRepository.findWithRoleAndPermissionsById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.issueTokenFor(99L))
                .isInstanceOf(TicketYaException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.USER_NOT_FOUND);
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void issueTokenFor_deletedUser_throwsUserNotFoundWithoutIssuing() {
        when(userRepository.findWithRoleAndPermissionsById(7L))
                .thenReturn(Optional.of(user(7L, "CLIENT", LocalDateTime.of(2026, 1, 1, 0, 0), "EVENT_SEARCH")));

        assertThatThrownBy(() -> authService.issueTokenFor(7L))
                .isInstanceOf(TicketYaException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.USER_NOT_FOUND);
        verifyNoInteractions(jwtTokenProvider);
    }

    private TokenSubject issuedSubject() {
        ArgumentCaptor<TokenSubject> subject = ArgumentCaptor.forClass(TokenSubject.class);
        verify(jwtTokenProvider).issue(subject.capture());
        return subject.getValue();
    }

    private static User user(Long id, String roleName, LocalDateTime deletedAt, String... permissions) {
        Role role = Role.builder()
                .name(roleName)
                .permissions(Arrays.stream(permissions)
                        .map(name -> Permission.builder().name(name).build())
                        .collect(Collectors.toSet()))
                .build();
        return User.builder().id(id).email(id + "@ticketya.com").role(role).deletedAt(deletedAt).build();
    }

    private static Organizer organizer(Long userId, Long organizationId, LocalDateTime deletedAt) {
        return Organizer.builder()
                .userId(userId)
                .organization(Organization.builder().id(organizationId).build())
                .deletedAt(deletedAt)
                .build();
    }
}

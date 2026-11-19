package org.group10.auth.service;

import org.group10.auth.dto.LoginResponse;
import org.group10.auth.entity.Permission;
import org.group10.auth.entity.Role;
import org.group10.auth.entity.User;
import org.group10.auth.exception.AuthErrorCode;
import org.group10.auth.repository.OrganizerRepository;
import org.group10.auth.repository.RoleRepository;
import org.group10.auth.repository.UserRepository;
import org.group10.auth.security.GoogleIdTokenVerifier;
import org.group10.auth.security.GoogleIdentity;
import org.group10.auth.security.JwtProperties;
import org.group10.auth.security.JwtTokenProvider;
import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceGoogleLoginTest {

    private static final String ID_TOKEN = "google-id-token";
    private static final GoogleIdentity ANA = new GoogleIdentity("google-sub-123", "ana@gmail.com", "Ana", "Gómez");

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private OrganizerRepository organizerRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(new UserService(userRepository), userRepository, roleRepository,
                organizerRepository, jwtTokenProvider, googleIdTokenVerifier,
                new JwtProperties("ticketya-auth", "ticketya-api", Duration.ofMinutes(30), null));
        lenient().when(jwtTokenProvider.issue(any())).thenReturn("signed-token");
    }

    @Test
    void loginWithGoogle_userAlreadyLinked_returnsItsTokenAndIsNotNew() {
        User ana = user(7L, "ana@gmail.com", "google-sub-123", "CLIENT", null);
        when(googleIdTokenVerifier.verify(ID_TOKEN)).thenReturn(ANA);
        when(userRepository.findByIdpSubject("google-sub-123")).thenReturn(Optional.of(ana));
        when(userRepository.findWithRoleAndPermissionsById(7L)).thenReturn(Optional.of(ana));

        LoginResponse response = authService.loginWithGoogle(ID_TOKEN);

        assertThat(response).isEqualTo(new LoginResponse("signed-token", "Bearer", 1800, false));
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginWithGoogle_preloadedEmailWithoutGoogle_linksItAndKeepsItsRole() {
        User organizer = user(2L, "ana@gmail.com", null, "ORGANIZER", null);
        when(googleIdTokenVerifier.verify(ID_TOKEN)).thenReturn(ANA);
        when(userRepository.findByIdpSubject("google-sub-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("ana@gmail.com")).thenReturn(Optional.of(organizer));
        when(userRepository.findWithRoleAndPermissionsById(2L)).thenReturn(Optional.of(organizer));

        LoginResponse response = authService.loginWithGoogle(ID_TOKEN);

        assertThat(response.newUser()).isFalse();
        assertThat(organizer.getIdpSubject()).isEqualTo("google-sub-123");
        assertThat(organizer.getRole().getName()).isEqualTo("ORGANIZER");
        verify(userRepository).save(organizer);
    }

    @Test
    void loginWithGoogle_unknownPerson_createsAClientAndIsNew() {
        Role client = role("CLIENT");
        when(googleIdTokenVerifier.verify(ID_TOKEN)).thenReturn(ANA);
        when(userRepository.findByIdpSubject("google-sub-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("ana@gmail.com")).thenReturn(Optional.empty());
        when(roleRepository.findByName("CLIENT")).thenReturn(Optional.of(client));
        when(userRepository.save(any())).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(8L);
            return saved;
        });
        when(userRepository.findWithRoleAndPermissionsById(8L)).thenAnswer(invocation -> Optional.of(savedUser()));

        LoginResponse response = authService.loginWithGoogle(ID_TOKEN);

        assertThat(response.newUser()).isTrue();
        User created = savedUser();
        assertThat(created.getEmail()).isEqualTo("ana@gmail.com");
        assertThat(created.getIdpSubject()).isEqualTo("google-sub-123");
        assertThat(created.getFirstName()).isEqualTo("Ana");
        assertThat(created.getLastName()).isEqualTo("Gómez");
        assertThat(created.getRole()).isSameAs(client);
    }

    @Test
    void loginWithGoogle_linkedUserWasDeleted_throwsUserDisabled() {
        when(googleIdTokenVerifier.verify(ID_TOKEN)).thenReturn(ANA);
        when(userRepository.findByIdpSubject("google-sub-123"))
                .thenReturn(Optional.of(user(7L, "ana@gmail.com", "google-sub-123", "CLIENT", deleted())));

        assertThatThrownBy(() -> authService.loginWithGoogle(ID_TOKEN))
                .isInstanceOf(TicketYaException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.USER_DISABLED);
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void loginWithGoogle_preloadedEmailWasDeleted_throwsUserDisabledWithoutLinking() {
        User deletedOrganizer = user(2L, "ana@gmail.com", null, "ORGANIZER", deleted());
        when(googleIdTokenVerifier.verify(ID_TOKEN)).thenReturn(ANA);
        when(userRepository.findByIdpSubject("google-sub-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("ana@gmail.com")).thenReturn(Optional.of(deletedOrganizer));

        assertThatThrownBy(() -> authService.loginWithGoogle(ID_TOKEN))
                .isInstanceOf(TicketYaException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.USER_DISABLED);
        assertThat(deletedOrganizer.getIdpSubject()).isNull();
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginWithGoogle_emailLinkedToAnotherGoogleAccount_throwsConflictWithoutRelinking() {
        User linkedElsewhere = user(2L, "ana@gmail.com", "another-google-sub", "ORGANIZER", null);
        when(googleIdTokenVerifier.verify(ID_TOKEN)).thenReturn(ANA);
        when(userRepository.findByIdpSubject("google-sub-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("ana@gmail.com")).thenReturn(Optional.of(linkedElsewhere));

        assertThatThrownBy(() -> authService.loginWithGoogle(ID_TOKEN))
                .isInstanceOf(TicketYaException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.DATA_CONFLICT);
        assertThat(linkedElsewhere.getIdpSubject()).isEqualTo("another-google-sub");
        verify(userRepository, never()).save(any());
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void loginWithGoogle_invalidGoogleToken_doesNotTouchTheUsers() {
        when(googleIdTokenVerifier.verify(ID_TOKEN))
                .thenThrow(new TicketYaException(AuthErrorCode.INVALID_CREDENTIALS, "Token inválido"));

        assertThatThrownBy(() -> authService.loginWithGoogle(ID_TOKEN))
                .isInstanceOf(TicketYaException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
        verifyNoInteractions(userRepository, roleRepository, jwtTokenProvider);
    }

    private User savedUser() {
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        return saved.getValue();
    }

    private static User user(Long id, String email, String idpSubject, String roleName, LocalDateTime deletedAt) {
        return User.builder().id(id).email(email).idpSubject(idpSubject).role(role(roleName))
                .deletedAt(deletedAt).build();
    }

    private static Role role(String name) {
        return Role.builder().name(name)
                .permissions(Set.of(Permission.builder().name("EVENT_SEARCH").build()))
                .build();
    }

    private static LocalDateTime deleted() {
        return LocalDateTime.of(2026, 1, 1, 0, 0);
    }
}

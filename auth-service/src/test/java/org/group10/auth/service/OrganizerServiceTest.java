package org.group10.auth.service;

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
import org.group10.common.exception.ErrorCode;
import org.group10.common.exception.TicketYaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrganizerServiceTest {

    private static final LocalDateTime DELETED = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Mock
    private OrganizerRepository organizerRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private OrganizerService organizerService;

    private final Role clientRole = Role.builder().name("CLIENT").build();
    private final Role organizerRole = Role.builder().name("ORGANIZER").build();
    private final Organization organization = Organization.builder().id(1L).publicName("Del Sur").build();

    @BeforeEach
    void setUp() {
        when(roleRepository.findByName("CLIENT")).thenReturn(Optional.of(clientRole));
        when(roleRepository.findByName("ORGANIZER")).thenReturn(Optional.of(organizerRole));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        when(organizerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ---- promote ----

    @Test
    void promote_client_becomesOrganizerOfTheOrganization() {
        User client = user(7L, clientRole, null);
        when(userRepository.findWithRoleAndPermissionsById(7L)).thenReturn(Optional.of(client));
        when(organizerRepository.findById(7L)).thenReturn(Optional.empty());

        OrganizerResponse response = organizerService.promote(1L, 7L);

        ArgumentCaptor<Organizer> saved = ArgumentCaptor.forClass(Organizer.class);
        verify(organizerRepository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(client);
        assertThat(saved.getValue().getOrganization()).isSameAs(organization);
        assertThat(client.getRole()).isSameAs(organizerRole);
        assertThat(response).isEqualTo(new OrganizerResponse(7L, "Ana", "Gómez", 1L));
    }

    @Test
    void promote_removedOrganizer_isReactivatedInTheNewOrganization() {
        User client = user(7L, clientRole, null);
        Organizer removed = Organizer.builder().userId(7L).user(client)
                .organization(Organization.builder().id(2L).build()).deletedAt(DELETED).build();
        when(userRepository.findWithRoleAndPermissionsById(7L)).thenReturn(Optional.of(client));
        when(organizerRepository.findById(7L)).thenReturn(Optional.of(removed));

        organizerService.promote(1L, 7L);

        assertThat(removed.getDeletedAt()).isNull();
        assertThat(removed.getOrganization()).isSameAs(organization);
        assertThat(client.getRole()).isSameAs(organizerRole);
    }

    @Test
    void promote_alreadyActiveOrganizer_isAConflict() {
        User organizerUser = user(2L, organizerRole, null);
        when(userRepository.findWithRoleAndPermissionsById(2L)).thenReturn(Optional.of(organizerUser));
        when(organizerRepository.findById(2L)).thenReturn(Optional.of(
                Organizer.builder().userId(2L).user(organizerUser).organization(organization).build()));

        assertError(() -> organizerService.promote(1L, 2L), CommonErrorCode.DATA_CONFLICT);
        verify(organizerRepository, never()).save(any());
    }

    @Test
    void promote_userWhoIsNotAClient_isAConflictAndKeepsItsRole() {
        Role adminRole = Role.builder().name("ADMIN").build();
        User admin = user(3L, adminRole, null);
        when(userRepository.findWithRoleAndPermissionsById(3L)).thenReturn(Optional.of(admin));
        when(organizerRepository.findById(3L)).thenReturn(Optional.empty());

        assertError(() -> organizerService.promote(1L, 3L), CommonErrorCode.DATA_CONFLICT);
        assertThat(admin.getRole()).isSameAs(adminRole);
        verify(organizerRepository, never()).save(any());
    }

    @Test
    void promote_missingOrDeletedOrganization_isNotFound() {
        when(organizationRepository.findById(9L)).thenReturn(Optional.empty());
        assertError(() -> organizerService.promote(9L, 7L), AuthErrorCode.ORGANIZATION_NOT_FOUND);

        when(organizationRepository.findById(5L)).thenReturn(Optional.of(
                Organization.builder().id(5L).deletedAt(DELETED).build()));
        assertError(() -> organizerService.promote(5L, 7L), AuthErrorCode.ORGANIZATION_NOT_FOUND);
    }

    @Test
    void promote_missingOrDeletedUser_isNotFound() {
        when(userRepository.findWithRoleAndPermissionsById(99L)).thenReturn(Optional.empty());
        assertError(() -> organizerService.promote(1L, 99L), AuthErrorCode.USER_NOT_FOUND);

        when(userRepository.findWithRoleAndPermissionsById(8L)).thenReturn(Optional.of(user(8L, clientRole, DELETED)));
        assertError(() -> organizerService.promote(1L, 8L), AuthErrorCode.USER_NOT_FOUND);
    }

    // ---- remove ----

    @Test
    void remove_organizerOfTheOrganization_isDeletedAndBecomesAClient() {
        User organizerUser = user(2L, organizerRole, null);
        Organizer organizer = Organizer.builder().userId(2L).user(organizerUser).organization(organization).build();
        when(organizerRepository.findWithUserAndOrganizationByUserId(2L)).thenReturn(Optional.of(organizer));

        organizerService.remove(1L, 2L);

        assertThat(organizer.getDeletedAt()).isNotNull();
        assertThat(organizerUser.getRole()).isSameAs(clientRole);
    }

    @Test
    void remove_organizerOfAnotherOrganization_isNotFound() {
        User organizerUser = user(5L, organizerRole, null);
        when(organizerRepository.findWithUserAndOrganizationByUserId(5L)).thenReturn(Optional.of(
                Organizer.builder().userId(5L).user(organizerUser).organization(Organization.builder().id(2L).build())
                        .build()));

        assertError(() -> organizerService.remove(1L, 5L), AuthErrorCode.ORGANIZER_NOT_FOUND);
        assertThat(organizerUser.getRole()).isSameAs(organizerRole);
    }

    @Test
    void remove_alreadyRemovedOrNotAnOrganizer_isNotFound() {
        when(organizerRepository.findWithUserAndOrganizationByUserId(7L)).thenReturn(Optional.empty());
        assertError(() -> organizerService.remove(1L, 7L), AuthErrorCode.ORGANIZER_NOT_FOUND);

        when(organizerRepository.findWithUserAndOrganizationByUserId(6L)).thenReturn(Optional.of(
                Organizer.builder().userId(6L).user(user(6L, clientRole, null)).organization(organization)
                        .deletedAt(DELETED).build()));
        assertError(() -> organizerService.remove(1L, 6L), AuthErrorCode.ORGANIZER_NOT_FOUND);
    }

    private static User user(Long id, Role role, LocalDateTime deletedAt) {
        return User.builder().id(id).email(id + "@ticketya.com").firstName("Ana").lastName("Gómez").role(role)
                .deletedAt(deletedAt).build();
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(TicketYaException.class).extracting("errorCode").isEqualTo(code);
    }
}

package org.group10.auth.seeder;

import org.group10.auth.entity.Permission;
import org.group10.auth.entity.Role;
import org.group10.auth.entity.AccessStaff;
import org.group10.auth.repository.AccessStaffRepository;
import org.group10.auth.repository.OrganizationRepository;
import org.group10.auth.repository.OrganizerRepository;
import org.group10.auth.repository.PermissionRepository;
import org.group10.auth.repository.RoleRepository;
import org.group10.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthSeederTest {

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizerRepository organizerRepository;

    @Mock
    private AccessStaffRepository accessStaffRepository;

    @InjectMocks
    private AuthSeeder seeder;

    private Map<String, Set<String>> permissionsByRole;
    private Set<String> seededPermissions;

    @BeforeEach
    void runSeeder() {
        when(roleRepository.count()).thenReturn(0L);
        when(permissionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(organizationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        seeder.run(new DefaultApplicationArguments());

        seededPermissions = this.<Permission>saved(permissionRepository).stream()
                .map(Permission::getName)
                .collect(Collectors.toSet());
        permissionsByRole = this.<Role>saved(roleRepository).stream()
                .collect(Collectors.toMap(Role::getName, role -> role.getPermissions().stream()
                        .map(Permission::getName)
                        .collect(Collectors.toSet())));
    }

    @Test
    void permissions_areExactlyTheOnesTheServicesCanRequire() {
        Set<String> requirable = Arrays.stream(org.group10.common.security.Permission.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertThat(seededPermissions).isEqualTo(requirable);
    }

    @Test
    void venues_areManagedOnlyByTheAdmin() {
        assertThat(rolesWith("VENUE_MANAGE")).containsExactly("ADMIN");
    }

    @Test
    void participants_areManagedByOrganizersAndTheAdmin() {
        assertThat(rolesWith("PARTICIPANT_MANAGE")).containsExactlyInAnyOrder("ORGANIZER", "ADMIN");
    }

    @Test
    void accessStaff_oneStaffForEachOrganization() {
        List<String> staffWithOrganization = this.<AccessStaff>saved(accessStaffRepository).stream()
                .map(staff -> staff.getUser().getEmail() + " -> " + staff.getOrganization().getPublicName())
                .toList();

        assertThat(staffWithOrganization).containsExactlyInAnyOrder(
                "staff@ticketya.com -> Del Sur Producciones",
                "staff.serrana@ticketya.com -> Cultura Serrana");
    }

    @Test
    void accessStaff_usersHaveTheAccessStaffRole() {
        List<String> roles = this.<AccessStaff>saved(accessStaffRepository).stream()
                .map(staff -> staff.getUser().getRole().getName())
                .distinct()
                .toList();

        assertThat(roles).containsExactly("ACCESS_STAFF");
    }

    private List<String> rolesWith(String permission) {
        return permissionsByRole.entrySet().stream()
                .filter(role -> role.getValue().contains(permission))
                .map(Map.Entry::getKey)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> saved(org.springframework.data.jpa.repository.JpaRepository<T, Long> repository) {
        ArgumentCaptor<List<T>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        return captor.getValue();
    }
}

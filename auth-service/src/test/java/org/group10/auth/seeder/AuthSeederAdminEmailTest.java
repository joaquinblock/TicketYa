package org.group10.auth.seeder;

import org.group10.auth.entity.User;
import org.group10.auth.repository.AccessStaffRepository;
import org.group10.auth.repository.OrganizationRepository;
import org.group10.auth.repository.OrganizerRepository;
import org.group10.auth.repository.PermissionRepository;
import org.group10.auth.repository.RoleRepository;
import org.group10.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El admin del seeder toma el email de {@code BOOTSTRAP_ADMIN_EMAIL}: así, quien entra con ese Gmail queda vinculado
 * al admin y no se crea como cliente.
 */
@ExtendWith(MockitoExtension.class)
class AuthSeederAdminEmailTest {

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

    @BeforeEach
    void setUp() {
        when(roleRepository.count()).thenReturn(0L);
        when(permissionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(organizationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"laura.fernandez@gmail.com", "  laura.fernandez@gmail.com  "})
    void run_withBootstrapAdminEmail_theAdminHasThatEmail(String configured) {
        ReflectionTestUtils.setField(seeder, "bootstrapAdminEmail", configured);

        seeder.run(new DefaultApplicationArguments());

        assertThat(emailOfTheAdmin()).isEqualTo("laura.fernandez@gmail.com");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void run_withoutBootstrapAdminEmail_theAdminKeepsTheTestEmail(String configured) {
        ReflectionTestUtils.setField(seeder, "bootstrapAdminEmail", configured);

        seeder.run(new DefaultApplicationArguments());

        assertThat(emailOfTheAdmin()).isEqualTo("admin@ticketya.com");
    }

    @SuppressWarnings("unchecked")
    private String emailOfTheAdmin() {
        ArgumentCaptor<List<User>> users = ArgumentCaptor.forClass(List.class);
        verify(userRepository).saveAll(users.capture());
        return users.getValue().stream()
                .filter(user -> "ADMIN".equals(user.getRole().getName()))
                .map(User::getEmail)
                .findFirst()
                .orElseThrow();
    }
}

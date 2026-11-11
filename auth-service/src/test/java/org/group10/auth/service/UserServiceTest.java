package org.group10.auth.service;

import org.group10.auth.dto.UserPermissionsResponse;
import org.group10.auth.entity.Permission;
import org.group10.auth.entity.Role;
import org.group10.auth.entity.User;
import org.group10.auth.exception.AuthErrorCode;
import org.group10.auth.repository.UserRepository;
import org.group10.common.exception.TicketYaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void findPermissions_activeUser_returnsItsRoleAndPermissionsSorted() {
        when(userRepository.findWithRoleAndPermissionsById(2L)).thenReturn(Optional.of(organizer(null)));

        UserPermissionsResponse response = userService.findPermissions(2L);

        assertThat(response.userId()).isEqualTo(2L);
        assertThat(response.role()).isEqualTo("ORGANIZER");
        assertThat(response.permissions()).containsExactly("EVENT_MANAGE", "EVENT_SEARCH", "REPORT_VIEW");
    }

    @Test
    void findPermissions_missingUser_throwsUserNotFound() {
        when(userRepository.findWithRoleAndPermissionsById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findPermissions(99L))
                .isInstanceOf(TicketYaException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.USER_NOT_FOUND);
    }

    @Test
    void findPermissions_deletedUser_throwsUserNotFound() {
        when(userRepository.findWithRoleAndPermissionsById(2L))
                .thenReturn(Optional.of(organizer(LocalDateTime.of(2026, 1, 1, 0, 0))));

        assertThatThrownBy(() -> userService.findPermissions(2L))
                .isInstanceOf(TicketYaException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.USER_NOT_FOUND);
    }

    private static User organizer(LocalDateTime deletedAt) {
        Role role = Role.builder()
                .name("ORGANIZER")
                .permissions(Set.of(permission("REPORT_VIEW"), permission("EVENT_SEARCH"), permission("EVENT_MANAGE")))
                .build();
        return User.builder().id(2L).email("organizer@ticketya.com").role(role).deletedAt(deletedAt).build();
    }

    private static Permission permission(String name) {
        return Permission.builder().name(name).build();
    }
}

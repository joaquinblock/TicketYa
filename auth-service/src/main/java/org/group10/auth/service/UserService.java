package org.group10.auth.service;

import lombok.RequiredArgsConstructor;
import org.group10.auth.dto.UserPermissionsResponse;
import org.group10.auth.entity.Permission;
import org.group10.auth.entity.Role;
import org.group10.auth.entity.User;
import org.group10.auth.exception.AuthErrorCode;
import org.group10.auth.repository.UserRepository;
import org.group10.common.exception.TicketYaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    /**
     * Returns what the user is allowed to do, taken from the permissions of its role. A deleted user is treated
     * as not existing, so it can't act on any service.
     *
     * @throws TicketYaException {@code USER_NOT_FOUND}
     */
    public UserPermissionsResponse findPermissions(Long userId) {
        User user = userRepository.findWithRoleAndPermissionsById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new TicketYaException(AuthErrorCode.USER_NOT_FOUND,
                        "No se encontró el usuario con id %d".formatted(userId)));
        Role role = user.getRole();
        SortedSet<String> permissions = role.getPermissions().stream()
                .map(Permission::getName)
                .collect(Collectors.toCollection(TreeSet::new));
        return new UserPermissionsResponse(user.getId(), role.getName(), permissions);
    }
}

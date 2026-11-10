package org.group10.event.security;

import java.util.Set;

/**
 * Who is calling an endpoint with {@link RequiresPermission}, as the auth-service answered. {@link PermissionInterceptor}
 * leaves it in the request, so a service can tell an admin from an organizer without asking the auth-service again.
 * With JWT (Epic 9) it comes from the token instead.
 */
public record Caller(Long userId, Set<String> permissions) {

    public boolean has(Permission permission) {
        return permissions.contains(permission.name());
    }
}

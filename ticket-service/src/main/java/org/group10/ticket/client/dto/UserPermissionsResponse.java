package org.group10.ticket.client.dto;

import java.util.Set;

public record UserPermissionsResponse(Long userId, String role, Set<String> permissions) {

    public boolean has(String permission) {
        return permissions != null && permissions.contains(permission);
    }
}

package org.group10.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * El contacto que devuelve {@code GET /internal/users/{id}} del auth-service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserContact(Long userId, String email, String firstName, String lastName) {
}

package org.group10.auth.security;

import java.util.List;

/**
 * Lo que el JWT de TicketYa dice del usuario. Se arma desde {@code User} y {@code Organizer}, así el
 * provider no depende de JPA.
 *
 * @param organizationId organización del organizador o del personal de acceso; {@code null} para el resto
 */
public record TokenSubject(Long userId, String role, List<String> permissions, Long organizationId) {
}

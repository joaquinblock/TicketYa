package org.group10.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * El usuario que llama, tal como lo pasa el api-gateway en sus headers. Un controller lo recibe como parámetro y se
 * lo pasa al service, que así distingue a un admin de un organizador sin consultar al auth-service.
 *
 * @param organizationId organización del organizador; {@code null} para el resto
 */
public record Caller(Long userId, String role, Set<String> permissions, Long organizationId) {

    public boolean has(Permission permission) {
        return permissions.contains(permission.name());
    }

    public boolean hasAny(Permission... required) {
        return Arrays.stream(required).anyMatch(this::has);
    }

    /**
     * @throws TicketYaException {@code UNAUTHORIZED} si falta {@code X-User-Id} o algún id no es un número: el request
     *                           no pasó por el gateway con un token válido
     */
    static Caller fromGatewayHeaders(HttpServletRequest request) {
        Long userId = idHeader(request, GatewayHeaders.USER_ID);
        if (userId == null) {
            throw unauthorized();
        }
        return new Caller(userId, request.getHeader(GatewayHeaders.ROLE), permissions(request),
                idHeader(request, GatewayHeaders.ORGANIZATION_ID));
    }

    private static Set<String> permissions(HttpServletRequest request) {
        String header = request.getHeader(GatewayHeaders.PERMISSIONS);
        if (header == null) {
            return Set.of();
        }
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Long idHeader(HttpServletRequest request, String name) {
        String header = request.getHeader(name);
        if (header == null) {
            return null;
        }
        try {
            return Long.valueOf(header.trim());
        } catch (NumberFormatException e) {
            throw unauthorized();
        }
    }

    private static TicketYaException unauthorized() {
        return new TicketYaException(CommonErrorCode.UNAUTHORIZED, "Se necesita un token válido para esta operación");
    }
}

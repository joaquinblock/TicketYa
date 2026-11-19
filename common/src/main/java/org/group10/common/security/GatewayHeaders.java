package org.group10.common.security;

/**
 * Headers con los que el api-gateway pasa a cada servicio el usuario del JWT ya validado (ADR-003). El gateway
 * borra los que mande el cliente, así que un servicio puede confiar en ellos.
 */
public final class GatewayHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String ROLE = "X-User-Role";
    /** Nombres de los permisos separados por coma, como {@code EVENT_SEARCH,EVENT_MANAGE}. */
    public static final String PERMISSIONS = "X-User-Permissions";
    /** Solo si el usuario pertenece a una organización. */
    public static final String ORGANIZATION_ID = "X-Organization-Id";

    private GatewayHeaders() {
    }
}

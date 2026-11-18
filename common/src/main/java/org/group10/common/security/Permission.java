package org.group10.common.security;

/**
 * Permisos de TicketYa. Cada nombre tiene que coincidir con un {@code permission.name} del {@code AuthSeeder}:
 * llegan por nombre en el header {@code X-User-Permissions}, que el gateway arma con el claim del JWT.
 */
public enum Permission {
    /** Buscar eventos (todos los roles). */
    EVENT_SEARCH,
    /** Comprar entradas (todos los roles). */
    TICKET_PURCHASE,
    /** Pedir la devolución de una entrada (todos los roles). */
    REFUND_REQUEST,
    /** Crear, editar y publicar eventos de la propia organización (rol ORGANIZER). */
    EVENT_MANAGE,
    /** Ver los reportes de los eventos de la propia organización (rol ORGANIZER). */
    REPORT_VIEW,
    /** Suspender o cancelar cualquier evento (rol ADMIN). */
    EVENT_ADMIN,
    /** Resolver las devoluciones pedidas (rol ADMIN). */
    REFUND_RESOLVE,
    /** Administrar usuarios y roles (rol ADMIN). */
    USER_MANAGE,
    /** Validar el QR en el ingreso (rol ACCESS_STAFF). */
    TICKET_VALIDATE
}

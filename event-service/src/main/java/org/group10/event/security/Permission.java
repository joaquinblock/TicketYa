package org.group10.event.security;

/**
 * Permisos del auth-service que exige este servicio. Cada nombre tiene que coincidir con un {@code permission.name}
 * de {@code AuthSeeder}: el interceptor compara por nombre.
 */
public enum Permission {
    /** Crear, editar y publicar eventos de la propia organización (rol ORGANIZER). */
    EVENT_MANAGE,
    /** Suspender o cancelar cualquier evento (rol ADMIN). */
    EVENT_ADMIN
}

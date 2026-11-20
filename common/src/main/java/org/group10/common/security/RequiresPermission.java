package org.group10.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exige que el usuario tenga <b>alguno</b> de estos permisos para entrar al endpoint. Lo verifica
 * {@link PermissionInterceptor} antes de leer el body, así un usuario sin permiso recibe 403 aunque el body sea
 * inválido. Un endpoint sin esta anotación es público.
 * <pre>{@code
 * @PostMapping
 * @RequiresPermission(Permission.EVENT_MANAGE)
 * public EventResponse create(@Valid @RequestBody CreateEventRequest request, Caller caller)
 *
 * @PostMapping("/{id}/suspend")
 * @RequiresPermission({Permission.EVENT_ADMIN, Permission.EVENT_MANAGE})   // cualquiera de los dos
 * public EventDetailResponse suspend(@PathVariable Long id, Caller caller)
 * }</pre>
 * Solo dice qué puede hacer el usuario en general. Que el recurso sea suyo (por ejemplo, que el evento sea de su
 * organización) lo sigue validando el service, con el {@link Caller}.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /** Alcanza con tener uno. */
    Permission[] value();
}

package org.group10.event.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exige que el usuario del header {@code X-User-Id} tenga <b>alguno</b> de estos permisos para entrar al endpoint. Lo
 * verifica {@link PermissionInterceptor} antes de llamar al controller.
 * <pre>{@code
 * @PostMapping
 * @RequiresPermission(Permission.EVENT_MANAGE)
 * public EventResponse create(...)
 *
 * @PostMapping("/{id}/suspend")
 * @RequiresPermission({Permission.EVENT_ADMIN, Permission.EVENT_MANAGE})   // cualquiera de los dos
 * public EventDetailResponse suspend(...)
 * }</pre>
 * Solo dice qué puede hacer el usuario en general. Que el recurso sea suyo (p. ej. que el evento sea de su
 * organización, o que lo haya creado él) lo sigue validando el service, con el {@link Caller} que deja el interceptor.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /** Alcanza con tener uno. */
    Permission[] value();
}

package org.group10.event.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exige que el usuario del header {@code X-User-Id} tenga este permiso para entrar al endpoint. Lo verifica
 * {@link PermissionInterceptor} antes de llamar al controller.
 * <pre>{@code
 * @PostMapping
 * @RequiresPermission(Permission.EVENT_MANAGE)
 * public EventResponse create(...)
 * }</pre>
 * Solo dice qué puede hacer el usuario en general. Que el recurso sea suyo (p. ej. que el evento sea de su
 * organización) lo sigue validando el service.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    Permission value();
}

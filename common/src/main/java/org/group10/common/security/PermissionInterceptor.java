package org.group10.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Chequea {@link RequiresPermission} en {@code preHandle}: después de elegir el método del controller y antes de
 * leer el body. Por eso un usuario sin permiso recibe 403, y uno sin identidad 401, aunque el body sea inválido.
 * <p>
 * Los permisos salen de los headers del api-gateway, que es el único que valida el JWT (ADR-003). Deja el
 * {@link Caller} en el request para el {@link CallerArgumentResolver}.
 */
public class PermissionInterceptor implements HandlerInterceptor {

    /** Atributo del request con el {@link Caller}. */
    public static final String CALLER_ATTRIBUTE = "ticketya.caller";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequiresPermission required = method.getMethodAnnotation(RequiresPermission.class);
        if (required == null) {
            return true;
        }
        Caller caller = Caller.fromGatewayHeaders(request);
        if (!caller.hasAny(required.value())) {
            throw new TicketYaException(CommonErrorCode.FORBIDDEN, missingPermissionMessage(caller, required.value()));
        }
        request.setAttribute(CALLER_ATTRIBUTE, caller);
        return true;
    }

    private static String missingPermissionMessage(Caller caller, Permission[] required) {
        return required.length == 1
                ? "El usuario %d no tiene el permiso %s".formatted(caller.userId(), required[0])
                : "El usuario %d no tiene ninguno de los permisos %s".formatted(caller.userId(),
                Arrays.stream(required).map(Enum::name).collect(Collectors.joining(", ")));
    }
}

package org.group10.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Optional;

/**
 * Permite recibir el {@link Caller} como parámetro de un método del controller:
 * <ul>
 *   <li>{@code Caller caller}: el endpoint necesita un usuario. Sin {@code X-User-Id}, 401.</li>
 *   <li>{@code Optional<Caller> caller}: el endpoint es público, pero responde distinto si sabe quién llama (por
 *       ejemplo, un borrador solo lo ve su organizador). Sin {@code X-User-Id}, vacío.</li>
 * </ul>
 * Usa el {@link Caller} que dejó el {@link PermissionInterceptor}; si no hay, lo arma con los headers del gateway.
 * Un {@code X-User-Id} que no es un número siempre da 401.
 */
public class CallerArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return isCaller(parameter);
    }

    /**
     * Si el parámetro es un {@code Caller} o un {@code Optional<Caller>}: lo arma el servidor, no lo manda el cliente.
     */
    public static boolean isCaller(MethodParameter parameter) {
        return Caller.class.equals(parameter.getParameterType()) || isOptionalCaller(parameter);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (!isOptionalCaller(parameter)) {
            return callerOf(request);
        }
        if (request.getAttribute(PermissionInterceptor.CALLER_ATTRIBUTE) == null
                && request.getHeader(GatewayHeaders.USER_ID) == null) {
            return Optional.empty();
        }
        return Optional.of(callerOf(request));
    }

    private static Caller callerOf(HttpServletRequest request) {
        if (request.getAttribute(PermissionInterceptor.CALLER_ATTRIBUTE) instanceof Caller caller) {
            return caller;
        }
        return Caller.fromGatewayHeaders(request);
    }

    private static boolean isOptionalCaller(MethodParameter parameter) {
        return Optional.class.equals(parameter.getParameterType())
                && Caller.class.equals(parameter.nested().getNestedParameterType());
    }
}

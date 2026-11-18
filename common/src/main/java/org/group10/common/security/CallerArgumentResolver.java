package org.group10.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Permite recibir el {@link Caller} como parámetro de un método del controller. Usa el que dejó el
 * {@link PermissionInterceptor}; en un endpoint sin {@link RequiresPermission} lo arma con los headers, y si no hay
 * usuario responde 401.
 */
public class CallerArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Caller.class.equals(parameter.getParameterType());
    }

    @Override
    public Caller resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request.getAttribute(PermissionInterceptor.CALLER_ATTRIBUTE) instanceof Caller caller) {
            return caller;
        }
        return Caller.fromGatewayHeaders(request);
    }
}

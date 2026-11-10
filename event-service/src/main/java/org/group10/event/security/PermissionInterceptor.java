package org.group10.event.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.group10.event.client.AuthClient;
import org.group10.event.client.dto.UserPermissionsResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Checks {@link RequiresPermission} before the controller runs, so it also runs before {@code @Valid}: a user
 * without the permission gets 403 even if the body is invalid.
 * <p>
 * The exceptions it throws go through the common {@code GlobalExceptionHandler} like any other: a 404
 * {@code user_not_found} from the auth-service is forwarded as is, and an auth-service that doesn't answer gives 503.
 * With JWT (Epic 9) this class goes away and the annotation becomes {@code @PreAuthorize}.
 */
@Component
@RequiredArgsConstructor
public class PermissionInterceptor implements HandlerInterceptor {

    public static final String USER_HEADER = "X-User-Id";

    private final AuthClient authClient;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequiresPermission required = method.getMethodAnnotation(RequiresPermission.class);
        if (required == null) {
            return true;
        }

        Long userId = readUserId(request);
        UserPermissionsResponse user = authClient.getPermissions(userId);
        if (!user.permissions().contains(required.value().name())) {
            throw new TicketYaException(CommonErrorCode.FORBIDDEN,
                    "El usuario %d no tiene el permiso %s".formatted(userId, required.value()));
        }
        return true;
    }

    /**
     * Same messages as the {@code GlobalExceptionHandler} gives when a {@code @RequestHeader} is missing or has the
     * wrong type, so the answer doesn't depend on who catches it first.
     */
    private static Long readUserId(HttpServletRequest request) {
        String header = request.getHeader(USER_HEADER);
        if (header == null || header.isBlank()) {
            throw new TicketYaException(CommonErrorCode.INVALID_PARAMETER,
                    "Falta el header obligatorio '%s'".formatted(USER_HEADER));
        }
        try {
            return Long.valueOf(header.trim());
        } catch (NumberFormatException e) {
            throw new TicketYaException(CommonErrorCode.INVALID_PARAMETER,
                    "Valor inválido '%s' para el parámetro '%s'".formatted(header, USER_HEADER));
        }
    }
}

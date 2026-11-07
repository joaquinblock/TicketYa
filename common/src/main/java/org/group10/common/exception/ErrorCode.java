package org.group10.common.exception;

import org.group10.common.dto.ErrorDto;
import org.springframework.http.HttpStatus;

import java.util.Locale;
import java.util.Map;

/**
 * Código de error con su status HTTP. Lo implementa {@link CommonErrorCode} con los errores generales
 * y un enum propio en cada microservicio con los de su negocio (p. ej. {@code EventErrorCode}).
 */
public interface ErrorCode {

    HttpStatus getStatus();

    String name();

    default String getCode() {
        return name().toLowerCase(Locale.ROOT);
    }

    default ErrorDto toErrorDto(String message) {
        return toErrorDto(message, null);
    }

    default ErrorDto toErrorDto(String message, Map<String, Object> detail) {
        return ErrorDto.builder()
                .error(getCode())
                .message(message)
                .status(getStatus().value())
                .detail(detail)
                .build();
    }
}

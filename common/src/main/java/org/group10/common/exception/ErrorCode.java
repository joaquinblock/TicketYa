package org.group10.common.exception;

import org.group10.common.dto.ErrorDto;
import org.slf4j.event.Level;
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

    /**
     * Con qué nivel se registra cuando se responde: {@code ERROR} si falla algo de nuestro lado (5xx), para que lo
     * crítico se encuentre en los logs de todo el sistema; {@code WARN} si es un error del cliente (4xx), que es una
     * respuesta esperada. Un código que sea crítico aunque responda 4xx lo sobrescribe.
     */
    default Level getLevel() {
        return getStatus().is5xxServerError() ? Level.ERROR : Level.WARN;
    }

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

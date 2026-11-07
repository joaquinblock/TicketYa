package org.group10.common.exception;

import lombok.Getter;

import java.util.Map;

/**
 * Excepción de negocio de TicketYa. El {@link GlobalExceptionHandler} la convierte en un
 * {@link org.group10.common.dto.ErrorDto} con el status de su {@link ErrorCode}.
 * <pre>{@code
 * throw new TicketYaException(EventErrorCode.EVENT_NOT_FOUND, "No se encontró el evento con id %d".formatted(id));
 * }</pre>
 */
@Getter
public class TicketYaException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> detail;

    public TicketYaException(ErrorCode errorCode, String message) {
        this(errorCode, message, null, null);
    }

    public TicketYaException(ErrorCode errorCode, String message, Map<String, Object> detail) {
        this(errorCode, message, detail, null);
    }

    public TicketYaException(ErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, message, null, cause);
    }

    private TicketYaException(ErrorCode errorCode, String message, Map<String, Object> detail, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.detail = detail;
    }
}

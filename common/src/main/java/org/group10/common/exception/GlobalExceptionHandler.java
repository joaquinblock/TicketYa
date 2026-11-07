package org.group10.common.exception;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.group10.common.dto.ErrorDto;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.ValueInstantiationException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Atrapa las excepciones que salen de cualquier controller y las convierte en un {@link ErrorDto}
 * con el status HTTP que corresponde. Así los controllers no necesitan ningún try/catch.
 * <p>
 * Cada microservicio lo recibe al depender de {@code common}, vía
 * {@link org.group10.common.config.ErrorHandlingAutoConfiguration}.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Red de seguridad para cualquier cosa que no atrape otro handler: errores inesperados
     * o fallas de infraestructura (base de datos caída, etc.).
     *
     * @return 500 con "Error interno del servidor"
     */
    @ExceptionHandler(Exception.class)
    @ApiResponse(responseCode = "500", description = "Error interno del servidor",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<ErrorDto> handleDefaultException(Exception ex) {
        log.error(ex.getMessage(), ex);
        return response(CommonErrorCode.INTERNAL_ERROR, "Error interno del servidor");
    }

    /**
     * Excepciones de negocio (evento no encontrado, asiento no disponible, etc.).
     * Se loguea como warn y sin stack trace porque es una respuesta esperada, no una falla.
     *
     * @return el status del {@link ErrorCode} que trae la excepción, con su mensaje y su detail
     */
    @ExceptionHandler(TicketYaException.class)
    public ResponseEntity<ErrorDto> handleTicketYaException(TicketYaException ex) {
        log.warn(ex.getMessage());
        ErrorCode code = ex.getErrorCode();
        return ResponseEntity.status(code.getStatus()).body(code.toErrorDto(ex.getMessage(), ex.getDetail()));
    }

    /**
     * Falla alguna anotación de validación (@NotBlank, @Positive, etc.) en un body recibido con @Valid.
     *
     * @return 400 con cada campo inválido y su mensaje en el detail
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ApiResponse(responseCode = "400", description = "Request inválido",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<ErrorDto> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> detail = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(err -> detail.put(err.getField(), err.getDefaultMessage()));
        return response(CommonErrorCode.VALIDATION_ERROR, "Campos inválidos", detail);
    }

    /**
     * Falla una validación sobre un @PathVariable, @RequestParam o @RequestHeader
     * (p. ej. {@code @Positive Long id}).
     *
     * @return 400 con cada parámetro inválido y su mensaje en el detail
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorDto> handleMethodValidation(HandlerMethodValidationException ex) {
        Map<String, Object> detail = new HashMap<>();
        ex.getParameterValidationResults().forEach(result -> detail.put(
                result.getMethodParameter().getParameterName(),
                result.getResolvableErrors().getFirst().getDefaultMessage()));
        return response(CommonErrorCode.VALIDATION_ERROR, "Parámetros inválidos", detail);
    }

    /**
     * Falla una validación fuera del controller: en un service con @Validated o en una entidad al persistirla.
     *
     * @return 400 con cada propiedad inválida y su mensaje en el detail
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorDto> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, Object> detail = new HashMap<>();
        ex.getConstraintViolations()
                .forEach(v -> detail.put(v.getPropertyPath().toString(), v.getMessage()));
        return response(CommonErrorCode.VALIDATION_ERROR, "Datos inválidos", detail);
    }

    /**
     * Un path variable o query param no se puede convertir al tipo esperado,
     * por ejemplo /events/abc cuando se espera un número, o ?type=XYZ con un valor que no está en el enum.
     *
     * @return 400 con el nombre del parámetro y el valor que mandaron
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorDto> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = String.format("Valor inválido '%s' para el parámetro '%s'", ex.getValue(), ex.getName());
        return response(CommonErrorCode.INVALID_PARAMETER, message);
    }

    /**
     * Falta un @RequestParam obligatorio en la URL.
     *
     * @return 400 con el nombre del parámetro que falta
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorDto> handleMissingParameter(MissingServletRequestParameterException ex) {
        String message = String.format("Falta el parámetro obligatorio '%s'", ex.getParameterName());
        return response(CommonErrorCode.INVALID_PARAMETER, message);
    }

    /**
     * Falta un header obligatorio. En la Entrega 1 el usuario se identifica por header, así que
     * es el caso de un request sin él.
     *
     * @return 400 con el nombre del header que falta
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorDto> handleMissingHeader(MissingRequestHeaderException ex) {
        String message = String.format("Falta el header obligatorio '%s'", ex.getHeaderName());
        return response(CommonErrorCode.INVALID_PARAMETER, message);
    }

    /**
     * Jackson no pudo convertir el JSON del body en el RequestDTO. Hay tres casos:
     * <ul>
     *     <li>El constructor del DTO rechazó un dato: se devuelve su mensaje tal cual.</li>
     *     <li>Un campo tiene un valor del tipo equivocado (ej: texto en un número o un enum que no existe):
     *     se indica qué campo falló y, si es un enum, cuáles son los valores permitidos.</li>
     *     <li>Cualquier otra cosa (JSON mal formado, body vacío): mensaje genérico, y queda en el log
     *     por si en realidad es un bug.</li>
     * </ul>
     *
     * @return 400 con el mensaje que corresponde a cada caso
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorDto> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof ValueInstantiationException
                && ex.getMostSpecificCause() instanceof IllegalArgumentException iae) {
            return response(CommonErrorCode.VALIDATION_ERROR, iae.getMessage());
        }

        if (ex.getCause() instanceof InvalidFormatException ife) {
            String field = extractFieldName(ife);
            String message = String.format("Valor inválido '%s' para el campo '%s'", ife.getValue(), field);

            Map<String, Object> detail = new HashMap<>();
            detail.put("field", field);
            detail.put("rejectedValue", ife.getValue());
            if (ife.getTargetType().isEnum()) {
                detail.put("allowedValues", ife.getTargetType().getEnumConstants());
            }
            return response(CommonErrorCode.VALIDATION_ERROR, message, detail);
        }

        log.warn(ex.getMessage(), ex);
        return response(CommonErrorCode.VALIDATION_ERROR, "El cuerpo del request es inválido");
    }

    /**
     * Se pidió una URL que no corresponde a ningún endpoint. Sin este handler terminaría como 500.
     *
     * @return 404 con la URL que se pidió
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorDto> handleNoResource(NoResourceFoundException ex) {
        String message = String.format("No existe el recurso '/%s'", ex.getResourcePath());
        return response(CommonErrorCode.RESOURCE_NOT_FOUND, message);
    }

    /**
     * La URL existe pero no acepta ese método HTTP (ej: un POST donde solo hay GET).
     *
     * @return 405 con el método que se usó
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorDto> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        String message = String.format("El método %s no está permitido en este recurso", ex.getMethod());
        return response(CommonErrorCode.METHOD_NOT_ALLOWED, message);
    }

    /**
     * Se pidió ordenar por un campo que la entidad no tiene, por ejemplo /events?sort=invalid.
     *
     * @return 400 con el campo que no existe
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorDto> handleInvalidSort(PropertyReferenceException ex) {
        String message = String.format("No existe el campo '%s' para ordenar", ex.getPropertyName());
        return response(CommonErrorCode.INVALID_PARAMETER, message);
    }

    /**
     * La base rechazó el dato por una restricción (unique, FK, check). Lo normal es validarlo antes en el
     * service con un error de negocio; esto cubre la carrera entre dos requests que pasan esa validación a la vez.
     *
     * @return 409
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorDto> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn(ex.getMostSpecificCause().getMessage());
        return response(CommonErrorCode.DATA_CONFLICT, "El dato entra en conflicto con uno existente");
    }

    /**
     * Otra operación modificó el registro al mismo tiempo (bloqueo optimista con @Version,
     * p. ej. dos compras del mismo asiento).
     *
     * @return 409
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorDto> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn(ex.getMessage());
        return response(CommonErrorCode.DATA_CONFLICT, "El recurso fue modificado por otra operación, volvé a intentarlo");
    }

    /**
     * Otro microservicio respondió con error. Como todos usan el mismo {@link ErrorDto}, si es un 4xx
     * se reenvía tal cual (p. ej. el event_not_found de event-service le llega así al cliente de ticket-service).
     * Si es un 5xx o el body no es un ErrorDto, se responde 503.
     *
     * @return el mismo status y error del otro servicio, o 503
     */
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<ErrorDto> handleDownstreamError(RestClientResponseException ex) {
        ErrorDto downstream = readErrorDto(ex);
        if (ex.getStatusCode().is4xxClientError() && downstream != null) {
            log.warn("Error de otro servicio: {}", downstream.message());
            return ResponseEntity.status(ex.getStatusCode()).body(downstream);
        }
        log.error("Falla de otro servicio: {} {}", ex.getStatusCode(), ex.getResponseBodyAsString());
        return response(CommonErrorCode.SERVICE_UNAVAILABLE, "Un servicio interno no está disponible");
    }

    /**
     * Otro microservicio no respondió: está caído o tardó más que el timeout.
     *
     * @return 503
     */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ErrorDto> handleDownstreamUnavailable(ResourceAccessException ex) {
        log.error("No se pudo conectar con otro servicio: {}", ex.getMessage());
        return response(CommonErrorCode.SERVICE_UNAVAILABLE, "Un servicio interno no está disponible");
    }

    private ResponseEntity<ErrorDto> response(ErrorCode code, String message) {
        return response(code, message, null);
    }

    private ResponseEntity<ErrorDto> response(ErrorCode code, String message, Map<String, Object> detail) {
        return ResponseEntity.status(code.getStatus()).body(code.toErrorDto(message, detail));
    }

    private ErrorDto readErrorDto(RestClientResponseException ex) {
        try {
            return ex.getResponseBodyAs(ErrorDto.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Saca el nombre del campo que falló.
     *
     * @param ife el error de Jackson, que trae la ruta del campo dentro del JSON
     * @return el último elemento de la ruta, o "unknown" si viene vacía
     */
    private String extractFieldName(InvalidFormatException ife) {
        List<JacksonException.Reference> path = ife.getPath();
        if (path.isEmpty()) {
            return "unknown";
        }
        return path.getLast().getPropertyName();
    }
}

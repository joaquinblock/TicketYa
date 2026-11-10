package org.group10.event.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.group10.common.dto.ErrorDto;
import org.group10.event.dto.EventCreateRequest;
import org.group10.event.dto.EventDetailResponse;
import org.group10.event.dto.EventParticipantsRequest;
import org.group10.event.dto.EventResponse;
import org.group10.event.dto.EventResumeRequest;
import org.group10.event.dto.EventSectorsRequest;
import org.group10.event.dto.ParticipantResponse;
import org.group10.event.security.Caller;
import org.group10.event.security.Permission;
import org.group10.event.security.PermissionInterceptor;
import org.group10.event.security.RequiresPermission;
import org.group10.event.service.EventService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * En la Entrega 1 no hay JWT: quien llama se identifica con los headers {@value #USER_HEADER} y
 * {@value #ORGANIZATION_HEADER}. {@code PermissionInterceptor} revisa los permisos de {@value #USER_HEADER} y el
 * service valida la organización y el organizador contra el auth-service.
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Eventos", description = "Alta y gestión de eventos")
public class EventController {

    static final String USER_HEADER = PermissionInterceptor.USER_HEADER;
    static final String ORGANIZATION_HEADER = "X-Organization-Id";

    private final EventService eventService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.EVENT_MANAGE)
    @Operation(summary = "Crear un evento", description = """
            Se crea en `DRAFT`, sin sectores ni participantes. El usuario de `X-User-Id` necesita el permiso \
            `EVENT_MANAGE` y tiene que ser un organizador activo de la organización de `X-Organization-Id`. \
            El permiso se revisa antes que el body: sin permiso la respuesta es 403 aunque el body sea inválido.""")
    @ApiResponse(responseCode = "201", description = "Evento creado en borrador")
    @ApiResponse(responseCode = "400", description = "validation_error / invalid_parameter / invalid_event_configuration",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = "forbidden (sin EVENT_MANAGE) / organizer_not_in_organization",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404",
            description = "user_not_found / venue_not_found / organization_not_found / organizer_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "503", description = "service_unavailable: el auth-service no responde",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public EventResponse create(
            @Parameter(description = "Organización dueña del evento", example = "1")
            @RequestHeader(ORGANIZATION_HEADER) Long organizationId,
            @Parameter(description = "Organizador que lo crea (id de usuario)", example = "2")
            @RequestHeader(USER_HEADER) Long organizerId,
            @Valid @RequestBody EventCreateRequest request) {
        return eventService.create(organizationId, organizerId, request);
    }

    @PutMapping("/{id}/participants")
    @RequiresPermission(Permission.EVENT_MANAGE)
    @Operation(summary = "Definir los participantes de un evento", description = """
            Reemplaza la lista completa: los ids repetidos cuentan una vez y una lista vacía los quita a todos. \
            Solo en `DRAFT`, y solo un organizador de la organización del evento.""")
    @ApiResponse(responseCode = "200", description = "Participantes del evento, ordenados por nombre")
    @ApiResponse(responseCode = "400", description = "validation_error / invalid_parameter",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = "forbidden (sin EVENT_MANAGE) / organizer_not_in_organization",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "event_not_found / participant_not_found (con missingIds en "
            + "detail) / user_not_found / organizer_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "event_not_editable: el evento no está en DRAFT",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "503", description = "service_unavailable: el auth-service no responde",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public List<ParticipantResponse> replaceParticipants(
            @PathVariable Long id,
            @Parameter(description = "Organizador que hace el cambio (id de usuario)", example = "2")
            @RequestHeader(USER_HEADER) Long organizerId,
            @Valid @RequestBody EventParticipantsRequest request) {
        return eventService.replaceParticipants(id, organizerId, request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ver un evento", description = "Público. Trae el lugar, los sectores que vende con su precio y "
            + "capacidad, los participantes y la capacidad total.")
    @ApiResponse(responseCode = "200", description = "Evento encontrado")
    @ApiResponse(responseCode = "404", description = "event_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public EventDetailResponse findById(@PathVariable Long id) {
        return eventService.findById(id);
    }

    @PutMapping("/{id}")
    @RequiresPermission(Permission.EVENT_MANAGE)
    @Operation(summary = "Editar un evento", description = """
            Reemplaza los datos del alta (mismo body que `POST /api/events`). Solo en `DRAFT`, y solo un organizador             de la organización del evento. Si cambia el lugar, se quitan los sectores que vendía, porque eran del             lugar anterior. La organización, el estado, los sectores y los participantes no se cambian acá.""")
    @ApiResponse(responseCode = "200", description = "Evento modificado")
    @ApiResponse(responseCode = "400", description = "validation_error / invalid_parameter / invalid_event_configuration",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = "forbidden (sin EVENT_MANAGE) / organizer_not_in_organization",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "event_not_found / venue_not_found / user_not_found / organizer_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "event_not_editable: el evento no está en DRAFT",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public EventDetailResponse update(
            @PathVariable Long id,
            @Parameter(description = "Organizador que hace el cambio (id de usuario)", example = "2")
            @RequestHeader(USER_HEADER) Long organizerId,
            @Valid @RequestBody EventCreateRequest request) {
        return eventService.update(id, organizerId, request);
    }

    @PutMapping("/{id}/sectors")
    @RequiresPermission(Permission.EVENT_MANAGE)
    @Operation(summary = "Definir los sectores y precios de un evento", description = """
            Reemplaza la lista completa: una lista vacía los quita a todos. Cada sector tiene que ser del lugar del             evento y aparecer una sola vez. Solo en `DRAFT`, y solo un organizador de la organización del evento.""")
    @ApiResponse(responseCode = "200", description = "Evento con sus sectores, precios y capacidad")
    @ApiResponse(responseCode = "400", description = "validation_error (precio inválido) / invalid_event_configuration "
            + "(sectores repetidos, en detail.repeatedSectorIds)",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = "forbidden (sin EVENT_MANAGE) / organizer_not_in_organization",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "event_not_found / sector_not_found (sectores que no son del lugar, "
            + "en detail.missingIds) / user_not_found / organizer_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "event_not_editable: el evento no está en DRAFT",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public EventDetailResponse replaceSectors(
            @PathVariable Long id,
            @Parameter(description = "Organizador que hace el cambio (id de usuario)", example = "2")
            @RequestHeader(USER_HEADER) Long organizerId,
            @Valid @RequestBody EventSectorsRequest request) {
        return eventService.replaceSectors(id, organizerId, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(Permission.EVENT_MANAGE)
    @Operation(summary = "Borrar un borrador", description = """
            Solo en `DRAFT`, que todavía no tiene entradas. Borra también sus sectores y participantes. Un evento             publicado no se borra: se cancela.""")
    @ApiResponse(responseCode = "204", description = "Evento borrado")
    @ApiResponse(responseCode = "403", description = "forbidden (sin EVENT_MANAGE) / organizer_not_in_organization",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "event_not_found / user_not_found / organizer_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "event_not_editable: el evento no está en DRAFT",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public void delete(
            @PathVariable Long id,
            @Parameter(description = "Organizador que lo borra (id de usuario)", example = "2")
            @RequestHeader(USER_HEADER) Long organizerId) {
        eventService.delete(id, organizerId);
    }

    @PostMapping("/{id}/publish")
    @RequiresPermission(Permission.EVENT_MANAGE)
    @Operation(summary = "Publicar un borrador", description = """
            `DRAFT` → `PUBLISHED`. Tiene que tener al menos un sector a la venta, ningún sector numerado sin             asientos, la fecha en el futuro y, si es de alta demanda, un `saleStart` anterior a la fecha. Si falla             algo, la respuesta lista todos los problemas en `detail.problems`.""")
    @ApiResponse(responseCode = "200", description = "Evento publicado")
    @ApiResponse(responseCode = "403", description = "forbidden (sin EVENT_MANAGE) / organizer_not_in_organization",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "event_not_found / user_not_found / organizer_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "invalid_status_transition (no es un borrador) / "
            + "event_not_publishable (con detail.problems)",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public EventDetailResponse publish(
            @PathVariable Long id,
            @Parameter(description = "Organizador que lo publica (id de usuario)", example = "2")
            @RequestHeader(USER_HEADER) Long organizerId) {
        return eventService.publish(id, organizerId);
    }

    @PostMapping("/{id}/cancel")
    @RequiresPermission({Permission.EVENT_ADMIN, Permission.EVENT_MANAGE})
    @Operation(summary = "Cancelar un evento", description = """
            Definitivo, desde `PUBLISHED`, `SOLD_OUT` o `SUSPENDED`. Lo puede hacer un administrador (`EVENT_ADMIN`), sobre cualquier evento, o el organizador que creó el evento (`EVENT_MANAGE`), mientras siga siendo organizador de su organización. Otro organizador de la misma organización no puede.             Cancelar uno ya cancelado no cambia nada. Frena la venta en el ticket-service; los reintegros todavía no están.""")
    @ApiResponse(responseCode = "200", description = "Evento cancelado")
    @ApiResponse(responseCode = "403", description = "forbidden (sin EVENT_ADMIN ni EVENT_MANAGE) / not_event_creator / "
            + "organizer_not_in_organization",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "event_not_found / user_not_found / organizer_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "invalid_status_transition: es un borrador (se borra) o ya terminó",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public EventDetailResponse cancel(
            @PathVariable Long id,
            @Parameter(description = "Administrador u organizador que creó el evento (id de usuario)", example = "3")
            @RequestHeader(USER_HEADER) Long userId,
            @Parameter(hidden = true) @RequestAttribute(PermissionInterceptor.CALLER_ATTRIBUTE) Caller caller) {
        return eventService.cancel(id, caller);
    }

    @PostMapping("/{id}/suspend")
    @RequiresPermission({Permission.EVENT_ADMIN, Permission.EVENT_MANAGE})
    @Operation(summary = "Suspender un evento", description = """
            Desde `PUBLISHED` o `SOLD_OUT`, y se recuerda cuál de los dos era para reanudarlo. Lo puede hacer un administrador (`EVENT_ADMIN`), sobre cualquier evento, o el organizador que creó el evento (`EVENT_MANAGE`), mientras siga siendo organizador de su organización. Otro organizador de la misma organización no puede.             Suspender uno ya suspendido no cambia nada.""")
    @ApiResponse(responseCode = "200", description = "Evento suspendido")
    @ApiResponse(responseCode = "403", description = "forbidden (sin EVENT_ADMIN ni EVENT_MANAGE) / not_event_creator / "
            + "organizer_not_in_organization",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "event_not_found / user_not_found / organizer_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "invalid_status_transition: es un borrador, o está cancelado o finalizado",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public EventDetailResponse suspend(
            @PathVariable Long id,
            @Parameter(description = "Administrador u organizador que creó el evento (id de usuario)", example = "3")
            @RequestHeader(USER_HEADER) Long userId,
            @Parameter(hidden = true) @RequestAttribute(PermissionInterceptor.CALLER_ATTRIBUTE) Caller caller) {
        return eventService.suspend(id, caller);
    }

    @PostMapping("/{id}/resume")
    @RequiresPermission({Permission.EVENT_ADMIN, Permission.EVENT_MANAGE})
    @Operation(summary = "Reanudar un evento suspendido", description = """
            Vuelve a `PUBLISHED` o `SOLD_OUT`, el que tenía antes de suspenderse. El body es opcional: con             `dateTime`, el evento queda reprogramado para esa fecha. Lo puede hacer un administrador (`EVENT_ADMIN`), sobre cualquier evento, o el organizador que creó el evento (`EVENT_MANAGE`), mientras siga siendo organizador de su organización. Otro organizador de la misma organización no puede.""")
    @ApiResponse(responseCode = "200", description = "Evento reanudado")
    @ApiResponse(responseCode = "400", description = "validation_error (fecha pasada) / invalid_event_configuration "
            + "(fecha nueva anterior a saleStart)",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = "forbidden (sin EVENT_ADMIN ni EVENT_MANAGE) / not_event_creator / "
            + "organizer_not_in_organization",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "event_not_found / user_not_found / organizer_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "invalid_status_transition: no está suspendido",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public EventDetailResponse resume(
            @PathVariable Long id,
            @Parameter(description = "Administrador u organizador que creó el evento (id de usuario)", example = "3")
            @RequestHeader(USER_HEADER) Long userId,
            @Parameter(hidden = true) @RequestAttribute(PermissionInterceptor.CALLER_ATTRIBUTE) Caller caller,
            @Valid @RequestBody(required = false) EventResumeRequest request) {
        return eventService.resume(id, caller, request);
    }
}

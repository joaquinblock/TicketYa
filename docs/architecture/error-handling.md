# Manejo de errores

> Issue #15. Vive en el módulo `common` y lo recibe cada servicio que depende de él (ver `microservices.md` → Módulos Maven).

## Formato

Todos los servicios responden los errores con `ErrorDto`:

```json
{ "error": "event_not_found", "message": "No se encontró el evento con id 7", "status": 404, "detail": { } }
```

`detail` es opcional: trae los campos inválidos o datos extra del error.

## Cómo tirar un error de negocio

```java
throw new TicketYaException(EventErrorCode.EVENT_NOT_FOUND, "No se encontró el evento con id %d".formatted(id));
```

No se crea una clase por error. Cada servicio tiene su enum `<Servicio>ErrorCode` (en el paquete `exception`) que implementa `ErrorCode`, y el código nuevo se agrega ahí con su status HTTP.

| Enum | Dónde | Códigos |
|---|---|---|
| `CommonErrorCode` | common | `validation_error`, `invalid_parameter`, `unauthorized`, `forbidden`, `resource_not_found`, `method_not_allowed`, `data_conflict`, `service_unavailable`, `internal_error` |
| `AuthErrorCode` | auth-service | `user_not_found`, `email_duplicated`, `invalid_credentials`, `organization_not_found`, `organizer_not_found`, `staff_not_assigned` |
| `EventErrorCode` | event-service | `event_not_found`, `venue_not_found`, `venue_in_use`, `sector_not_found`, `sector_name_duplicated`, `sector_in_use`, `sector_not_numbered`, `invalid_sector_configuration`, `seat_not_found`, `seat_row_duplicated`, `participant_not_found`, `participant_duplicated`, `participant_in_use`, `invalid_status_transition`, `event_not_editable`, `event_not_publishable`, `invalid_event_configuration`, `organizer_not_in_organization`, `not_event_creator` |
| `TicketErrorCode` | ticket-service | `order_not_found`, `ticket_not_found`, `sale_not_started`, `seat_unavailable`, `sector_sold_out`, `ticket_limit_exceeded`, `reservation_expired`, `ticket_already_used`, `refund_not_allowed`, `invalid_qr` |
| `PaymentErrorCode` | payment-service | `payment_not_found`, `payment_rejected`, `payment_gateway_unavailable`, `refund_failed` |

**`forbidden` es el error de permisos de todos los servicios.** Lo usa `PermissionInterceptor` cuando el rol del usuario no tiene el permiso que pide `@RequiresPermission`, con un mensaje que dice cuál faltó. Los `403` de negocio, como `organizer_not_in_organization`, llevan su propio código (ver [`permissions.md`](permissions.md)).

## Qué atrapa el `GlobalExceptionHandler`

| Caso | Status | Código |
|---|---|---|
| `TicketYaException` | el de su `ErrorCode` | el de su `ErrorCode` |
| Body con `@Valid` inválido, JSON mal formado, enum inexistente | 400 | `validation_error` |
| `@PathVariable` / `@RequestParam` que no cumple su validación, o validación de entidad al persistir | 400 | `validation_error` |
| Parámetro con tipo equivocado, falta un `@RequestParam` o un header (p. ej. `X-User-Id`), `sort` por un campo inexistente | 400 | `invalid_parameter` |
| URL inexistente / método no soportado | 404 / 405 | `resource_not_found` / `method_not_allowed` |
| Restricción de la base (unique, FK) o bloqueo optimista | 409 | `data_conflict` |
| Otro servicio responde 4xx con `ErrorDto` | el mismo | se reenvía tal cual |
| Otro servicio responde 5xx o no responde | 503 | `service_unavailable` |
| Cualquier otra excepción | 500 | `internal_error` (sin exponer el mensaje) |

## Tests

- `common` prueba cada handler con `GlobalExceptionHandlerTest` (MockMvc standalone, sin levantar Spring Boot).
- En los servicios, los errores se prueban desde los tests de controller. `common` registra el handler también en `@WebMvcTest`, así que no hace falta importarlo.

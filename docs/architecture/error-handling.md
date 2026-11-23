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
| `AuthErrorCode` | auth-service | `user_not_found`, `email_duplicated`, `invalid_credentials`, `organization_not_found`, `organizer_not_found`, `access_staff_not_found`, `staff_not_assigned`, `user_disabled` |
| `EventErrorCode` | event-service | `event_not_found`, `venue_not_found`, `venue_in_use`, `sector_not_found`, `sector_name_duplicated`, `sector_in_use`, `sector_not_numbered`, `invalid_sector_configuration`, `seat_not_found`, `seat_row_duplicated`, `participant_not_found`, `invalid_status_transition`, `event_not_editable`, `invalid_event_configuration`, `organizer_not_in_organization`, `participant_duplicated`, `participant_in_use`, `event_not_publishable`, `not_event_creator` |
| `TicketErrorCode` | ticket-service | `order_not_found`, `ticket_not_found`, `sale_not_started`, `seat_unavailable`, `sector_sold_out`, `ticket_limit_exceeded`, `reservation_expired`, `invalid_queue_token`, `ticket_already_used`, `refund_not_allowed`, `invalid_qr`, `return_request_not_found`, `return_request_already_pending`, `event_not_on_sale`, `order_not_cancellable`, `order_not_payable`, `order_not_paid`, `payment_in_progress`, `payment_window_closed`, `purchase_failed` |
| `PaymentErrorCode` | payment-service | `payment_not_found`, `payment_rejected`, `payment_gateway_unavailable`, `payment_not_refundable`, `refund_failed` |
| `QueueErrorCode` | queue-service | `event_not_found`, `queue_entry_not_found`, `event_not_high_demand`, `sale_not_started` |

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

## Con qué nivel se registra

Una `TicketYaException` se registra con el nivel de su `ErrorCode` (`getLevel()`):

- **`ERROR`** si su status es 5xx. Lo que falla es de nuestro lado: un servicio caído, la pasarela que no responde (`payment_gateway_unavailable`), un reintegro que falló (`refund_failed`).
- **`WARN`** si es 4xx. Es un error del cliente y una respuesta esperada: un id que no existe, un token vencido, un asiento ya vendido.

El nivel sale del status, así que un código nuevo no tiene que declararlo. Uno que sea crítico aunque responda 4xx sobrescribe `getLevel()`. Las excepciones no previstas (500) se registran en `ERROR`, con su stack trace.

En Docker los logs son JSON, así que lo crítico de todo el sistema se encuentra con `docker compose logs | grep '"level":"ERROR"'`, y cada línea trae su `correlationId` ([ADR 0010](../adr/0010-trazabilidad-con-x-correlation-id.md)).

## Tests

- No hay tests propios del handler ni de los controllers: los códigos de error se prueban a mano con las colecciones de Postman, y los tests de servicio verifican qué `ErrorCode` se tira.
- Si se agrega un `@WebMvcTest`, `common` registra el handler también ahí, así que no hace falta importarlo.

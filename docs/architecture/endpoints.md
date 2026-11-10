# Listado de endpoints

> Consigna, *Diseño de API REST*: *"listado de endpoints con método, ruta, parámetros, códigos de respuesta, ejemplos de request/response (JSON)"*. Se generó a partir de las anotaciones OpenAPI de cada controller, así que coincide con Swagger (http://localhost:8080/swagger-ui.html), donde además están los esquemas completos y un ejemplo de cada campo.

## Convenciones

- **Todo entra por el gateway** (`http://localhost:8080`). Las rutas `/internal/**` son llamadas entre servicios: el gateway no las enruta y se usan contra el puerto de cada servicio.
- **Identidad:** en la Entrega 1 va en el header `X-User-Id` (sin JWT). En el event-service, los endpoints con permiso lo verifican contra el auth-service antes de entrar (ver [`permissions.md`](permissions.md)): si falta, `400`; si el usuario no tiene el permiso, `403 forbidden`; si no existe, `404 user_not_found`.
- **Errores:** todos responden con el mismo `ErrorDto` (`error`, `message`, `status` y, a veces, `detail`). Los códigos de cada servicio están en [`error-handling.md`](error-handling.md).
- **Validación:** un body o un parámetro inválido responde `400 validation_error` con cada campo en `detail`, aunque la tabla no lo repita en cada fila. Un servicio del que se depende y no responde da `503 service_unavailable`.
- **Fechas:** ISO-8601 sin zona (`2026-12-12T21:00:00`). **Montos:** decimales con dos posiciones, en pesos.

## Resumen

| Servicio | Puerto | Endpoints | Qué cubre |
|---|---|---|---|
| [auth-service](#auth-service) | 8081 | 3 | Usuarios, permisos, organizaciones y organizadores. |
| [event-service](#event-service) | 8082 | 31 | Lugares, sectores, asientos, participantes y eventos (alta, configuración, estados y búsqueda). |
| [ticket-service](#ticket-service) | 8083 | 11 | Disponibilidad, reservas, pago y emisión de entradas, compras y devoluciones. |
| [payment-service](#payment-service) | 8084 | 4 | Cobros y reintegros contra la pasarela simulada. |

## auth-service

Usuarios, permisos, organizaciones y organizadores. Detalle de reglas y decisiones: [`auth-service.md`](auth-service.md).

### Públicos

| Método | Ruta | Qué hace | Parámetros | Quién | Respuestas |
|---|---|---|---|---|---|
| `GET` | `/api/organizations/{id}` | Ver una organización | `{id}` | Público | **200** Organización encontrada<br>**404** organization_not_found |
| `GET` | `/api/organizers/{userId}` | Ver un organizador activo | `{userId}` | Público | **200** Organizador activo<br>**404** organizer_not_found |
| `GET` | `/api/users/{id}/permissions` | Ver el rol y los permisos de un usuario | `{id}` | Público | **200** Usuario activo con su rol y sus permisos<br>**404** user_not_found |

## event-service

Lugares, sectores, asientos, participantes y eventos (alta, configuración, estados y búsqueda). Detalle de reglas y decisiones: [`event-service-api.md`](event-service-api.md).

### Públicos

| Método | Ruta | Qué hace | Parámetros | Quién | Respuestas |
|---|---|---|---|---|---|
| `POST` | `/api/events` | Crear un evento | header `X-Organization-Id`<br>header `X-User-Id`<br>body `EventCreateRequest` | `EVENT_MANAGE` | **201** Evento creado en borrador<br>**400** validation_error / invalid_parameter / invalid_event_configuration<br>**403** forbidden (sin EVENT_MANAGE) / organizer_not_in_organization<br>**404** user_not_found / venue_not_found / organization_not_found / organizer_not_found<br>**503** service_unavailable: el auth-service no responde |
| `PUT` | `/api/events/{id}/participants` | Definir los participantes de un evento | `{id}`<br>header `X-User-Id`<br>body `EventParticipantsRequest` | `EVENT_MANAGE` | **200** Participantes del evento, ordenados por nombre<br>**400** validation_error / invalid_parameter<br>**403** forbidden (sin EVENT_MANAGE) / organizer_not_in_organization<br>**404** event_not_found / participant_not_found (con missingIds en detail) / user_not_found / organizer_not_found<br>**409** event_not_editable: el evento no está en DRAFT<br>**503** service_unavailable: el auth-service no responde |
| `GET` | `/api/events` | Buscar eventos | query `EventSearchRequest` (filtros) (opcional) | Público | **200** Página de resultados (puede venir vacía)<br>**400** validation_error (size fuera de 1..50, tipo u orden inexistente) / invalid_parameter (from posterior a to) |
| `GET` | `/api/events/{id}` | Ver un evento | `{id}`<br>header `X-User-Id` (opcional) | Público | **200** Evento encontrado<br>**404** event_not_found (no existe, o es un borrador de otra organización)<br>**503** service_unavailable: el auth-service no responde (solo al pedir un borrador) |
| `PUT` | `/api/events/{id}` | Editar un evento | `{id}`<br>header `X-User-Id`<br>body `EventCreateRequest` | `EVENT_MANAGE` | **200** Evento modificado<br>**400** validation_error / invalid_parameter / invalid_event_configuration<br>**403** forbidden (sin EVENT_MANAGE) / organizer_not_in_organization<br>**404** event_not_found / venue_not_found / user_not_found / organizer_not_found<br>**409** event_not_editable: el evento no está en DRAFT |
| `PUT` | `/api/events/{id}/sectors` | Definir los sectores y precios de un evento | `{id}`<br>header `X-User-Id`<br>body `EventSectorsRequest` | `EVENT_MANAGE` | **200** Evento con sus sectores, precios y capacidad<br>**400** validation_error (precio inválido) / invalid_event_configuration (sectores repetidos, en detail.repeatedSectorIds)<br>**403** forbidden (sin EVENT_MANAGE) / organizer_not_in_organization<br>**404** event_not_found / sector_not_found (sectores que no son del lugar, en detail.missingIds) / user_not_found / organizer_not_found<br>**409** event_not_editable: el evento no está en DRAFT |
| `DELETE` | `/api/events/{id}` | Borrar un borrador | `{id}`<br>header `X-User-Id` | `EVENT_MANAGE` | **204** Evento borrado<br>**403** forbidden (sin EVENT_MANAGE) / organizer_not_in_organization<br>**404** event_not_found / user_not_found / organizer_not_found<br>**409** event_not_editable: el evento no está en DRAFT |
| `POST` | `/api/events/{id}/publish` | Publicar un borrador | `{id}`<br>header `X-User-Id` | `EVENT_MANAGE` | **200** Evento publicado<br>**403** forbidden (sin EVENT_MANAGE) / organizer_not_in_organization<br>**404** event_not_found / user_not_found / organizer_not_found<br>**409** invalid_status_transition (no es un borrador) / event_not_publishable (con detail.problems) |
| `POST` | `/api/events/{id}/cancel` | Cancelar un evento | `{id}`<br>header `X-User-Id` | `EVENT_ADMIN` o `EVENT_MANAGE` | **200** Evento cancelado<br>**403** forbidden (sin EVENT_ADMIN ni EVENT_MANAGE) / not_event_creator / organizer_not_in_organization<br>**404** event_not_found / user_not_found / organizer_not_found<br>**409** invalid_status_transition: es un borrador (se borra) o ya terminó |
| `POST` | `/api/events/{id}/suspend` | Suspender un evento | `{id}`<br>header `X-User-Id` | `EVENT_ADMIN` o `EVENT_MANAGE` | **200** Evento suspendido<br>**403** forbidden (sin EVENT_ADMIN ni EVENT_MANAGE) / not_event_creator / organizer_not_in_organization<br>**404** event_not_found / user_not_found / organizer_not_found<br>**409** invalid_status_transition: es un borrador, o está cancelado o finalizado |
| `POST` | `/api/events/{id}/resume` | Reanudar un evento suspendido | `{id}`<br>header `X-User-Id`<br>body `EventResumeRequest` (opcional) | `EVENT_ADMIN` o `EVENT_MANAGE` | **200** Evento reanudado<br>**400** validation_error (fecha pasada) / invalid_event_configuration (fecha nueva anterior a saleStart)<br>**403** forbidden (sin EVENT_ADMIN ni EVENT_MANAGE) / not_event_creator / organizer_not_in_organization<br>**404** event_not_found / user_not_found / organizer_not_found<br>**409** invalid_status_transition: no está suspendido |
| `GET` | `/api/participants` | Listar participantes | `?type` (opcional)<br>`?name` (opcional) | Público | **200** Participantes encontrados (puede ser una lista vacía)<br>**400** invalid_parameter: tipo inexistente |
| `GET` | `/api/participants/{id}` | Ver un participante | `{id}` | Público | **200** Participante encontrado<br>**404** participant_not_found |
| `POST` | `/api/participants` | Crear un participante | body `ParticipantRequest` | Público | **201** Participante creado<br>**400** validation_error<br>**409** participant_duplicated |
| `PUT` | `/api/participants/{id}` | Modificar un participante | `{id}`<br>body `ParticipantRequest` | Público | **200** Participante modificado<br>**400** validation_error<br>**404** participant_not_found<br>**409** participant_duplicated |
| `DELETE` | `/api/participants/{id}` | Eliminar un participante | `{id}` | Público | **204** Participante eliminado<br>**404** participant_not_found<br>**409** participant_in_use |
| `GET` | `/api/venues/{venueId}/sectors/{sectorId}/seats` | Listar los asientos de un sector | `{venueId}`<br>`{sectorId}` | Público | **200** Asientos del sector (vacío si no es numerado)<br>**404** venue_not_found / sector_not_found |
| `POST` | `/api/venues/{venueId}/sectors/{sectorId}/seats` | Agregar filas de asientos | `{venueId}`<br>`{sectorId}`<br>body `SeatBatchRequest` | Público | **201** Asientos creados<br>**404** venue_not_found / sector_not_found<br>**409** sector_not_numbered / sector_in_use / seat_row_duplicated |
| `DELETE` | `/api/venues/{venueId}/sectors/{sectorId}/seats/{seatId}` | Eliminar un asiento | `{venueId}`<br>`{sectorId}`<br>`{seatId}` | Público | **204** Asiento eliminado<br>**404** venue_not_found / sector_not_found / seat_not_found<br>**409** sector_in_use |
| `GET` | `/api/venues/{venueId}/sectors` | Listar los sectores de un lugar | `{venueId}` | Público | **200** Sectores del lugar<br>**404** venue_not_found |
| `GET` | `/api/venues/{venueId}/sectors/{sectorId}` | Ver un sector | `{venueId}`<br>`{sectorId}` | Público | **200** Sector encontrado<br>**404** venue_not_found / sector_not_found |
| `POST` | `/api/venues/{venueId}/sectors` | Crear un sector | `{venueId}`<br>body `SectorCreateRequest` | Público | **201** Sector creado<br>**400** invalid_sector_configuration<br>**404** venue_not_found<br>**409** sector_name_duplicated / seat_row_duplicated |
| `PUT` | `/api/venues/{venueId}/sectors/{sectorId}` | Modificar un sector | `{venueId}`<br>`{sectorId}`<br>body `SectorUpdateRequest` | Público | **200** Sector modificado<br>**400** invalid_sector_configuration<br>**404** venue_not_found / sector_not_found<br>**409** sector_name_duplicated / sector_in_use |
| `DELETE` | `/api/venues/{venueId}/sectors/{sectorId}` | Eliminar un sector | `{venueId}`<br>`{sectorId}` | Público | **204** Sector eliminado<br>**404** venue_not_found / sector_not_found<br>**409** sector_in_use |
| `GET` | `/api/venues` | Listar lugares | `?city` (opcional) | Público | **200** Lugares encontrados (puede ser una lista vacía) |
| `GET` | `/api/venues/{id}` | Ver un lugar | `{id}` | Público | **200** Lugar encontrado<br>**404** venue_not_found |
| `POST` | `/api/venues` | Crear un lugar | body `VenueRequest` | Público | **201** Lugar creado |
| `PUT` | `/api/venues/{id}` | Modificar un lugar | `{id}`<br>body `VenueRequest` | Público | **200** Lugar modificado<br>**404** venue_not_found |
| `DELETE` | `/api/venues/{id}` | Eliminar un lugar | `{id}` | Público | **204** Lugar eliminado<br>**404** venue_not_found<br>**409** venue_in_use |

### Internos (entre servicios)

| Método | Ruta | Qué hace | Parámetros | Quién | Respuestas |
|---|---|---|---|---|---|
| `PUT` | `/internal/events/{id}/sold-out` | Marcar un evento como agotado | `{id}` | Solo entre servicios | **204** Evento agotado<br>**404** event_not_found<br>**409** invalid_status_transition |
| `PUT` | `/internal/events/{id}/back-on-sale` | Volver a poner en venta un evento agotado | `{id}` | Solo entre servicios | **204** Evento publicado<br>**404** event_not_found<br>**409** invalid_status_transition |

## ticket-service

Disponibilidad, reservas, pago y emisión de entradas, compras y devoluciones. Detalle de reglas y decisiones: [`ticket-service.md`](ticket-service.md).

### Públicos

| Método | Ruta | Qué hace | Parámetros | Quién | Respuestas |
|---|---|---|---|---|---|
| `GET` | `/api/availability` | Consultar la disponibilidad de un evento | `?eventId` | Público | **200** Disponibilidad por sector<br>**404** resource_not_found: el evento no tiene lugares a la venta |
| `POST` | `/api/orders` | Reservar lugares | header `X-User-Id`<br>body `ReservationRequest` | `X-User-Id`, sin validar rol | **201** Reserva creada, o la orden ya creada por ese mismo intento<br>**400** validation_error<br>**404** resource_not_found: el evento, sector o asiento no existe<br>**409** seat_unavailable, sector_sold_out, ticket_limit_exceeded, event_not_on_sale, sale_not_started o data_conflict |
| `POST` | `/api/orders/{id}/pay` | Pagar una orden | header `X-User-Id`<br>`{id}`<br>body `PayOrderRequest` | `X-User-Id`, sin validar rol | **200** Orden pagada, con sus entradas y QR<br>**400** validation_error<br>**404** order_not_found: no existe o es de otro cliente<br>**409** reservation_expired, payment_window_closed, order_not_payable o payment_in_progress<br>**422** payment_rejected, con reason y rejectedAttempts en detail<br>**503** service_unavailable: el pago quedó sin resultado y solo se puede repetir ese intento |
| `GET` | `/api/orders` | Ver mis compras | header `X-User-Id` | `X-User-Id`, sin validar rol | **200** Órdenes del cliente, de la más nueva a la más vieja |
| `GET` | `/api/orders/{id}` | Ver una compra | header `X-User-Id`<br>`{id}` | `X-User-Id`, sin validar rol | **200** Orden con sus entradas<br>**404** order_not_found: no existe o es de otro cliente |
| `POST` | `/api/orders/{id}/cancel` | Abandonar una reserva | header `X-User-Id`<br>`{id}` | `X-User-Id`, sin validar rol | **200** Orden abandonada<br>**404** order_not_found: no existe o es de otro cliente<br>**409** payment_in_progress, reservation_expired u order_not_cancellable |
| `POST` | `/api/tickets/{ticketId}/return-requests` | Solicitar la devolución de una entrada | header `X-User-Id`<br>`{ticketId}` | `X-User-Id`, sin validar rol | **201** Solicitud creada en PENDING<br>**404** ticket_not_found: no existe o es de otro cliente<br>**409** refund_not_allowed o return_request_already_pending |
| `GET` | `/api/return-requests` | Listar las solicitudes de devolución | `?status` (opcional)<br>`?page` (opcional)<br>`?size` (opcional) | Público | **200** Página de solicitudes<br>**400** Parámetros inválidos |
| `POST` | `/api/return-requests/{id}/approve` | Aprobar una devolución | header `X-User-Id`<br>`{id}` | `X-User-Id`, sin validar rol | **200** Solicitud aprobada<br>**404** return_request_not_found<br>**409** refund_not_allowed o ticket_already_used<br>**503** service_unavailable: la solicitud sigue PENDING |
| `POST` | `/api/return-requests/{id}/reject` | Rechazar una devolución | header `X-User-Id`<br>`{id}` | `X-User-Id`, sin validar rol | **200** Solicitud rechazada<br>**404** return_request_not_found<br>**409** refund_not_allowed |

### Internos (entre servicios)

| Método | Ruta | Qué hace | Parámetros | Quién | Respuestas |
|---|---|---|---|---|---|
| `PUT` | `/internal/event-sales/{eventId}` | Sincronizar la venta de un evento | `{eventId}`<br>body `EventSaleRequest` | Solo entre servicios | **204** Venta sincronizada<br>**400** validation_error |

## payment-service

Cobros y reintegros contra la pasarela simulada. Detalle de reglas y decisiones: [`payment-service.md`](payment-service.md).

### Públicos

| Método | Ruta | Qué hace | Parámetros | Quién | Respuestas |
|---|---|---|---|---|---|
| `GET` | `/api/payments` | Consultar los pagos de una orden | `?orderId` | Público | **200** Pagos de la orden, del más antiguo al más nuevo<br>**400** invalid_parameter o validation_error: falta orderId o no es válido<br>**404** payment_not_found: la orden no tiene pagos |

### Internos (entre servicios)

| Método | Ruta | Qué hace | Parámetros | Quién | Respuestas |
|---|---|---|---|---|---|
| `POST` | `/internal/payments` | Cobrar una orden | body `PaymentRequest` | Solo entre servicios | **201** Pago aprobado<br>**400** validation_error<br>**409** data_conflict: la clave ya se usó con otros datos o la orden ya tiene otro pago PENDING o APPROVED<br>**422** payment_rejected, con reason y rejectedAttempts en detail<br>**503** payment_gateway_unavailable: la pasarela no respondió y se puede reintentar con la misma clave |
| `GET` | `/internal/payments` | Consultar los pagos de una orden | `?orderId` | Solo entre servicios | **200** Pagos de la orden, del más antiguo al más nuevo<br>**400** invalid_parameter o validation_error: falta orderId o no es válido<br>**404** payment_not_found: la orden no tiene pagos |
| `POST` | `/internal/refunds` | Reintegrar una entrada | body `RefundRequest` | Solo entre servicios | **201** Reintegro acreditado<br>**200** La entrada ya tenía ese reintegro y no se acredita de nuevo<br>**400** validation_error<br>**404** payment_not_found: la orden no tiene pagos<br>**409** payment_not_refundable: sin pago aprobado o supera lo cobrado. data_conflict: la entrada ya tiene un reintegro con otros datos<br>**502** refund_failed: la pasarela no acreditó y se puede reintentar |

## Ejemplos

Con los datos de los seeders recién cargados. Las fechas del event-service son relativas al día de arranque, así que las de los ejemplos varían.

### Crear un evento (event-service)

```http
POST /api/events
X-User-Id: 2
X-Organization-Id: 1
Content-Type: application/json

{
  "name": "Los Serranos en vivo",
  "description": "Presentación del nuevo disco",
  "type": "CONCERT",
  "venueId": 1,
  "dateTime": "2099-12-12T21:00:00",
  "highDemand": true,
  "saleStart": "2099-11-20T10:00:00",
  "maxTicketsPerCustomer": 4
}
```
```json
201 Created
{
  "id": 13, "name": "Los Serranos en vivo", "description": "Presentación del nuevo disco", "type": "CONCERT",
  "organizationId": 1, "createdBy": 2,
  "venue": { "id": 1, "name": "Estadio del Sur", "address": "Av. del Estadio 1000", "city": "Buenos Aires",
             "latitude": -34.5453, "longitude": -58.4498 },
  "dateTime": "2099-12-12T21:00:00", "status": "DRAFT", "highDemand": true,
  "saleStart": "2099-11-20T10:00:00", "maxTicketsPerCustomer": 4
}
```

### Definir sectores y precios, y publicar (event-service)

```http
PUT /api/events/10/sectors
X-User-Id: 2
Content-Type: application/json

{ "sectors": [ { "sectorId": 3, "price": 12000 }, { "sectorId": 4, "price": 9000.50 } ] }
```
```json
200 OK
{ "id": 10, "status": "DRAFT", "capacity": 74,
  "sectors": [ { "sectorId": 3, "name": "Platea", "numbered": true, "capacity": 50, "price": 12000.00 },
               { "sectorId": 4, "name": "Pullman", "numbered": true, "capacity": 24, "price": 9000.50 } ],
  "participants": [], "...": "resto de los datos del evento" }
```
```http
POST /api/events/10/publish
X-User-Id: 2
```
```json
200 OK   { "id": 10, "status": "PUBLISHED", "...": "..." }

409 Conflict   (si no se puede publicar todavía)
{ "error": "event_not_publishable", "message": "El evento 10 todavía no se puede publicar", "status": 409,
  "detail": { "problems": ["No tiene sectores a la venta"] } }
```

### Buscar eventos (event-service)

```http
GET /api/events?q=serran&sort=RELEVANCE&size=2
```
```json
200 OK
{
  "content": [
    { "id": 11, "name": "Peña folklórica serrana", "type": "CONCERT", "status": "PUBLISHED",
      "dateTime": "2026-11-28T21:00:00", "highDemand": false, "organizationId": 2,
      "venue": { "id": 2, "name": "Teatro de la Sierra", "city": "Tandil", "...": "..." },
      "priceFrom": 7000.00, "participants": ["Ballet Folklórico Serrano"] },
    { "id": 1, "name": "Los Serranos en vivo", "type": "CONCERT", "status": "PUBLISHED",
      "dateTime": "2026-12-13T21:00:00", "highDemand": true, "organizationId": 1,
      "venue": { "id": 1, "name": "Estadio del Sur", "city": "Buenos Aires", "...": "..." },
      "priceFrom": 45000.00, "participants": ["Los Serranos"] }
  ],
  "page": 0, "size": 2, "totalElements": 3, "totalPages": 2
}
```

### Reservar asientos y pagar (ticket-service)

```http
POST /api/orders
X-User-Id: 1
Content-Type: application/json

{ "eventId": 2, "idempotencyKey": "e3b0c442-98fc-4b1d-8f3a-7c5d1a2b3c4d", "seatIds": [90, 91] }
```
```json
201 Created
{ "id": 4, "eventId": 2, "status": "PENDING", "expiresAt": "2026-11-10T10:42:00", "total": 40000.00,
  "paymentMethod": null,
  "tickets": [ { "id": 6, "eventSectorId": 3, "seatId": 90, "price": 20000.00, "status": "RESERVED", "qrCode": null },
               { "id": 7, "eventSectorId": 3, "seatId": 91, "price": 20000.00, "status": "RESERVED", "qrCode": null } ] }
```
```http
POST /api/orders/4/pay
X-User-Id: 1
Content-Type: application/json

{ "paymentMethod": "CREDIT_CARD", "idempotencyKey": "9f1c2d3e-4b5a-4c6d-8e7f-0a1b2c3d4e5f" }
```
```json
200 OK
{ "id": 4, "eventId": 2, "status": "PAID", "total": 40000.00, "paymentMethod": "CREDIT_CARD",
  "tickets": [ { "id": 6, "seatId": 90, "status": "ISSUED", "qrCode": "3f1c2d3e-4b5a-4c6d-8e7f-0a1b2c3d4e5f", "...": "..." },
               { "id": 7, "seatId": 91, "status": "ISSUED", "qrCode": "a8b2c4d6-1e3f-4a5b-9c7d-2e4f6a8b0c1d", "...": "..." } ] }

422 Unprocessable Content   (con DEBIT_CARD, que la pasarela simulada rechaza)
{ "error": "payment_rejected", "message": "El pago fue rechazado: Fondos insuficientes", "status": 422,
  "detail": { "reason": "Fondos insuficientes", "rejectedAttempts": 1 } }
```

El flujo completo, con el rechazo y el vencimiento de la reserva, está en [`purchase-sequence.md`](purchase-sequence.md).

### Un error de permisos (event-service)

```http
POST /api/events/1/suspend
X-User-Id: 6
```
```json
403 Forbidden
{ "error": "not_event_creator", "message": "Solo el organizador que creó el evento 1, o un administrador, puede suspenderlo",
  "status": 403 }
```

# Creación de eventos: qué falta para el flujo completo

> Épica 3 (#3). Hoy existe `POST /api/events`, que crea el evento en `DRAFT` validando la organización y el organizador contra el auth-service (ver [`auth-service.md`](../architecture/auth-service.md)). El enunciado pide que *"los organizadores crean sus eventos en estado borrador, configuran sectores y precios, y luego los publican"*: falta todo lo que va después del borrador.

## Flujo completo

| Paso | Endpoint | Estado |
|---|---|---|
| 1. Crear el evento en borrador | `POST /api/events` | ✅ Hecho |
| 2. Definir qué sectores se venden y a qué precio | `PUT /api/events/{id}/sectors` | ✅ Hecho |
| 3. Agregar participantes | `PUT /api/events/{id}/participants` | ✅ Hecho |
| 4. Corregir datos mientras es borrador | `PUT /api/events/{id}` | ✅ Hecho |
| 5. Publicar | `POST /api/events/{id}/publish` | ✅ Hecho (sin inventario) |
| 6. Que el ticket-service arme el inventario | `TicketClient` | ⬜ Falta |

## Para poder probar lo que ya está

- [x] **Seeder del auth-service:** `AuthSeeder` carga la organización `1` con el organizador `2` (`organizer@ticketya.com`), que son los ids que asume `EventSeeder`, y la organización `2` con el organizador `5`. El `403` se prueba con el organizador `2` contra la organización `2` (PR #37 y #56)
- [x] **Seeder del event-service:** eventos en todos los estados, tipos y ciudades, de las dos organizaciones (ver *Datos de prueba* en [`event-service.md`](../architecture/event-service.md))
- [x] **Ruta del gateway:** `api-gateway` ya enruta `/api/organizers/**` al auth-service
- [x] **Permisos:** `POST /api/events` exige `EVENT_MANAGE` con `@RequiresPermission` (ver [`permissions.md`](../architecture/permissions.md))
- [x] **Correr los tests:** corren con `mvn -o -Djava.version=24 -pl event-service test` (Maven de la terminal usa JDK 24 y el proyecto pide 25)
- [ ] **Colección de Postman:** importar [`docs/postman/event-creation.postman_collection.json`](../postman/event-creation.postman_collection.json) y correrla con los tres servicios levantados

## Configuración del evento (mientras está en `DRAFT`)

- [x] **Sectores y precios** (`event_sector`): `PUT /api/events/{id}/sectors` con `[{ sectorId, price }]`
  - Cada sector tiene que ser **del lugar del evento**: `404 sector_not_found` si no
  - Sin sectores repetidos (el unique `uk_event_sector` lo protege igual)
  - `price > 0` con dos decimales, como en `EventSector`
  - Reemplaza la lista completa en vez de agregar de a uno: es más simple de validar y de explicar
- [x] **Participantes:** ABM en `/api/participants` y `PUT /api/events/{id}/participants` (ver *Participantes* en [`eventservice.md`](../architecture/eventservice.md))
- [x] **Capacidad del evento:** la calcula `GET /api/events/{id}`. Σ de los sectores que se venden (asientos si es numerado, `capacity` si no). Misma regla que `VenueMapper.toDetail`, pero solo con los sectores de `event_sector` (ver *Capacidades* en `event-service.md`)
- [x] **Modificar el evento:** `PUT /api/events/{id}`, solo en `DRAFT`. Si cambia el lugar, se borran los sectores del lugar anterior
- [x] **Que solo lo toque su organización:** editar, borrar, publicar y asignar participantes comparan `event.organizationId` con el del organizador de `X-User-Id`

## Publicación (`DRAFT` → `PUBLISHED`)

- [x] **Usar la máquina de estados:** publicar y cancelar pasan por `Event.changeStatus`, que la usa. Ya no hay `setStatus`
  - Que lance `TicketYaException(INVALID_STATUS_TRANSITION)` en vez de `RuntimeException`, para que responda `409` y no `500`
  - Sacar el `@Setter` de `status` en `Event` y exponer `changeStatus(next)`, como propone `event-state-machine.md`. Hoy cualquiera puede saltearla con `setStatus`
  - Hacerla `@Component` o dejarla estática, para poder inyectarla o mockearla en los tests
- [x] **Precondiciones para publicar:** si falla alguna, `409 event_not_publishable` con todas en `detail.problems`
  - Al menos un sector con precio
  - Ningún sector numerado sin asientos (se vendería un sector de capacidad 0)
  - `dateTime` todavía futura
  - Alta demanda con `saleStart` (ya se valida al crear, pero un `PUT` podría romperlo)
- [ ] **Inventario en el ticket-service:** al publicar, el ticket-service tiene que crear un `event_seat` por asiento y un `event_sector_stock` por sector no numerado (ver `ticket-service.md`). No existe ese endpoint ni `TicketClient` tiene métodos. Hay que definir qué pasa si la llamada falla después de cambiar el estado: o se publica solo si el inventario se creó, o se reintenta

## Lectura y baja

- [x] **`GET /api/events/{id}`:** con lugar, sectores y precios, participantes y capacidad
- [ ] **`GET /api/events`:** filtros simples (`organizationId`, `status`). La búsqueda de la consigna 9 (tipo, organizador, participante, ciudad, fechas, ordenamiento) es otra tarea
- [x] **`DELETE /api/events/{id}`:** solo en `DRAFT`. En borrador no hay tickets, porque el inventario nace al publicar, así que no hace falta cascada. Un evento publicado se cancela, no se borra
- [ ] **Nombre de la organización:** las páginas de eventos tienen que mostrar `publicName`. [`RFC-004`](../rfc/RFC-004-cross-service-relations.md) eligió la referencia por id, así que para mostrar el nombre hay que sumarle composición (B) o snapshot (D)

## Cancelación

- [x] **`POST /api/events/{id}/cancel`:** el admin o el organizador que creó el evento, desde `PUBLISHED`, `SOLD_OUT` o `SUSPENDED`
- [x] **Suspender y reanudar:** `POST /suspend` y `POST /resume` (con fecha nueva opcional), con la misma regla. Reanudar vuelve a `PUBLISHED` o `SOLD_OUT`, el que tenía
- [ ] **Frenar la venta de un evento suspendido:** le toca al ticket-service (US-TKT-07, #49)
- [ ] **Reintegros:** cancelar todavía no avisa al ticket-service (US-TKT-18, #61). Las entradas de un evento cancelado siguen válidas allá

## Resiliencia de `AuthClient`

- [x] **Timeouts:** 1 s para conectar y 2 s para leer. Si el auth-service se cuelga, responde `503` en vez de esperar para siempre (ver *Timeouts y reintentos* en [`microservices.md`](../architecture/microservices.md))
- [x] **Reintentos:** un reintento a los 200 ms, solo para `GET` y solo si no hubo respuesta (`RetryOnIoErrorInterceptor`)

## Decisiones abiertas

- [x] **RFC-004:** el equipo eligió la referencia por id (opción A)
- [ ] **`created_by` → `organizer_id`:** el campo apunta a `organizer.user_id`, pero su nombre no lo dice
- [ ] **Headers duplicados:** `X-Organization-Id` es redundante, porque el organizador ya trae su `organizationId`. Se mantiene para validar las dos cosas, pero con JWT (Épica 9) alcanza con el usuario del token
- [ ] **`saleStart` en el pasado:** hoy se acepta si es anterior a `dateTime`. Definir si un borrador puede tener una apertura de venta ya vencida

## Documentación

- [ ] Agregar la sección de eventos (endpoints, reglas, errores) a `eventservice.md`
- [x] Sumar `invalid_event_configuration` y `organizer_not_in_organization` a la tabla de `EventErrorCode` en `error-handling.md`

# Creación de eventos: qué falta para el flujo completo

> Épica 3 (#3). Hoy existe `POST /api/events`, que crea el evento en `DRAFT` validando la organización y el organizador contra el auth-service (ver [`auth-service.md`](../architecture/auth-service.md)). El enunciado pide que *"los organizadores crean sus eventos en estado borrador, configuran sectores y precios, y luego los publican"*: falta todo lo que va después del borrador.

## Flujo completo

| Paso | Endpoint | Estado |
|---|---|---|
| 1. Crear el evento en borrador | `POST /api/events` | ✅ Hecho |
| 2. Definir qué sectores se venden y a qué precio | `PUT /api/events/{id}/sectors` | ⬜ Falta |
| 3. Agregar participantes | `PUT /api/events/{id}/participants` | ⬜ Falta |
| 4. Corregir datos mientras es borrador | `PUT /api/events/{id}` | ⬜ Falta |
| 5. Publicar | `POST /api/events/{id}/publish` | ⬜ Falta |
| 6. Que el ticket-service arme el inventario | `TicketClient` | ⬜ Falta |

## Para poder probar lo que ya está

- [ ] **Seeder del auth-service:** `AuthSeeder` no carga organizaciones ni organizadores, así que hoy `POST /api/events` responde siempre `404`. Hay que crear la organización `1` con el organizador `2` (`organizer@ticketya.com`), que son los ids que asume `EventSeeder`. Para probar el `403`, sumar una organización `2` sin ese organizador
- [ ] **Ruta del gateway:** `api-gateway` enruta `/api/organizations/**` al auth-service, pero no `/api/organizers/**`. El event-service no la necesita (llama directo a `:8081`), pero sin ella el endpoint no se puede usar desde `:8080`
- [ ] **Correr los tests:** `EventServiceTest` y `EventControllerTest` todavía no se ejecutaron. Maven desde la terminal usa JDK 24 y el proyecto pide 25
- [ ] **Colección de Postman:** importar [`docs/postman/event-creation.postman_collection.json`](../postman/event-creation.postman_collection.json) y correrla con los tres servicios levantados

## Configuración del evento (mientras está en `DRAFT`)

- [ ] **Sectores y precios** (`event_sector`): recibir `[{ sectorId, price }]`
  - Cada sector tiene que ser **del lugar del evento**: `404 sector_not_found` si no
  - Sin sectores repetidos (el unique `uk_event_sector` lo protege igual)
  - `price > 0` con dos decimales, como en `EventSector`
  - Reemplaza la lista completa en vez de agregar de a uno: es más simple de validar y de explicar
- [ ] **Participantes:** recibir `participantIds` y validar que existan (`404 participant_not_found`, el código ya existe). No hay ABM de participantes ni ruta `/api/participants`, aunque el gateway ya la tiene
- [ ] **Capacidad del evento:** Σ de los sectores que se venden (asientos si es numerado, `capacity` si no). Misma regla que `VenueMapper.toDetail`, pero solo con los sectores de `event_sector` (ver *Capacidades* en `event-service.md`)
- [ ] **Modificar el evento:** solo en `DRAFT`, si no `409 event_not_editable` (el código ya existe). Si cambia el lugar, los sectores cargados dejan de valer y hay que borrarlos
- [ ] **Que solo lo toque su organización:** en editar, configurar y publicar, comparar `event.organizationId` con el del organizador de `X-User-Id`. Hoy solo se valida al crear

## Publicación (`DRAFT` → `PUBLISHED`)

- [ ] **Usar la máquina de estados:** nadie llama a `EventStatusMachine`
  - Que lance `TicketYaException(INVALID_STATUS_TRANSITION)` en vez de `RuntimeException`, para que responda `409` y no `500`
  - Sacar el `@Setter` de `status` en `Event` y exponer `changeStatus(next)`, como propone `event-state-machine.md`. Hoy cualquiera puede saltearla con `setStatus`
  - Hacerla `@Component` o dejarla estática, para poder inyectarla o mockearla en los tests
- [ ] **Precondiciones para publicar:** si falla alguna, `409` con un código nuevo (p. ej. `event_not_publishable`)
  - Al menos un sector con precio
  - Ningún sector numerado sin asientos (se vendería un sector de capacidad 0)
  - `dateTime` todavía futura
  - Alta demanda con `saleStart` (ya se valida al crear, pero un `PUT` podría romperlo)
- [ ] **Inventario en el ticket-service:** al publicar, el ticket-service tiene que crear un `event_seat` por asiento y un `event_sector_stock` por sector no numerado (ver `ticket-service.md`). No existe ese endpoint ni `TicketClient` tiene métodos. Hay que definir qué pasa si la llamada falla después de cambiar el estado: o se publica solo si el inventario se creó, o se reintenta

## Lectura y baja

- [ ] **`GET /api/events/{id}`:** con lugar, sectores y precios, participantes y capacidad
- [ ] **`GET /api/events`:** filtros simples (`organizationId`, `status`). La búsqueda de la consigna 9 (tipo, organizador, participante, ciudad, fechas, ordenamiento) es otra tarea
- [ ] **`DELETE /api/events/{id}`:** solo en `DRAFT`. En borrador no hay tickets, porque el inventario nace al publicar, así que no hace falta cascada. Un evento publicado se cancela, no se borra
- [ ] **Nombre de la organización:** las páginas de eventos tienen que mostrar `displayName`. Depende de la decisión de [`RFC-004`](../rfc/RFC-004-cross-service-relations.md) (composición, réplica o snapshot)

## Resiliencia de `AuthClient`

- [ ] **Timeouts:** no están configurados. Si el auth-service se cuelga, el create queda esperando
- [ ] **Reintentos:** no hay. Para lecturas como estas se podrían reintentar una vez, el enunciado pide reintentos y timeouts ante fallos de otros servicios

## Decisiones abiertas

- [ ] **RFC-004:** la referencia por id a la organización es provisoria hasta que decida el equipo
- [ ] **`created_by` → `organizer_id`:** el campo apunta a `organizer.user_id`, pero su nombre no lo dice
- [ ] **Headers duplicados:** `X-Organization-Id` es redundante, porque el organizador ya trae su `organizationId`. Se mantiene para validar las dos cosas, pero con JWT (Épica 9) alcanza con el usuario del token
- [ ] **`saleStart` en el pasado:** hoy se acepta si es anterior a `dateTime`. Definir si un borrador puede tener una apertura de venta ya vencida

## Documentación

- [ ] Agregar la sección de eventos (endpoints, reglas, errores) a `eventservice.md`
- [x] Sumar `invalid_event_configuration` y `organizer_not_in_organization` a la tabla de `EventErrorCode` en `error-handling.md`

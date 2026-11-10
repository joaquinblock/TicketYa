Se decidió hacer una máquina de estados para controlar los cambios de estado que puedan provocar problemas en el sistema.

La lista de transiciones de estado de un evento es:

| Desde                   | Hacia                                             |
| ----------------------- | ------------------------------------------------- |
| `DRAFT`                 | `PUBLISHED`                                       |
| `PUBLISHED`             | `SOLD_OUT`, `CANCELLED`, `SUSPENDED`, `FINISHED`  |
| `SOLD_OUT`              | `PUBLISHED`, `CANCELLED`, `SUSPENDED`, `FINISHED` |
| `SUSPENDED`             | `PUBLISHED`, `SOLD_OUT`, `CANCELLED`              |
| `CANCELLED`, `FINISHED` | ninguno (estados finales)                         |

## Explicación cambio de estados
- `DRAFT -> PUBLISHED`: Se pasa de borrador a publicado para comenzar a vender las entradas.
- `PUBLISHED -> SOLD_OUT`: Está publicado y se vendieron todas las entradas.
- `PUBLISHED -> CANCELLED`: El evento se canceló definitivamente.
- `PUBLISHED -> SUSPENDED`: El evento se suspendió por alguna cuestión externa (baja demanda, cuestiones climatológicas, etc.).
- `PUBLISHED -> FINISHED`: El evento se realizó con éxito.
- `SOLD_OUT -> PUBLISHED`: Estaba agotado pero una o más personas pidieron su devolución y se liberaron cupos.
- `SOLD_OUT -> CANCELLED`: El evento se canceló definitivamente. Hay que hacer la devolución de pagos a todos los compradores y avisar vía mail.
- `SOLD_OUT -> SUSPENDED`: Se suspendió temporalmente un evento con entradas agotadas.
- `SOLD_OUT -> FINISHED`: Se concretó con éxito el evento cuyas entradas estaban agotadas.
- `SUSPENDED -> PUBLISHED`: Se suspendió en su momento pero se re-programó y volvió a estar publicado. Hay que hacer devolución al cliente.
- `SUSPENDED -> SOLD_OUT`: Un evento agotado que se suspendió y se reprogramó vuelve a estar agotado. Si solo pudiera volver a `PUBLISHED`, se vendería como si tuviera lugar.
- `SUSPENDED -> CANCELLED`: Se suspendió temporalmente y se canceló definitivamente

## Estados finales
- `CANCELLED` es estado final porque el evento quedó descartado definitivamente y no va a concretarse.
- `FINISHED` es estado final porque el evento ya se realizó

## Excepciones
- **Transición no permitida:** `TicketYaException(INVALID_STATUS_TRANSITION)`, que responde `409` con *"Un evento en X no puede pasar a Y"*.
- **`null` como estado:** `IllegalArgumentException`. Es un bug del código, no un request que haya que rechazar.

### Chequeo de mismo estado
Si se intenta pasar de un estado a el mismo, ejemplo `PUBLISHED` a `PUBLISHED`, la máquina simplemente devuelve ese mismo estado como `next`. Es decir, devuelve `PUBLISHED`.

## Agregar un estado nuevo
Es necesario agregarlo en el `HashMap` junto con los estados a los que puede pasar.

---
## Cómo se usa

- **Solo se cambia con `Event.changeStatus(next, machine)`.** `status` no tiene setter, así que nadie puede saltear la máquina. `EventStatusMachine` es un `@Component` y se inyecta en `EventService`.
- **`EventStatusMachineTest`** recorre todas las combinaciones de estados contra esta tabla. Si se agrega un estado al enum y no a la tabla, el test falla.

| Endpoint | Transición | Quién |
|---|---|---|
| `POST /api/events/{id}/publish` | `DRAFT → PUBLISHED`, con precondiciones (ver `event-service-api.md`) | Organizador de la organización (`EVENT_MANAGE`) |
| `POST /api/events/{id}/cancel` | `PUBLISHED`, `SOLD_OUT` o `SUSPENDED → CANCELLED` | Administrador, o el organizador que creó el evento |
| `POST /api/events/{id}/suspend` | `PUBLISHED` o `SOLD_OUT → SUSPENDED` (guarda cuál era en `status_before_suspension`) | Ídem |
| `POST /api/events/{id}/resume` | `SUSPENDED →` el estado que tenía antes (`PUBLISHED` o `SOLD_OUT`), con fecha nueva opcional | Ídem |

Un borrador no se cancela: se borra con `DELETE /api/events/{id}`. `SOLD_OUT` y `FINISHED` no tienen endpoint público: agotarlo le toca al ticket-service cuando vende el último lugar (US-TKT-13, #55), y finalizarlo, al job de abajo.

## Finalización automática (`FINISHED`)

`FinishedEventsJob` corre cada `ticketya.events.finish-check` (1 minuto) y llama a `EventService.finishEventsThatAlreadyHappened()`:

1. Busca los eventos en `PUBLISHED` o `SOLD_OUT` cuya `date_time` ya pasó.
2. Por cada uno, **primero** le avisa al ticket-service (`PUT /internal/event-sales/{id}` con `FINISHED`) y **después** hace el `UPDATE`.
3. El `UPDATE` filtra por estado y fecha (`WHERE id = ? AND status IN (PUBLISHED, SOLD_OUT) AND date_time <= now`). Correrlo dos veces, o desde dos instancias de event-service a la vez, no rompe nada: la segunda no encuentra filas. No pasa por `changeStatus` porque es un `UPDATE` masivo, pero solo toca los dos estados desde los que la tabla permite ir a `FINISHED`.
4. Si el aviso falla, se loguea y el evento queda como estaba hasta el próximo ciclo, que lo reintenta. Mientras tanto no se vende igual: el ticket-service rechaza reservas de eventos cuya fecha ya pasó (`event_not_on_sale`), aunque su copia diga `PUBLISHED`.

**Zona horaria.** Las fechas se guardan como `LocalDateTime` en hora de Argentina. Los dos servicios toman "ahora" de un `Clock` con zona fija (`ticketya.time-zone: America/Argentina/Buenos_Aires`), no de la zona del sistema operativo, así que una máquina o contenedor en UTC no corre el chequeo 3 horas.


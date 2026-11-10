# event-service: ABM de lugares, sectores, asientos, participantes y eventos

> Épica 3 (#3), primera parte: *"ABM de lugares con sectores numerados (filas/asientos) y no numerados (capacidad)"*.
> El modelo de datos y su porqué están en [`event-service.md`](event-service.md). Este documento cubre cómo se usa la API y qué reglas aplica.

## Endpoints

Todas las rutas empiezan con `/api/venues`. El api-gateway ya las enruta al event-service, así que funcionan igual en `:8080` (gateway) y en `:8082` (directo). Swagger: `/swagger-ui.html` en el servicio, o `/docs/event-service/...` desde el gateway.

| Método | Ruta | Qué hace | OK |
|---|---|---|---|
| `GET` | `/api/venues?city=` | Lista lugares por nombre; `city` es opcional y no distingue mayúsculas | 200 |
| `GET` | `/api/venues/{id}` | Lugar con sus sectores y la capacidad total | 200 |
| `POST` | `/api/venues` | Crea un lugar, sin sectores | 201 |
| `PUT` | `/api/venues/{id}` | Reemplaza nombre, dirección, ciudad y coordenadas | 200 |
| `DELETE` | `/api/venues/{id}` | Borra el lugar con sus sectores y asientos | 204 |
| `GET` | `/api/venues/{venueId}/sectors` | Sectores del lugar | 200 |
| `GET` | `/api/venues/{venueId}/sectors/{sectorId}` | Un sector | 200 |
| `POST` | `/api/venues/{venueId}/sectors` | Crea un sector; si es numerado, puede crear sus filas en el mismo paso | 201 |
| `PUT` | `/api/venues/{venueId}/sectors/{sectorId}` | Cambia el nombre y, si no es numerado, la capacidad | 200 |
| `DELETE` | `/api/venues/{venueId}/sectors/{sectorId}` | Borra el sector con sus asientos | 204 |
| `GET` | `/api/venues/{venueId}/sectors/{sectorId}/seats` | Asientos ordenados por fila y número | 200 |
| `POST` | `/api/venues/{venueId}/sectors/{sectorId}/seats` | Agrega filas completas de asientos | 201 |
| `DELETE` | `/api/venues/{venueId}/sectors/{sectorId}/seats/{seatId}` | Borra un asiento | 204 |

Los sectores y asientos se anidan bajo su lugar: un sector pedido con un `venueId` que no es el suyo da `404 sector_not_found`, aunque exista en otro lugar.

## Flujo típico

```http
POST /api/venues
{ "name": "Teatro de la Sierra", "address": "Calle Pinto 650", "city": "Tandil",
  "latitude": -37.3217, "longitude": -59.1332 }

POST /api/venues/2/sectors                      ← numerado, con sus filas
{ "name": "Platea", "numbered": true,
  "rows": [ { "row": "A", "seats": 10 }, { "row": "B", "seats": 10 } ] }

POST /api/venues/2/sectors                      ← no numerado
{ "name": "Campo", "numbered": false, "capacity": 500 }

POST /api/venues/2/sectors/3/seats              ← más filas después
{ "rows": [ { "row": "C", "seats": 12 } ] }

GET /api/venues/2
→ { ..., "capacity": 532, "sectors": [
      { "id": 4, "name": "Campo",  "numbered": false, "capacity": 500 },
      { "id": 3, "name": "Platea", "numbered": true,  "capacity": 32 } ] }
```

## Reglas

### Tipo de sector

| | Numerado | No numerado |
|---|---|---|
| `capacity` en el request | Prohibido | Obligatorio |
| `rows` al crear | Opcional | Prohibido |
| Agregar asientos (`/seats`) | Sí | `409 sector_not_numbered` |
| `capacity` en la respuesta | Cantidad de asientos | La cargada |

Si no se respeta, la respuesta es `400 invalid_sector_configuration`. El tipo **no se puede cambiar** después de crear el sector: pasar de numerado a no numerado dejaría asientos huérfanos. Si hace falta, se borra el sector y se crea otro.

La capacidad no se guarda en los sectores numerados ni en el lugar: se calcula (ver *Capacidades* en `event-service.md`).

### Filas y asientos

- Cada fila se crea con asientos numerados del **1 a `seats`**. Máximo 100 filas por pedido y 200 asientos por fila.
- La fila se guarda **en mayúsculas y sin espacios** (`" a "` → `"A"`). La collation por defecto de MySQL no distingue mayúsculas, así que `a` y `A` chocarían igual contra el unique `(sector_id, seat_row, number)`.
- No se puede agregar una fila que ya existe en el sector, ni repetir una fila en el mismo pedido: `409 seat_row_duplicated`, con las filas en `detail.rows`. Para agrandar una fila existente, se borra y se vuelve a crear.

### Nombres

El nombre del sector es único dentro de su lugar, sin distinguir mayúsculas: `409 sector_name_duplicated`. Los nombres de lugar no son únicos (hay "Teatro Municipal" en muchas ciudades).

### Qué se puede cambiar cuando hay eventos

| Acción | Se bloquea si… | Error |
|---|---|---|
| Borrar un lugar | Tiene algún evento, en cualquier estado | `409 venue_in_use` |
| Borrar un sector | Algún evento, en cualquier estado, lo vende (`event_sector`) | `409 sector_in_use` |
| Cambiar la capacidad, agregar o borrar asientos | Lo vende un evento que **ya salió de `DRAFT`** | `409 sector_in_use` |
| Renombrar un sector o modificar los datos del lugar | Nunca | — |

**Por qué la estructura se congela al salir de `DRAFT`:** desde la publicación, el ticket-service arma su inventario a partir del sector (un `event_seat` por asiento o un `event_sector_stock` con la capacidad). Si el catálogo cambia después, los dos servicios quedan desalineados: se venderían asientos que ya no existen o cupos que nadie contó. Mientras el evento es borrador todavía no hay inventario, así que se puede ajustar libremente. Los eventos `FINISHED` y `CANCELLED` también bloquean, porque sus reportes de ocupación dependen de esa capacidad.

**Por qué borrar se bloquea siempre:** `event_sector` y `event` tienen FK hacia `sector` y `venue`, y además las entradas vendidas (`ticket.event_sector_id`, en el ticket-service) apuntan a esos sectores.

## Errores

Todos usan el `ErrorDto` común (ver [`error-handling.md`](error-handling.md)).

| Código | Status | Cuándo |
|---|---|---|
| `validation_error` | 400 | Falta un campo, latitud fuera de [-90, 90], `seats` < 1, etc. |
| `invalid_sector_configuration` | 400 | `capacity` o `rows` no corresponden al tipo de sector |
| `venue_not_found` / `sector_not_found` / `seat_not_found` | 404 | No existe, o no pertenece al lugar o sector de la ruta |
| `venue_in_use` / `sector_in_use` | 409 | Ver la tabla anterior |
| `sector_name_duplicated` | 409 | Ya hay un sector con ese nombre en el lugar |
| `sector_not_numbered` | 409 | Se intentó agregar asientos a un sector no numerado |
| `seat_row_duplicated` | 409 | La fila ya existe o está repetida en el pedido |

## Código

| Capa | Clases |
|---|---|
| `controller` | `VenueController`, `SectorController`, `SeatController` |
| `service` | `VenueService`, `SectorService` |
| `dto` | `VenueRequest`, `VenueSummaryResponse`, `VenueDetailResponse`, `SectorCreateRequest`, `SectorUpdateRequest`, `SectorResponse`, `SeatBatchRequest`, `SeatRowRequest`, `SeatResponse` |
| `mapper` | `VenueMapper`, `SectorMapper` |
| `repository` | Queries nuevas en `VenueRepository`, `SectorRepository`, `SeatRepository`, `EventRepository` y `EventSectorRepository`; `SectorSeatCount` (proyección del conteo de asientos) |

- **Hay un solo service para sectores y asientos.** Un asiento no existe fuera de su sector y las reglas (tipo, bloqueo por eventos) son las del sector, así que `SectorService` maneja los dos como un agregado. Separarlos en dos services generaba una dependencia circular, porque crear un sector crea asientos y crear asientos necesita validar el sector.
- **`VenueService` depende de `SectorService` y no al revés.** Lo usa para listar los sectores del detalle y para borrar todo al eliminar un lugar.
- **Los borrados en cascada son `DELETE` en bloque (`@Modifying @Query`).** No cargan cada asiento en memoria: un estadio puede tener miles.
- **El listado de sectores cuenta los asientos con una sola query agrupada** (`countBySectorOfVenue`), sin una query por sector.

## Pendiente

| Tema | Detalle |
|---|---|
| Autorización | La Entrega 1 no tiene autenticación: cualquiera puede usar estos endpoints. Con la Épica 9 deberían quedar solo para administradores, u organizadores según se decida |
| Mover o renumerar asientos | No hay endpoint para cambiar fila o número de un asiento: se borra y se crea. No lo pide el enunciado |
| Capacidad habilitada | Sigue sin modelarse `venue.licensed_capacity` (ver `event-service.md`) |

## Participantes

Artistas, equipos y disertantes (`ARTIST`, `TEAM`, `SPEAKER`). El gateway enruta `/api/participants/**` al event-service.

| Método | Ruta | Qué hace | OK |
|---|---|---|---|
| `GET` | `/api/participants?type=&name=` | Lista por nombre. `type` y `name` (parte del nombre, sin distinguir mayúsculas) son opcionales | 200 |
| `GET` | `/api/participants/{id}` | Un participante | 200 |
| `POST` | `/api/participants` | Crea un participante | 201 |
| `PUT` | `/api/participants/{id}` | Reemplaza nombre y tipo | 200 |
| `DELETE` | `/api/participants/{id}` | Lo borra definitivamente | 204 |
| `PUT` | `/api/events/{id}/participants` | Reemplaza los participantes de un evento en `DRAFT` | 200 |

```http
POST /api/participants
{ "name": "Trío Costero", "type": "ARTIST" }

PUT /api/events/10/participants              X-User-Id: 2
{ "participantIds": [1, 5] }
→ 200 [ { "id": 5, "name": "DJ Marea", "type": "ARTIST" }, { "id": 1, "name": "Los Serranos", ... } ]
```

### Reglas

- **No hay dos participantes con el mismo nombre y tipo**, sin distinguir mayúsculas: `409 participant_duplicated`. Un artista y un equipo sí pueden llamarse igual. Un participante repetido partiría en dos los favoritos de los clientes (Entrega 2).
- **Borrado físico, solo si no está en ningún evento**, en cualquier estado: si no, `409 participant_in_use`. Es la misma regla que `venue_in_use`.
- **Modificar un participante** cambia lo que muestran todos los eventos que lo tienen.
- **Asignar participantes a un evento:**
  - reemplaza la lista completa; los ids repetidos cuentan una vez y `[]` los quita a todos;
  - solo en `DRAFT` (`409 event_not_editable`), con `EVENT_MANAGE` (`403 forbidden`) y por un organizador de la organización del evento (`403 organizer_not_in_organization`);
  - si falta algún id, `404 participant_not_found` con los ids que faltan en `detail.missingIds`, y no se cambia nada.

**Datos de prueba:** *Sofía Lanza* no está en ningún evento, así que se puede borrar (`204`). Cualquier otro participante da `409`. El evento `10` es un borrador sin participantes.

| Tema | Detalle |
|---|---|
| Permisos del ABM | Igual que `/api/venues`, no tiene `@RequiresPermission`: falta definir qué permiso cubre el catálogo (ver `permissions.md`) |
| Eventos publicados | Cambiar la grilla de un evento publicado queda para cuando haya notificaciones de cambios (Entrega 2) |

## Eventos: ver, editar, borrar, publicar, suspender y cancelar

El alta (`POST /api/events`) está en `event-creation-pending.md`. Las transiciones de estado siguen [`event-state-machine.md`](event-state-machine.md).

| Método | Ruta | Qué hace | Permiso | OK |
|---|---|---|---|---|
| `GET` | `/api/events` | Búsqueda: `PUBLISHED` y `SOLD_OUT` futuros, con filtros, orden y páginas (ver [`event-search.md`](event-search.md)) | Público | 200 |
| `GET` | `/api/events/{id}` | El evento con su lugar, los sectores que vende (precio y capacidad), los participantes y la capacidad total. Un `DRAFT` solo lo ve un organizador de su organización; para el resto da 404 | Público | 200 |
| `PUT` | `/api/events/{id}` | Reemplaza los datos del alta. Usa el mismo body que `POST` | `EVENT_MANAGE` | 200 |
| `PUT` | `/api/events/{id}/sectors` | Reemplaza los sectores que vende y sus precios | `EVENT_MANAGE` | 200 |
| `DELETE` | `/api/events/{id}` | Borra un borrador con sus sectores y participantes | `EVENT_MANAGE` | 204 |
| `POST` | `/api/events/{id}/publish` | `DRAFT → PUBLISHED` | `EVENT_MANAGE` | 200 |
| `POST` | `/api/events/{id}/cancel` | `PUBLISHED`, `SOLD_OUT` o `SUSPENDED → CANCELLED` | `EVENT_ADMIN`, o `EVENT_MANAGE` si es el organizador que lo creó | 200 |
| `POST` | `/api/events/{id}/suspend` | `PUBLISHED` o `SOLD_OUT → SUSPENDED` | `EVENT_ADMIN`, o `EVENT_MANAGE` si es el organizador que lo creó | 200 |
| `POST` | `/api/events/{id}/resume` | `SUSPENDED →` el estado anterior. Body opcional `{ "dateTime" }` para reprogramarlo | `EVENT_ADMIN`, o `EVENT_MANAGE` si es el organizador que lo creó | 200 |

### Reglas

- **Editar, borrar y publicar** son solo para un organizador de la organización del evento: si no, `403 organizer_not_in_organization`. Cancelar lo hace cualquier administrador, sobre cualquier evento, como dice la consigna 3.
- **Sectores y precios:** `{ "sectors": [{ "sectorId": 3, "price": 20000.00 }] }` reemplaza la lista completa, y `[]` los quita a todos. Solo en `DRAFT`. Además:
  - cada sector tiene que ser del lugar del evento: si no, `404 sector_not_found` con los que no lo son en `detail.missingIds`;
  - un sector va una sola vez, porque tendría dos precios: `400 invalid_event_configuration` con `detail.repeatedSectorIds`;
  - el precio es mayor a 0 y tiene hasta dos decimales (`400 validation_error`), y se guarda siempre con dos. Se asume que está en pesos;
  - **se actualiza en el lugar:** un sector que sigue conserva su fila de `event_sector` (y su id) y solo cambia el precio; los que se van se borran y los nuevos se insertan. Así el id queda estable si algún día se permite cambiar precios de un evento publicado, porque el ticket-service lo referencia.
- **Eventos gratuitos:** no están soportados. El precio tiene que ser mayor a 0, y el payment-service tampoco cobra montos de 0. Si hicieran falta, habría que aceptar `price = 0` acá y que el ticket-service emita la entrada sin cobrar.
- **Editar, borrar y cambiar sectores, solo en `DRAFT`:** si no, `409 event_not_editable`. Un evento publicado se cancela, no se borra.
- **Si al editar cambia el lugar,** se borran los sectores que vendía, porque eran del lugar anterior. La organización, el estado, los sectores y los participantes no se cambian con `PUT`.
- **Publicar** exige que esté en `DRAFT` (si no, `409 invalid_status_transition`) y que cumpla todas estas condiciones. Si falla alguna, responde `409 event_not_publishable` con todas en `detail.problems`:
  - al menos un sector a la venta;
  - ningún sector numerado sin asientos, porque se vendería un sector de capacidad 0;
  - la fecha todavía en el futuro;
  - si es de alta demanda, un `saleStart`, y siempre anterior a la fecha.
- **Suspender, reanudar y cancelar:** el admin puede con cualquier evento. Un organizador, solo con los que creó, y mientras siga siendo organizador de su organización. Otro organizador, aunque sea de la misma organización, recibe `403 not_event_creator`. Ver *Varios permisos y quién llama* en `permissions.md`.
- **Cancelar o suspender un borrador** da `409 invalid_status_transition`: un borrador se borra. Cancelar uno ya cancelado, o suspender uno ya suspendido, no cambia nada.
- **Reanudar vuelve al estado anterior:** al suspender se guarda `PUBLISHED` o `SOLD_OUT` en `status_before_suspension`. Así un evento agotado no reabre la venta como si tuviera lugar. Un evento suspendido sin estado anterior guardado (el 7 del seeder antes de este cambio) vuelve a `PUBLISHED`.
- **Reprogramar:** `POST /resume` con `{ "dateTime": "2027-02-20T21:00:00" }` cambia la fecha. Tiene que ser futura (`400 validation_error`) y posterior a `saleStart` (`400 invalid_event_configuration`). Es la única forma de cambiar la fecha de un evento que no es borrador.
- **La capacidad** es la suma de los sectores que vende: los asientos de los numerados y `capacity` de los no numerados.
- **Las llamadas al auth-service van fuera de la transacción,** como en el alta: solo la escritura corre dentro de una (`TransactionTemplate`). Así una espera de auth no retiene una conexión de la base.

```http
POST /api/events/10/publish                  X-User-Id: 2
→ 409 { "error": "event_not_publishable", "detail": { "problems": ["No tiene sectores a la venta"] } }
```

| Pendiente | Detalle |
|---|---|
| Reintegros | Cancelar ya frena la venta en el ticket-service, pero todavía no reintegra las entradas vendidas (US-TKT-18, #61) |
| Avisar a los compradores | Suspender, reprogramar y cancelar no notifican a nadie (Entrega 2) |
| Otras transiciones | Agotar (`SOLD_OUT`) y finalizar (`FINISHED`) todavía no tienen endpoint. Agotar le toca al ticket-service (US-TKT-13, #55) |

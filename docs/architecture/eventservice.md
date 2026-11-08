# event-service: ABM de lugares, sectores y asientos

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

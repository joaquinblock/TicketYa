# Búsqueda de eventos

> Consigna 9: *"búsquedas por múltiples criterios: tipo de evento, organizador, participante, ciudad y rango de fechas, con ordenamiento por fecha, precio o relevancia"*. Endpoint: `GET /api/events`, público y paginado.

## Qué eventos aparecen

**Solo `PUBLISHED` y `SOLD_OUT` con fecha futura.** No hay un filtro `?status=`: el resto de los estados no aparece nunca en la búsqueda.

| Estado | ¿Aparece? | Por qué |
|---|---|---|
| `PUBLISHED` | Sí | Es lo que se puede comprar |
| `SOLD_OUT` | Sí | Puede volver a tener lugar: una devolución aprobada libera el asiento (consigna 7), o la organización puede abrir otra tanda. El front lo muestra como *"Agotado: si alguien devuelve su entrada, vuelve a estar disponible"* |
| `SUSPENDED` | **No** | No se puede comprar, y no se sabe si va a volver ni cuándo. Ver abajo |
| `CANCELLED`, `FINISHED` | No | Ya no se van a vender |
| `DRAFT` | No | Es privado de su organización |

### Por qué la consigna no lo resuelve

La consigna no dice qué estados devuelve la búsqueda. Lo que sí dice:

- Los clientes *"**buscan eventos y compran entradas**"*: la búsqueda está pensada para comprar.
- Ante la cancelación o el cambio de un evento, *"se notifica a los compradores"* (consignas 7 y 11). Quien compró se entera por la notificación, no buscando.

Por eso la regla es una sola: **la búsqueda muestra lo que se puede comprar, o lo que podría volver a tener lugar pronto.**

### Por qué no se muestra `SUSPENDED`

- **No se puede comprar.** Un cliente que lo encuentra no puede hacer nada con él.
- **No se sabe si vuelve.** Puede reanudarse, con la misma fecha o con otra, o cancelarse. Mostrarlo obliga al front a explicar *"suspendido, sin venta, puede volver"*, para un caso que el cliente no puede resolver.
- **El que compró no lo necesita en la búsqueda.** Ve el estado en el detalle (`GET /api/events/{id}`), al que llega desde *mis compras* (US-TKT-12) o desde la notificación de la suspensión.
- **Cuando se reanuda, vuelve a aparecer solo,** porque pasa a `PUBLISHED` o `SOLD_OUT`.

### Por qué sí se muestra `SOLD_OUT`

- **Puede volver a `PUBLISHED`:** una devolución libera el lugar, y la máquina de estados ya prevé `SOLD_OUT → PUBLISHED`.
- **Para el cliente es información útil:** sabe que el evento existe y que puede volver a intentar.

### Estas decisiones se pueden revertir

Ninguna de las dos cambia el modelo ni la API, así que revertirlas es barato:

- **Mostrar `SUSPENDED`:** agregarlo a `EventSearchService.VISIBLE` y que el front lo muestre como *"suspendido, sin venta"*.
- **Ocultar `SOLD_OUT`:** sacarlo de `EventSearchService.VISIBLE`.
- **Dejar que el cliente elija:** agregar un `?status=` que solo acepte estados públicos (nunca `DRAFT`). No se agregó por YAGNI: el front todavía no lo necesita.

Si se revierte alguna, hay que actualizar este doc, el `@Operation` de `EventController.search` y `EventSearchServiceTest`, que verifica qué estados aparecen.

## Filtros

Todos son opcionales y se combinan con *y*.

| Parámetro | Ejemplo | Cómo filtra |
|---|---|---|
| `q` | `serranos` | Texto libre: parte del nombre, de la descripción o del nombre de un participante, sin distinguir mayúsculas |
| `type` | `CONCERT` | Tipo de evento, exacto |
| `city` | `cordoba` | Ciudad del lugar, completa. No distingue mayúsculas ni acentos: la collation de MySQL los ignora, así que `cordoba` encuentra *Córdoba* |
| `participant` | `serranos` | Parte del nombre de un artista, equipo o disertante |
| `organizationId` | `1` | Organización que publica el evento |
| `from`, `to` | `2026-12-01` | Días, los dos inclusive. `from` posterior a `to` da `400 invalid_parameter` |
| `sort` | `PRICE` | `DATE` (por defecto), `PRICE` o `RELEVANCE` |
| `page`, `size` | `0`, `20` | Página desde 0. `size` va de 1 a 50, y por defecto es 20 |

- **El organizador se filtra por `organizationId`,** no por nombre. Por el [RFC-004](../rfc/RFC-004-cross-service-relations.md), el event-service solo guarda el id. Por eso la respuesta tampoco trae el nombre de la organización.
- **El participante se filtra por nombre,** porque es lo que escribe un cliente. No hay `participantId` (YAGNI): se agrega cuando el front lo elija de una lista o lo pida el recommendation-service.
- **`%` y `_` se toman como texto,** no como comodines.

## Orden

En los tres, a igual valor va primero el evento más próximo, y después el de menor id.

| `sort` | Ordena por |
|---|---|
| `DATE` | La fecha, del más próximo al más lejano |
| `PRICE` | El **precio desde**: el del sector más barato, de menor a mayor |
| `RELEVANCE` | Con `q`: primero los que coinciden en el **nombre**, después en un **participante** y después en la **descripción**. Sin `q`, es igual que `DATE` |

**La consigna no define "relevancia".** Sin un texto de búsqueda no hay contra qué medirla, y la popularidad (entradas vendidas) vive en el ticket-service. Por eso se asocia a `q`, que es lo que mandaría la barra de búsqueda del front.

## Respuesta

```http
GET /api/events?q=serran&sort=RELEVANCE
→ 200 {
  "content": [
    { "id": 11, "name": "Peña folklórica serrana", "type": "CONCERT", "status": "PUBLISHED",
      "dateTime": "2026-11-28T21:00:00", "highDemand": false, "organizationId": 2,
      "venue": { "id": 2, "name": "Teatro de la Sierra", "city": "Tandil", ... },
      "priceFrom": 7000.00, "participants": ["Ballet Folklórico Serrano"] },
    ...
  ],
  "page": 0, "size": 20, "totalElements": 3, "totalPages": 1
}
```

La página es un record propio (`EventPageResponse`), y no el `Page` de Spring Data, para que el JSON no dependa de cómo serializa Spring sus clases internas.

## Ver un evento por id

`GET /api/events/{id}` muestra **cualquier estado salvo `DRAFT`**. Por eso un evento cancelado, suspendido o finalizado se sigue pudiendo ver aunque no aparezca en la búsqueda.

- **Un borrador solo lo ve un organizador de su organización,** con `X-User-Id`. Lo necesita el front para cargar la pantalla de edición.
- **Para cualquier otro, un borrador da `404 event_not_found`,** igual que un id que no existe. Así el id no revela que el evento se está preparando.
- **Solo se consulta al auth-service cuando el evento es un borrador.** Los demás se responden sin llamar a nadie.

## Implementación

- **`EventSearchService`, con la Criteria API de JPA.** Cada filtro que viene agrega su condición. La base resuelve el filtrado, el orden y la paginación, no la memoria.
- **Cuatro consultas por página:**
  1. el conteo;
  2. la página, con el lugar;
  3. los participantes de esos eventos (`findWithParticipantsByIdIn`);
  4. su precio desde (`findPriceFromByEventIds`).

  Cargarlos por evento sería una consulta por cada resultado.
- **Tests:** `EventSearchServiceTest` corre contra MySQL sobre los datos de `EventSeeder`, y cubre cada filtro, cada orden, la paginación y los comodines.

## Pendiente

| Tema | Detalle |
|---|---|
| Nombre de la organización | La respuesta trae `organizationId`, no `publicName`. Para mostrarlo hace falta composición o snapshot (RFC-004, opciones B o D) |
| Ordenar por distancia | El lugar tiene latitud y longitud, pero la consigna no lo pide. Se agrega si el front lo necesita |
| Caché | El catálogo se lee mucho y se escribe poco (ver `microservices.md`). La caché es de la Entrega 2 |

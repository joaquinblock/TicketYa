# 0009: Cache del catálogo con Redis

- **Fecha:** 2026-11-23
- **Estado:** aceptada

## Contexto
La consigna pide definir qué datos se cachean, dónde, con qué TTL y cómo se invalidan, y justificar qué datos no deben cachearse. En una apertura masiva, miles de personas abren la cartelera y el mismo evento en los mismos minutos. queue-service, además, pide el detalle del evento en cada ingreso a la fila.

Restricciones:

- **El catálogo se lee mucho más de lo que cambia** ([`microservices.md`](../architecture/microservices.md)) y tolera unos segundos de atraso, porque la compra la valida el ticket-service con su base.
- **La disponibilidad no tolera atraso.** Vive en el ticket-service, con consistencia fuerte ([`event-service.md`](../architecture/event-service.md)). Un asiento cacheado se vendería dos veces.
- **Un borrador es privado.** Si se cacheara con la misma clave que la vista pública, se lo podría ver cualquiera.

El análisis completo está en el doc [*Cache en TicketYa*](https://claude.ai/artifact/ThtABBhLWqahmTqR1QXF5w).

## Decisión
La vista pública del catálogo se cachea en un **Redis propio del event-service**, con la abstracción de cache de Spring (`@Cacheable` / `@CacheEvict`) y un TTL por cache. Se usa *cache-aside*: un cambio no actualiza la cache, borra la clave, y la próxima lectura la recarga desde la base.

| Cache | Qué guarda | TTL | Se borra cuando |
|---|---|---|---|
| `event` | El detalle de un evento que no es borrador (`GET /api/events/{id}`) | 5 min | Se publica, suspende, reanuda o cancela; se agota o vuelve a la venta; el job lo da por terminado |
| `events-upcoming` | La primera página de la búsqueda sin filtros | 60 s | Cualquiera de esos cambios en cualquier evento |
| `venue` | El lugar con sus sectores (`GET /api/venues/{id}`) | 1 h | Se edita o borra el lugar, o se crea, edita o borra uno de sus sectores o asientos |
| `participant` | El participante (`GET /api/participants/{id}`) | 10 min | Se edita o borra |

**No se cachean:**

- **La disponibilidad, las órdenes y los pagos.** Están en el ticket-service, y ahí un dato viejo es una doble venta o un cobro mal informado.
- **Los borradores.** `unless` los deja afuera, así que todo lo cacheado se ve igual para cualquiera que lo pida.
- **Las búsquedas con filtros.** Tipo, ciudad, fechas, orden y página dan miles de combinaciones que casi no se repiten, y no hay forma de saber cuáles incluyen un evento que cambió.

**Cómo se borra:**

- Las anotaciones están en los métodos de los services. Las combinaciones están en `CatalogCaches`:
  - `@EvictsEvent` borra el detalle y la lista inicial.
  - `@EvictsAllEvents` vacía las dos caches de eventos.
- **Primero el commit, después el borrado.** El cache corre por afuera de la transacción (`@EnableCaching(order = HIGHEST_PRECEDENCE)`). Si se borrara antes del commit, una lectura en el medio volvería a guardar el valor viejo hasta el TTL.
  - La excepción es el job de eventos terminados: el borrado está en el `UPDATE` del repository, un instante antes del commit.
- **Escrituras inmediatas.** Por defecto, Spring Data Redis 4 escribe en la cache de forma asíncrona. Así, una lectura anterior a un cambio podía llegar a Redis después del borrado y quedar hasta el TTL. Lo detectó `CatalogCacheRedisIntegrationTest`, y por eso se configuró `immediateWrites()`.
- **Si cambia un lugar, un sector o un participante, se vacían todas las caches de eventos.** El doc proponía buscar en `event_participant` los eventos afectados y borrar solo esos. Se simplificó porque esos cambios son raros, y vaciar todo no puede fallar por olvidarse un evento.
- **Al arrancar, el event-service vacía sus caches.** La base se recrea y se vuelve a cargar con el seeder en cada arranque, pero Redis sigue vivo.

## Alternativas consideradas
- **En memoria de cada instancia (Caffeine).** Con varias instancias, un borrado solo limpia la instancia que atendió el cambio, y las demás muestran el dato viejo hasta el TTL.
- **En el gateway.** No sabe cuándo cambia un evento, así que solo podría vencer por TTL. Además pondría lógica de negocio en el punto de entrada.
- **`Cache-Control` para el cliente.** Lo que guarda el cliente no se puede invalidar. Puede sumarse con un `max-age` corto, pero no reemplaza a la cache del servicio.
- **El mismo Redis de queue-service.** Al llenarse, el desalojo podría sacar una posición de la fila virtual, y alguien perdería su turno. Un contenedor de 64 MB separa lo que se puede perder (la cache) de lo que no (la fila).

## Consecuencias
- **Un componente más que puede caerse.** Si Redis no responde, `LoggingCacheErrorHandler` deja un warn y la lectura va a la base: es más lento, pero responde.
  - Los timeouts de Redis son de 500 ms para no colgar el request.
  - El health de Redis está apagado, para que una cache caída no marque el servicio como caído.
- **Lo prueba `CatalogWithoutRedisMySqlIntegrationTest`.**
- **Avalancha de lecturas al vencer una clave (*stampede*).** La lista inicial, los lugares y los participantes usan `sync = true`: en cada instancia, uno recalcula y los demás esperan.
  - El detalle no puede, porque Spring no permite `sync` junto con `unless`.
  - En una apertura masiva, el detalle se carga antes de que abra la venta, cuando la gente entra a mirar. Si alguna vez pesa, el detalle se puede separar del chequeo de borrador.
- **El sistema completo pasa a 18 contenedores.** El `event-redis` usa `maxmemory 48mb` con `allkeys-lru` y no guarda nada en disco.
- **Quien agregue una escritura al catálogo tiene que anotarla.** `CatalogCacheRedisIntegrationTest` muestra el patrón: cambiar un dato por SQL demuestra que la lectura salió de la cache, y cambiarlo con el service demuestra que se borró.
- **Los TTL se ajustan con la prueba de carga.** Están en `ticketya.cache.*`.

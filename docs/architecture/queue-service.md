# queue-service

Implementa US-QUE-01 (#147): ingreso y consulta de la fila virtual para eventos de alta demanda. El contrato HTTP está en [`endpoints.md`](endpoints.md); los criterios completos, en [`../stories/147-fila-virtual-alta-demanda.md`](../stories/147-fila-virtual-alta-demanda.md).

## Ingreso y validación del evento

`POST /api/queue/{eventId}/join` consulta `GET /api/events/{eventId}` antes de escribir en Redis. Si el evento no existe, no es de alta demanda o la venta todavía no abrió, el ingreso no se crea. Un fallo de conexión o timeout se convierte en `503 service_unavailable`; la conexión tiene timeout de 1 s y la lectura de 10 s.

El endpoint de ingreso y `GET /api/queue/{eventId}/me` requieren `TICKET_PURCHASE`. Ambos usan el `Caller` que construye `common` a partir de los headers confiables del api-gateway. No existe una operación para salir voluntariamente.

## Redis, orden y tandas

Se comparte una instancia Redis entre las instancias de queue-service. Por evento se usan:

| Clave | Uso |
|---|---|
| `queue:{eventId}:arrivals` | Sorted set de ingresos, ordenado por secuencia creciente |
| `queue:{eventId}:sequence` | Secuencia monotónica para nuevos ingresos y generaciones |
| `queue:{eventId}:current` | Usuario a ingreso vigente |
| `queue:{eventId}:status` | Estado por generación: `WAITING`, `ENABLED` o `EXPIRED` |
| `queue:{eventId}:enabled-up-to` | Cursor de entradas ya habilitadas |
| `queue:{eventId}:enabled-at` / `enabled-until` | Inicio y vencimiento inmutables de cada turno |
| `queue:{eventId}:queue-token` | JWT de turno emitido para cada generación habilitada |
| `queue:{eventId}:next-promotion` | Instante en que puede avanzar la siguiente tanda |
| `queue:active-events` | Eventos que el job debe revisar |

El ingreso es un script Lua: resuelve de forma atómica el reintento/concurrencia, la generación vigente, la posición y el registro del evento para el job. Un scheduler revisa los eventos activos cada segundo. Otro script Lua comprueba el vencimiento del evento, habilita hasta `batch-size` personas y mueve el cursor. La siguiente tanda queda programada, como mínimo, `batch-interval` después; si el job se retrasa, no intenta ponerse al día liberando tandas juntas.

La posición visible de quien espera es `ZRANK + 1 - enabledUpTo`. Así, cuando se habilitan 100 personas, la siguiente queda en posición 1. Los reingresos después de `EXPIRED` agregan una nueva generación al final; las generaciones previas permanecen en el sorted set para no alterar el cursor. El vencimiento se materializa cuando el cliente consulta o reingresa. La fila no se cierra por inventario ni ofrece baja voluntaria.

El conjunto global `queue:active-events` y los scripts que lo actualizan asumen Redis standalone, como el Redis 7.4 del compose del servicio. Escalar queue-service a varias instancias sí está cubierto; Redis Cluster requeriría particionar el registro activo para que cada script opere en un único hash slot.

## Turno firmado

Al consultar una entrada `ENABLED`, queue-service devuelve un JWT RS256. Sus claims son:

| Claim | Valor |
|---|---|
| `iss` | `ticketya-queue` (configurable) |
| `aud` | `ticket-service` (configurable) |
| `sub` | ID del cliente |
| `eventId` | ID del evento |
| `iat` | Instante en que se habilitó la tanda |
| `enabledUntil` y `exp` | Fin fijo de la ventana, en segundos Unix para el claim y como expiración JWT estándar |
| `jti` | ID de la generación de la entrada |

El token se firma con una clave RSA propia del queue-service, distinta de la clave de autenticación de auth-service. `GET /.well-known/jwks.json` publica solo la clave pública y su `kid`; ticket-service puede verificar firma, emisor, audiencia, cliente, evento y vencimiento sin una llamada por compra. El endpoint se consume directo entre servicios, no por el gateway. US-TKT-21 (#64) implementa la validación del turno en ticket-service y queda fuera de esta historia.

Configurar `QUEUE_JWT_PRIVATE_KEY_LOCATION` con una clave privada PKCS#8 montada como secreto en entornos persistentes. Si no está definida, se genera una clave efímera para desarrollo y los turnos dejan de verificarse tras reiniciar queue-service. No compartir la clave privada de auth-service. La rotación de claves debe considerar que los turnos emitidos siguen vigentes hasta 10 minutos.

## Configuración

| Variable | Default | Uso |
|---|---:|---|
| `QUEUE_BATCH_SIZE` | `100` | Personas habilitadas por tanda |
| `QUEUE_BATCH_INTERVAL` | `30s` | Tiempo mínimo entre tandas |
| `QUEUE_ENABLED_WINDOW` | `10m` | Ventana fija para reservar |
| `QUEUE_JWT_ISSUER` | `ticketya-queue` | Emisor del JWT de turno |
| `QUEUE_JWT_AUDIENCE` | `ticket-service` | Audiencia del JWT de turno |
| `QUEUE_JWT_PRIVATE_KEY_LOCATION` | vacío | Ruta del PEM PKCS#8 privado |
| `EVENT_SERVICE_URL` | `http://localhost:8082` | Base URL del event-service |

La firma, los claims y el descubrimiento de la clave pública se deciden en [ADR-0005](../adr/0005-jwt-turno-fila-virtual.md).

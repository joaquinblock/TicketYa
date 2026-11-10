# Concurrencia e idempotencia

> **Enunciado:** "El mecanismo que impide la doble venta debe funcionar aun con varias instancias del mismo microservicio ejecutándose a la vez. Debe explicarse y probarse el mecanismo elegido (bloqueos, control de versiones, restricciones de unicidad, claves de idempotencia, etc.)."

## Cómo lo definimos

La condición de varias instancias descarta cualquier control en memoria, así que **la decisión final siempre la toma la base de datos**. Hay dos problemas distintos, y cada uno tiene su capa:

| Capa | Problema | Ejemplo |
|---|---|---|
| **Idempotencia** | El mismo cliente repite un pedido | Doble clic, o un reintento porque se cortó la conexión |
| **Datos** | Clientes distintos compiten por el mismo lugar | Dos personas compran el último asiento en el mismo instante |

La primera evita que una operación se aplique dos veces. La segunda evita que dos operaciones distintas se queden con lo mismo.

## Capa de idempotencia

El cliente genera una clave por cada intento y la repite en los reintentos de ese intento. Viaja en el cuerpo del request.

| Operación | Dónde se guarda | Qué recibe un reintento |
|---|---|---|
| Reservar | `orders.idempotency_key`, UNIQUE | La orden existente, sin reservar de nuevo |
| Cobrar | `payment.idempotency_key`, UNIQUE, también enviada a la pasarela | El resultado del primer intento, sin cobrar de nuevo |
| Reintegrar | `refund.ticket_id`, UNIQUE | El reintegro existente |

La clave se guarda en la misma fila que la operación, y no en una tabla aparte. Así las dos se confirman en la misma transacción, y no puede quedar una clave registrada sin su operación, ni al revés.

Si dos reintentos llegan a la vez, el UNIQUE hace que solo uno inserte. El otro falla y lee lo que guardó el primero.

## Capa de datos

| Riesgo | Mecanismo | Qué pasa con el perdedor |
|---|---|---|
| Dos compras del mismo asiento | Bloqueo optimista: `@Version` en `event_seat` | Falla con conflicto y no se vende |
| Dos compras del último lugar de un sector | `UPDATE` atómico sobre `event_sector_stock`, con la condición `taken + :places <= capacity` y un `CHECK` | Actualiza 0 filas: no hay lugar |
| Una orden termina por dos caminos a la vez | `UPDATE orders ... WHERE status = 'PENDING'` | Actualiza 0 filas y no modifica nada |
| Dos intentos de pago con medios distintos | `UPDATE` condicional sobre `orders.payment_attempt_key` | 409 `payment_in_progress` |
| Dos pagos activos en una orden | `payment.active_order_id`, columna generada y UNIQUE | 409 `data_conflict` |
| Reintegros simultáneos que superan lo cobrado | Bloqueo pesimista de la fila de `payment` | Se rechaza con `payment_not_refundable` |
| Dos solicitudes de devolución pendientes | `return_request.pending_ticket_id`, columna generada y UNIQUE | Conflicto |

Las columnas generadas reemplazan a los índices parciales, que MySQL no tiene. Valen el ID mientras el registro está activo y `NULL` cuando no, y el UNIQUE ignora los `NULL`.

Los jobs programados pueden correr en varias instancias a la vez sin un lock propio, porque los cierres de órdenes son `UPDATE` condicionales y el reintegro es idempotente por `ticket_id`.

### Asientos: por qué `@Version`

Cada asiento tiene su fila en `event_seat` desde que se publica el evento, con `(event_id, seat_id)` como clave primaria. Reservar es actualizar esa fila. Si dos compras la leen a la vez, la primera que escribe incrementa `version` y la segunda falla, porque la versión que leyó ya no existe.

- **No genera reintentos.** El que pierde recibe un error enseguida y elige otro asiento, así que no satura la base con intentos repetidos.
- **Es válido para la consigna.** El enunciado nombra el control de versiones entre los mecanismos aceptados.
- **Con el mismo patrón que los cupos** se lograría la misma garantía usando un `UPDATE` condicional sobre `event_seat`. Si se empezara de cero, sería la opción preferida para resolver todo de la misma forma, pero cambiarlo no agrega seguridad y no se justifica.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| `synchronized` o locks en memoria | No funcionan con varias instancias |
| Lock pesimista en los asientos | Encola las compras durante el pico |
| Insertar una fila por reserva y frenar la segunda con un UNIQUE | Obliga a borrar filas al liberar un asiento y a rehacer el modelo, con la misma garantía que ya tenemos |
| Tabla aparte de claves de idempotencia | La clave y la operación podrían quedar desalineadas si una se guarda y la otra no |
| Clave de idempotencia en un header `Idempotency-Key` | Es la convención más común, pero no agrega seguridad y cambiaría el contrato de la API |
| Reservas en Redis | Agrega otra tecnología al núcleo de la compra, cuando la base ya garantiza la consistencia |
| Usar solo `gateway_id` para evitar el doble cobro | Lo asigna la pasarela después de cobrar, y no sirve si se pierde la respuesta |

## Cómo se prueba

Los tests de concurrencia usan MySQL real e hilos alineados con `CountDownLatch`, y afirman el estado final de la base.

- `ReservationMySqlIntegrationTest`: mismo asiento y último lugar.
- `OrderPaymentMySqlIntegrationTest` y `ReturnRequestMySqlIntegrationTest`: pagos y devoluciones simultáneos.
- `PaymentIdempotencyIntegrationTest` y `RefundMySqlIntegrationTest`: cobros y reintegros simultáneos.

## Dónde verlo

[`ticket-service.md`](../architecture/ticket-service.md) y [`payment-service.md`](../architecture/payment-service.md), en *Decisiones*.

# payment-service: decisiones del modelo

> Épica 2 (#22). Tablas `payment` (🟠 *Sales and access*) y `refund` (🔴 *Post-sale and notifications*) de `ticketya_schema_en.drawio`.

Es el único servicio que habla con la pasarela de pagos: cobra las compras y devuelve el dinero. En la Entrega 1 la pasarela es un mock que vive como adaptador dentro del servicio, así después se reemplaza por la real sin tocar el resto (ver `microservices.md`).

## Diagrama

```mermaid
erDiagram
    payment ||--o{ refund : "is refunded by"

    payment {
        bigint id PK
        bigint order_id "ticket-service, by ID"
        varchar idempotency_key UK
        enum payment_method "CREDIT_CARD | DEBIT_CARD | ACCOUNT_MONEY"
        decimal amount
        enum status "PENDING | APPROVED | REJECTED"
        varchar gateway_id UK "NULL until the gateway answers"
        varchar failure_reason
        datetime created_at
        datetime updated_at
    }
    refund {
        bigint id PK
        bigint ticket_id UK "ticket-service, by ID"
        bigint payment_id FK
        enum reason "RETURN_REQUEST | EVENT_CANCELLED | PURCHASE_COMPENSATION"
        bigint return_request_id "ticket-service, by ID; only for RETURN_REQUEST"
        decimal amount
        enum status "REQUESTED | CREDITED | FAILED"
        varchar gateway_id UK "NULL until the gateway answers"
        varchar failure_reason
        datetime created_at
        datetime updated_at
    }
```

## Referencias a otros servicios

`refund → payment` es la única FK real, porque las dos tablas están en la misma base. El resto va por ID, sin FK física.

| Campo | Apunta a | Servicio |
|---|---|---|
| `payment.order_id` | `orders` | ticket-service |
| `refund.ticket_id` | `ticket` | ticket-service |
| `refund.return_request_id` | `return_request` | ticket-service |

**Datos solapados entre bases:**

| Dato | Copia de | Por qué se copia |
|---|---|---|
| `payment.amount` | `orders.total` | Es el monto que efectivamente se cobró. Si la orden cambiara, el pago tiene que seguir diciendo cuánto se le cobró al cliente |
| `refund.amount` | `ticket.price` | Lo mismo para la devolución: queda registrado cuánto se devolvió |

## Cambios respecto del diagrama original

| Cambio | Tabla |
|---|---|
| `idempotency_key` UNIQUE | `payment` |
| `ticket_id` UNIQUE | `refund` |
| `reason` (enum), en vez de deducir el motivo de que `return_request_id` sea NULL | `refund` |
| `gateway_id` UNIQUE | `refund` (en `payment` ya lo era) |
| `payment_method` como enum, no texto libre | `payment` |
| `failure_reason` | las dos |
| `created_at` y `updated_at` | las dos (`payment` solo tenía `created_at`) |

## Decisiones

### Idempotencia del cobro: `payment.idempotency_key`

Consigna 5: *"una entrada nunca puede venderse dos veces, aun cuando […] un cliente reintente el pago por un problema de conexión"*.

`gateway_id` es único, pero no alcanza: lo asigna la pasarela **después** de cobrar. Caso concreto:

1. El ticket-service le pide al payment-service que cobre la orden 10.
2. El payment-service cobra, pero la respuesta se pierde por un timeout.
3. El ticket-service reintenta.
4. Sin clave de idempotencia, el payment-service crea un segundo `payment` y **cobra dos veces**. Los dos `gateway_id` son distintos, así que el unique no lo frena.

Con `idempotency_key`:
- El ticket-service genera una clave por cada intento de pago y la repite en todos los reintentos de ese intento.
- Si la clave ya existe, el payment-service devuelve el `payment` que ya tiene, sin volver a llamar a la pasarela. Si dos reintentos llegan a la vez, el UNIQUE hace que solo uno inserte; el otro falla y lee el existente.
- La clave se reenvía a la pasarela, que también la usa para no cobrar dos veces (las pasarelas reales lo aceptan; Mercado Pago, por ejemplo, con el header `X-Idempotency-Key`). Así está cubierto incluso el caso de que el payment-service se caiga entre cobrar y guardar la respuesta.

La `orders.idempotency_key` del ticket-service no reemplaza a esta: protege la creación de la orden (que no se reserve dos veces), no el cobro.

### Varios pagos por orden, un solo aprobado

La relación `orders → payment` es de uno a muchos: si un pago se rechaza, el cliente puede intentar con otra tarjeta mientras la reserva siga vigente, y cada intento queda registrado. La regla "como máximo un `APPROVED` por orden" se valida en el service antes de llamar a la pasarela.

### Un solo reintegro por entrada: `refund.ticket_id` UNIQUE

El diagrama ya decía que una entrada tiene como máximo un reintegro (0..1), pero nada lo hacía cumplir. Con el UNIQUE, si un pedido de reintegro llega dos veces (por ejemplo, la cancelación de un evento procesada dos veces o un mensaje de RabbitMQ repetido), el segundo falla en vez de devolver la plata dos veces.

Si un reintegro queda `FAILED`, el reintento actualiza la misma fila (vuelve a `REQUESTED`), no crea otra.

El reintegro es **por entrada** y no por orden porque la consigna 7 permite devolver una sola entrada de una compra de varias.

### Motivo explícito: `refund.reason`

El diagrama original distinguía los casos por `return_request_id`: con valor, devolución; NULL, evento cancelado. Pero hay un tercer caso que también tendría NULL:

| `reason` | Cuándo | `return_request_id` |
|---|---|---|
| `RETURN_REQUEST` | Un administrador aprueba una devolución (consigna 7) | Obligatorio |
| `EVENT_CANCELLED` | Se cancela el evento y todas sus entradas pasan a reintegro (consigna 7) | NULL |
| `PURCHASE_COMPENSATION` | Se cobró, pero la compra falló después (por ejemplo, al emitir las entradas) y la saga devuelve el dinero | NULL |

Con el motivo explícito, los reportes y el log no dependen de interpretar un NULL. Un `CHECK` en la tabla impide la combinación inválida: `return_request_id` es obligatorio si y solo si el motivo es `RETURN_REQUEST`.

### `PENDING` como estado de duda

Un pago queda `PENDING` mientras no hay respuesta de la pasarela, incluso si la llamada terminó en timeout. En ese caso no se sabe si se cobró, así que no se lo marca `REJECTED`: se vuelve a consultar a la pasarela con la misma `idempotency_key` hasta tener una respuesta. Esto es lo que después usan los reintentos y el fallback de la Entrega 2.

### `failure_reason`

Guarda el motivo que devuelve la pasarela ("fondos insuficientes", "tarjeta vencida"). Sirve para mostrárselo al cliente y para depurar.

### `payment_method` como enum

Con texto libre aparecerían variantes del mismo medio ("tarjeta", "TC", "credit card"). El enum deja un conjunto cerrado, que además es lo que pide la consigna 4 (*"registrar […] el medio de pago"*).

## Datos de prueba

El `PaymentSeeder` usa las órdenes que crea el `TicketSeeder`:

| Orden | Pagos |
|---|---|
| 1 (Campo del recital, pagada) | Un pago `APPROVED` con tarjeta de crédito |
| 2 (asientos de la obra, pagada) | Un pago `REJECTED` con débito por fondos insuficientes y un reintento `APPROVED` con dinero en cuenta |
| 3 (reserva en curso) | Un pago `PENDING`, sin respuesta de la pasarela |

No hay reintegros: la única devolución del `TicketSeeder` está pendiente, así que todavía no corresponde devolver dinero.

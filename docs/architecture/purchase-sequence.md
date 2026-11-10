# Compra de una entrada numerada

> Consigna, *Arquitectura de microservicios*: *"diagrama de secuencia del flujo de compra de una entrada numerada, incluyendo qué ocurre si el pago es rechazado o la reserva vence"*. Describe lo que hace el código de `ReservationService`, `OrderPaymentService` y `ExpiredReservationsJob` (ticket-service) y de `PaymentService` (payment-service).

## Reglas que aparecen en el diagrama

| Regla | Valor | Dónde se configura |
|---|---|---|
| La reserva retiene los asientos | 10 minutos | `ticketya.reservation.hold` |
| No se aceptan intentos de pago nuevos en el último tramo | 1 minuto antes del vencimiento | `ticketya.reservation.payment-margin` |
| Rechazos hasta liberar los asientos | 3 | `ticketya.reservation.max-rejected-payments` |
| El job de vencimiento corre cada | 30 segundos | `ticketya.reservation.expiration-check` |
| La pasarela simulada rechaza | `DEBIT_CARD` | `payment.mock.rejected-method` (payment-service) |

## Camino feliz y las tres alternativas

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente
    participant G as api-gateway
    participant T as ticket-service
    participant TDB as ticket_db
    participant P as payment-service
    participant PG as Pasarela (mock)
    participant E as event-service

    C->>G: GET /api/availability?eventId=1
    G->>T: (mismo request)
    T->>TDB: asientos AVAILABLE del evento
    T-->>C: 200 asientos libres por sector

    Note over C,TDB: Reserva temporal
    C->>G: POST /api/orders  X-User-Id<br/>{ eventId, idempotencyKey, seatIds: [15, 16] }
    G->>T: (mismo request)
    T->>TDB: ¿ya hay una orden con esa clave? (reintento → la misma orden)
    T->>TDB: venta abierta, límite por cliente
    T->>TDB: lee los asientos (con @Version) y verifica que estén libres
    T->>TDB: asientos → RESERVED hasta ahora + 10 min<br/>orden PENDING, entradas RESERVED
    Note right of TDB: Si otra compra tomó el asiento en el medio,<br/>@Version rechaza el UPDATE: solo una gana
    T-->>C: 201 orden PENDING, expiresAt, total

    Note over C,PG: Pago
    C->>G: POST /api/orders/{id}/pay<br/>{ paymentMethod, idempotencyKey }
    G->>T: (mismo request)
    T->>TDB: la orden sigue PENDING y la reserva vigente
    T->>TDB: toma la orden para este intento (UPDATE condicional por clave)
    T->>P: POST /internal/payments<br/>{ orderId, amount, paymentMethod, idempotencyKey }
    P->>P: guarda el pago PENDING antes de cobrar
    P->>PG: cobrar

    alt Pago aprobado
        PG-->>P: aprobado
        P-->>T: 201 APPROVED
        T->>TDB: reserva vigente → asientos SOLD, orden PAID,<br/>entradas ISSUED con QR único
        T-->>C: 200 orden PAID con sus entradas y QR
        T->>E: si no queda lugar: PUT /internal/events/{id}/sold-out

    else Pago rechazado
        PG-->>P: rechazado ("Fondos insuficientes")
        P-->>T: 422 payment_rejected { reason, rejectedAttempts }
        alt Menos de 3 rechazos
            T->>TDB: libera el intento: la orden sigue PENDING<br/>y los asientos siguen reservados
            T-->>C: 422 payment_rejected: puede reintentar con otro medio y otra clave
        else Tercer rechazo
            T->>TDB: orden REJECTED, asientos → AVAILABLE
            T-->>C: 422 payment_rejected: la reserva se perdió
        end

    else La reserva venció mientras se cobraba
        PG-->>P: aprobado
        P-->>T: 201 APPROVED
        T->>TDB: la reserva ya no está vigente: no se emite nada
        T->>TDB: orden EXPIRED, asientos → AVAILABLE
        T->>P: POST /internal/refunds por cada entrada<br/>(motivo PURCHASE_COMPENSATION)
        T-->>C: 409 reservation_expired (el dinero se reintegra)
    end
```

## Si la reserva vence antes de pagar

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente
    participant J as ExpiredReservationsJob<br/>(ticket-service, cada 30 s)
    participant T as ticket-service
    participant TDB as ticket_db

    Note over C,TDB: El cliente reservó y no pagó en 10 minutos
    J->>TDB: órdenes PENDING con expiresAt vencido
    J->>TDB: orden EXPIRED, asientos → AVAILABLE
    Note right of TDB: Los asientos ya se pueden reservar de nuevo

    C->>T: POST /api/orders/{id}/pay
    T->>TDB: la orden está EXPIRED
    T-->>C: 409 reservation_expired

    Note over C,TDB: Un asiento con la reserva vencida figura libre<br/>aunque el job todavía no haya corrido:<br/>la disponibilidad compara reservedUntil con la hora actual
```

## Qué garantiza cada paso

| Paso | Qué pasaría sin él | Cómo se evita |
|---|---|---|
| Reserva | Dos clientes compran el mismo asiento | `@Version` en `event_seat`: de dos transacciones sobre el mismo asiento, solo una confirma. Funciona con varias instancias, porque lo decide la base ([`concurrency-and-idempotency.md`](../quality-attributes/concurrency-and-idempotency.md)) |
| Reintento de la reserva | Un doble clic reserva dos veces | `idempotencyKey` de la orden: la misma clave devuelve la misma orden |
| Toma del intento de pago | Dos pagos simultáneos de la misma orden cobran dos veces | `UPDATE` condicional sobre la orden: solo un intento la toma, el otro recibe `409 payment_in_progress` |
| Cobro | Un reintento por un corte de conexión cobra dos veces | `idempotencyKey` del pago: la misma clave devuelve el resultado guardado, sin volver a llamar a la pasarela |
| Emisión | Se emite una entrada de una reserva vencida | Solo se emite si la reserva sigue vigente y los asientos pasan de `RESERVED` a `SOLD` en la misma transacción |
| Compensación | Se cobra una compra que no se concretó | Reintegro automático de cada entrada |

**Lo que el cliente nunca ve:** el reintento del job que avisa *agotado* al event-service si este no responde (no deshace la compra), y los reintegros que fallan, que quedan en el log para reintentarse (US-PAY-13, Entrega 2).

## Errores que puede recibir el cliente

| Momento | Código | Qué significa |
|---|---|---|
| Reservar | `409 seat_unavailable` | Otro cliente tomó alguno de los asientos |
| Reservar | `409 data_conflict` | Otro cliente reservó el mismo asiento en el mismo instante y ganó (`@Version`) |
| Reservar | `409 ticket_limit_exceeded` | Supera el máximo de entradas por cliente del evento |
| Reservar | `409 event_not_on_sale` / `sale_not_started` | El evento no está a la venta, o la venta todavía no abrió |
| Pagar | `422 payment_rejected` | La pasarela rechazó el cobro; `detail` trae el motivo y cuántos rechazos van |
| Pagar | `409 payment_in_progress` | Hay otro intento de pago en curso para esa orden |
| Pagar | `409 payment_window_closed` | Falta menos de 1 minuto para que venza la reserva |
| Pagar | `409 reservation_expired` | La reserva venció |
| Pagar | `503 service_unavailable` | El payment-service no respondió: solo se puede repetir ese mismo intento |

Todos los códigos están en [`error-handling.md`](error-handling.md).

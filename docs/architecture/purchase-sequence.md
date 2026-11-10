# Compra de una entrada numerada

> Diagrama de secuencia que pide la Entrega 1: compra de asientos numerados, con lo que pasa si el pago se rechaza o la reserva vence. Las reglas y su porqué están en [`ticket-service.md`](ticket-service.md) y [`payment-service.md`](payment-service.md).

Participantes: el cliente (app o Postman), el `api-gateway`, el `ticket-service` con su base (`ticket_db`), el `payment-service` y la pasarela simulada (`MockPaymentGateway`). El event-service no participa durante la compra: al publicar el evento ya le copió al ticket-service el inventario, los precios y los datos que se imprimen en la entrada.

## 1. Reserva y pago aprobado

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente
    participant G as api-gateway
    participant T as ticket-service
    participant DB as ticket_db
    participant P as payment-service
    participant PG as Pasarela (mock)

    C->>G: GET /api/availability?eventId=1
    G->>T: reenvía
    T->>DB: event_sector_price + event_seat
    T-->>C: 200 sectores con nombre, precio y asientos (fila, número, libre)

    C->>G: POST /api/orders {eventId, seatIds, idempotencyKey}<br/>X-User-Id
    G->>T: reenvía
    T->>DB: event_sale abierta y tope por cliente
    T->>DB: event_seat libres (AVAILABLE o reserva vencida)
    T->>DB: orders PENDING, expires_at = ahora + 10 min<br/>event_seat RESERVED (@Version)<br/>ticket RESERVED, sin QR
    Note over T,DB: Si otro cliente tomó el asiento entre la lectura y la escritura,<br/>la version ya no coincide y esta reserva falla con 409 seat_unavailable
    T-->>C: 201 orden PENDING con sus entradas

    C->>G: POST /api/orders/{id}/pay {paymentMethod, idempotencyKey}
    G->>T: reenvía
    T->>DB: UPDATE orders SET payment_attempt_key<br/>WHERE status = PENDING AND sin otro intento
    T->>P: POST /internal/payments {orderId, amount, paymentMethod, idempotencyKey}
    P->>PG: charge
    PG-->>P: aprobado
    P-->>T: 201 APPROVED
    T->>DB: event_seat SOLD (solo si siguen siendo de esta orden)<br/>orders PAID<br/>ticket ISSUED con qr_code = UUID aleatorio
    T-->>C: 200 orden PAID, cada entrada con evento, sector, fila, asiento y QR
```

## 2. Pago rechazado

La orden sigue `PENDING` y el cliente puede probar otro medio mientras no venza la reserva. Al tercer rechazo, la orden se cierra y los asientos se liberan.

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente
    participant T as ticket-service
    participant DB as ticket_db
    participant P as payment-service

    C->>T: POST /api/orders/{id}/pay (DEBIT_CARD, clave k1)
    T->>DB: toma la orden con k1
    T->>P: POST /internal/payments
    P-->>T: 422 payment_rejected, rejectedAttempts = 1
    T->>DB: limpia payment_attempt_key (la orden sigue PENDING)
    T-->>C: 422 payment_rejected, rejectedAttempts = 1

    C->>T: POST /api/orders/{id}/pay (CREDIT_CARD, clave k2)
    Note over C,P: Si se aprueba, sigue igual que el caso 1

    alt Tercer rechazo
        P-->>T: 422 payment_rejected, rejectedAttempts = 3
        T->>DB: orders REJECTED<br/>ticket VOIDED<br/>event_seat AVAILABLE
        T-->>C: 422 payment_rejected
    end
```

## 3. La reserva vence

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente
    participant T as ticket-service
    participant J as ExpiredReservationsJob (cada 30 s)
    participant DB as ticket_db
    participant P as payment-service

    Note over C,DB: El cliente reservó y no pagó en 10 minutos
    J->>DB: orders PENDING con expires_at vencido
    J->>DB: orders EXPIRED<br/>ticket VOIDED<br/>event_seat AVAILABLE, cupo devuelto
    C->>T: POST /api/orders/{id}/pay
    T-->>C: 409 reservation_expired, sin cobrar

    Note over C,P: Caso borde: el pago se aprueba justo después del vencimiento
    C->>T: POST /api/orders/{id}/pay (empezó antes del vencimiento)
    T->>P: POST /internal/payments
    P-->>T: 201 APPROVED
    T->>DB: el asiento ya no es de la orden o la reserva venció:<br/>orders EXPIRED, no se emiten entradas
    T->>P: POST /internal/refunds por cada entrada<br/>reason = PURCHASE_COMPENSATION
    T-->>C: 409 reservation_expired, con el pago reintegrado
```

## Qué garantiza cada paso

| Riesgo | Mecanismo | Dónde se prueba |
|---|---|---|
| Dos clientes por el mismo asiento | `@Version` en `event_seat` | `ReservationMySqlIntegrationTest.reserve_manyCustomersForTheSameSeats_onlyOneGetsThem` |
| Dos clientes por el último lugar de un sector | `UPDATE event_sector_stock ... WHERE taken + :n <= capacity` | `reserve_moreCustomersThanPlaces_neverSellsMoreThanTheCapacity` |
| La app reintenta la reserva por un corte | `orders.idempotency_key` única | `reserve_sameKeyAtTheSameTime_createsASingleOrder` |
| Dos pagos a la vez con tarjetas distintas | `payment_attempt_key` tomada con `UPDATE` condicional | `OrderPaymentMySqlIntegrationTest.pay_differentAttemptsAtTheSameTime_chargesOnlyOnce` |
| Pago aprobado con la reserva vencida | Compensación con reintegro | `pay_reservationExpiresWhileCharging_refundsEveryTicketAndIssuesNothing` |

# Diagrama de módulos

> Consigna, *Arquitectura de microservicios*: *"diagrama de módulos de la arquitectura de solución"*. Las responsabilidades de cada servicio y por qué se separaron así están en [`microservices.md`](microservices.md).

## Entrega 1

```mermaid
flowchart LR
    subgraph clients["Sistemas externos que llaman"]
        front["Apps de clientes y organizadores<br/>backoffice (Postman en la Entrega 1)"]
    end

    gateway["api-gateway<br/>:8080<br/>Spring Cloud Gateway"]

    subgraph services["Microservicios"]
        auth["auth-service<br/>:8081<br/>usuarios, roles, permisos,<br/>organizaciones, organizadores"]
        event["event-service<br/>:8082<br/>lugares, sectores, asientos,<br/>participantes, eventos, búsqueda"]
        ticket["ticket-service<br/>:8083<br/>inventario, reservas, órdenes,<br/>entradas y QR, devoluciones"]
        payment["payment-service<br/>:8084<br/>cobros y reintegros"]
    end

    subgraph data["Una base por servicio (Docker)"]
        authdb[("auth_db<br/>MySQL :3307")]
        eventdb[("event_db<br/>MySQL :3308")]
        ticketdb[("ticket_db<br/>MySQL :3309")]
        paymentdb[("payment_db<br/>MySQL :3310")]
    end

    pasarela["Pasarela de pagos<br/>(mock dentro del payment-service)"]

    front -->|"REST /api/**<br/>X-User-Id"| gateway
    gateway -->|"/api/users, /organizations, /organizers"| auth
    gateway -->|"/api/events, /venues, /participants"| event
    gateway -->|"/api/orders, /availability,<br/>/tickets, /return-requests"| ticket
    gateway -->|"/api/payments"| payment

    event -->|"permisos y organizador<br/>(AuthClient)"| auth
    event -->|"publicar, suspender y cancelar la venta<br/>PUT /internal/event-sales/{id}"| ticket
    ticket -->|"agotado o de nuevo a la venta<br/>PUT /internal/events/{id}/..."| event
    ticket -->|"cobrar y reintegrar<br/>POST /internal/payments, /internal/refunds"| payment
    payment --> pasarela

    auth --- authdb
    event --- eventdb
    ticket --- ticketdb
    payment --- paymentdb
```

**Cómo leerlo:**
- **Las flechas son llamadas HTTP síncronas** con clients `@HttpExchange` (ver *Comunicación entre microservicios* en `microservices.md`). Las que van a `/internal/**` son solo entre servicios: el gateway no las enruta.
- **Cada servicio es dueño de su base** y nadie más la lee. Lo que otro servicio necesita lo pide por API o lo guarda copiado (por ejemplo, el ticket-service copia los precios de cada sector en `event_sector_price`).
- **Las referencias entre servicios son por id, sin FK** ([RFC-004](../rfc/RFC-004-cross-service-relations.md)): `event.organization_id` apunta a auth, `ticket.event_id` apunta a event, `payment.order_id` apunta a ticket.

## Dependencias entre servicios

| Quién llama | A quién | Para qué | Si no responde |
|---|---|---|---|
| event-service | auth-service | Permisos del usuario de `X-User-Id` y organizador de cada evento | `503 service_unavailable`, con un reintento y timeouts (ver *Timeouts y reintentos* en `microservices.md`) |
| event-service | ticket-service | Sincronizar el estado y los sectores de la venta al publicar, suspender o cancelar | El evento no cambia de estado (`503`) |
| ticket-service | event-service | Avisar que el evento se agotó o volvió a tener lugar | Se reintenta hasta que se aplique, sin deshacer la compra |
| ticket-service | payment-service | Cobrar una orden y reintegrar entradas | La orden queda con el pago sin resultado y solo se reintenta ese intento |

**Ningún ciclo bloquea una operación.** Event y ticket se llaman en los dos sentidos, pero por motivos distintos: event avisa los cambios del catálogo y ticket avisa los cambios de disponibilidad. Ninguno espera al otro para responder la llamada que recibió.

## Entrega 2

Se suman los servicios que hoy son esqueletos, la mensajería y la seguridad:

```mermaid
flowchart LR
    gateway["api-gateway<br/>+ validación de JWT"]
    auth["auth-service"]
    event["event-service"]
    ticket["ticket-service"]
    payment["payment-service"]
    queue["queue-service<br/>:8085<br/>fila virtual"]
    notification["notification-service<br/>:8086<br/>email y push"]
    recommendation["recommendation-service<br/>:8087<br/>preferencias y sugerencias"]
    rabbit{{"RabbitMQ"}}
    redis[("Redis")]
    mongo[("MongoDB")]
    notifdb[("MySQL")]
    email["Proveedor de email / push"]

    gateway --> queue
    gateway --> notification
    gateway --> recommendation
    ticket -->|"¿tiene turno?"| queue
    queue -->|"alta demanda, apertura"| event
    ticket -->|"compra, devolución"| rabbit
    event -->|"cancelación, cambio, publicación"| rabbit
    rabbit --> notification
    rabbit --> recommendation
    notification --> email
    recommendation -->|"datos de eventos"| event
    queue --- redis
    recommendation --- mongo
    notification --- notifdb
```

El detalle de cada uno (base elegida, qué publica y qué consume) está en la tabla de servicios de [`microservices.md`](microservices.md).
